package com.example.ghalam.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Blocking decoders for local files; call them from Dispatchers.IO. Each returns null on failure. */
object MediaBitmaps {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Photos: ImageDecoder first (EXIF, HEIC/AVIF on new Android), BitmapFactory as a tolerant fallback. */
    fun image(file: File, maxSide: Int): Bitmap? {
        val key = "img|${file.path}|${file.lastModified()}|$maxSide"
        cache.get(key)?.let { return it }
        val bmp = decodeWithImageDecoder(file, maxSide) ?: decodeWithFactory(file, maxSide)
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun videoFrame(file: File): Bitmap? {
        val key = "vid|${file.path}|${file.lastModified()}"
        cache.get(key)?.let { return it }
        val r = MediaMetadataRetriever()
        val bmp = try {
            r.setDataSource(file.path)
            r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { scaleDown(it, 640) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun pdfFirstPage(file: File, width: Int): Bitmap? {
        val key = "pdf|${file.path}|${file.lastModified()}|$width"
        cache.get(key)?.let { return it }
        val bmp = runCatching { LocalPdf(file).use { if (it.pageCount > 0) it.render(0, width) else null } }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun decodeWithImageDecoder(file: File, maxSide: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
            }
        }
    }.getOrNull()

    private fun decodeWithFactory(file: File, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true)
    }
}

/** PdfRenderer wrapper: one page at a time, all renderers share one lock (the framework class is not thread-safe). */
class LocalPdf(file: File) : AutoCloseable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (e: Throwable) {
        runCatching { pfd.close() }
        throw e
    }

    val pageCount: Int = renderer.pageCount

    fun render(index: Int, width: Int): Bitmap? = synchronized(LOCK) {
        runCatching {
            val page = renderer.openPage(index)
            try {
                val w = width.coerceIn(200, 2000)
                val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally {
                page.close()
            }
        }.getOrNull()
    }

    override fun close() {
        synchronized(LOCK) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    private companion object {
        val LOCK = Any()
    }
}
