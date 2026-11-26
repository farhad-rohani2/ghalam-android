package com.example.ghalam.teacher.data.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.ghalam.teacher.data.local.MediaDao
import com.example.ghalam.teacher.data.local.MediaEntity
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Downloads the students' answer files once and keeps them in filesDir/media (index in Room), so a
 * teacher can review them offline. Used by the background sync (Wi-Fi, size limit) and by the
 * answer screen (on demand).
 */
class TeacherMedia(
    context: Context,
    baseClient: OkHttpClient,
    private val dao: MediaDao,
) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "media").apply { mkdirs() }

    // same auth as the API client, but no body logging (it would buffer whole videos) and no call timeout
    private val client: OkHttpClient = baseClient.newBuilder().also { b ->
        b.interceptors().removeAll { it is HttpLoggingInterceptor }
        b.networkInterceptors().removeAll { it is HttpLoggingInterceptor }
    }
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gate = Semaphore(3)
    private val _active = MutableStateFlow<Set<String>>(emptySet())

    /** Keys of the files being downloaded right now. */
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    fun keyOf(url: String): String = sha1(url.substringBefore('?'))

    fun observe(url: String): Flow<MediaEntity?> = dao.observe(keyOf(url))

    fun fileFor(path: String): File = File(dir, path)

    suspend fun localFile(url: String): File? {
        val path = dao.get(keyOf(url))?.path ?: return null
        return File(dir, path).takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Makes sure the file is on the phone and returns it. Returns null when it was skipped (bigger
     * than [maxBytes], or another call is already downloading it). Throws [IOException] on failure.
     */
    suspend fun ensure(url: String, name: String, taskId: Long, maxBytes: Long = Long.MAX_VALUE): File? {
        localFile(url)?.let { return it }
        val key = keyOf(url)
        synchronized(this) {
            if (key in _active.value) return null
            _active.update { it + key }
        }
        try {
            return gate.withPermit { download(url, name, taskId, key, maxBytes) }
        } finally {
            _active.update { it - key }
        }
    }

    private suspend fun download(url: String, name: String, taskId: Long, key: String, maxBytes: Long): File? =
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "$key.part")
            try {
                val request = Request.Builder().url(url).header("Accept", "*/*").build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("پاسخ خالی از سرور")
                    if (body.contentLength() > maxBytes) return@withContext null

                    var written = 0L
                    body.byteStream().use { input ->
                        tmp.outputStream().buffered().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                written += n
                                if (written > maxBytes) return@withContext null
                                currentCoroutineContext().ensureActive()
                            }
                        }
                    }
                    if (written == 0L) throw IOException("فایل خالی است")

                    val rawSniff = MediaKinds.sniff(tmp)
                    // a voice recording (M4A) has the same header as an MP4 video: the file name decides
                    val sniffed = if (rawSniff?.kind == MediaKind.VIDEO &&
                        MediaKinds.extensionOf(name) in setOf("m4a", "aac")
                    ) Sniffed(MediaKind.AUDIO, "audio/mp4", "m4a") else rawSniff
                    val headerMime = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
                        ?.takeIf { it != "application/octet-stream" && it != "binary/octet-stream" }
                    val ext = sniffed?.ext ?: MediaKinds.extensionOf(name) ?: "bin"
                    val mime = sniffed?.mime ?: headerMime ?: MediaKinds.mimeOfExt(ext) ?: "application/octet-stream"
                    if (mime == "text/html") throw IOException("سرور به‌جای فایل، یک صفحه‌ی وب برگرداند")

                    val dest = File(dir, "$key.$ext")
                    if (dest.exists()) dest.delete()
                    if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)

                    dao.upsert(
                        MediaEntity(
                            fileKey = key,
                            url = url.substringBefore('?'),
                            name = name,
                            mime = mime,
                            kind = MediaKinds.kindOfMime(mime).name,
                            sizeBytes = written,
                            path = dest.name,
                            taskId = taskId,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    dest
                }
            } finally {
                tmp.delete()
            }
        }

    /** Files of tasks that were removed from the phone. */
    suspend fun deleteForTasks(taskIds: List<Long>) {
        if (taskIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            dao.byTasks(taskIds).forEach { row -> row.path?.let { File(dir, it).delete() } }
            dao.deleteByTasks(taskIds)
        }
    }

    /** Logout / account change. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dao.clear()
        }
        _active.value = emptySet()
    }

    /** "Open with…" chooser; false when nothing could be started. */
    fun openExternally(ctx: Context, file: File, mime: String): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri("", uri)
        val chooser = Intent.createChooser(view, "باز کردن با…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(chooser) }.isSuccess
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
