package com.example.ghalam.data.media

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.ghalam.data.local.MediaDao
import com.example.ghalam.data.local.MediaEntity
import com.example.ghalam.data.model.RemoteFile
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor

sealed interface MediaState {
    /** Not looked at yet. */
    data object Idle : MediaState

    /** Bigger than the auto-download limit: waits for the user to press "show". */
    data class Large(val sizeBytes: Long) : MediaState

    data class Downloading(val progress: Float?) : MediaState

    data class Ready(val file: File, val mime: String, val kind: MediaKind) : MediaState

    data class Failed(val message: String, val offline: Boolean, val at: Long = System.currentTimeMillis()) : MediaState
}

private class MediaHttpException(val code: Int) : IOException("HTTP $code")

/**
 * Downloads remote files once, keeps them in filesDir/media (index in Room, table media_files)
 * and exposes a [MediaState] per file. Everything saved here is readable without internet.
 */
class MediaManager(
    private val context: Context,
    baseClient: OkHttpClient,
    private val dao: MediaDao,
    val prefs: MediaPrefs,
) {
    private val dir = File(context.applicationContext.filesDir, "media").apply { mkdirs() }

    /**
     * Same auth/session as the rest of the app, but without body logging (it would buffer whole
     * videos in memory) and without the short call timeout meant for JSON requests.
     */
    private val client: OkHttpClient = baseClient.newBuilder().also { b ->
        b.interceptors().removeAll { it is HttpLoggingInterceptor }
        b.networkInterceptors().removeAll { it is HttpLoggingInterceptor }
    }
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(3)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val states = MutableStateFlow<Map<String, MediaState>>(emptyMap())

    val usedBytes: Flow<Long> get() = dao.observeUsedBytes()

    fun keyOf(file: RemoteFile): String = sha1(file.url.substringBefore('?'))

    fun stateOf(key: String): Flow<MediaState> =
        states.map { it[key] ?: MediaState.Idle }.distinctUntilChanged()

    /**
     * Called for every file shown on screen (and for all files of a task when it loads):
     * restores it from disk, or downloads it if it is within the auto-download limit.
     * [force] = the user pressed "show"/"retry", ignore the limit.
     */
    fun request(file: RemoteFile, force: Boolean = false) {
        val key = keyOf(file)
        synchronized(jobs) {
            if (jobs[key]?.isActive == true) return
            when (val cur = states.value[key]) {
                is MediaState.Ready, is MediaState.Downloading -> return
                is MediaState.Large ->
                    if (!force && (cur.sizeBytes < 0 || cur.sizeBytes > prefs.limitBytes.value)) return
                is MediaState.Failed ->
                    if (!force && System.currentTimeMillis() - cur.at < RETRY_AFTER_MS) return
                else -> Unit
            }
            jobs[key] = scope.launch { load(file, key, force) }
        }
    }

    fun prefetch(files: List<RemoteFile>) = files.forEach { request(it) }

    // ---------------------------------------------------------------- loading

    private suspend fun load(file: RemoteFile, key: String, force: Boolean) {
        try {
            var row = dao.get(key)
            val path = row?.path
            if (path != null) {
                val f = File(dir, path)
                if (f.isFile && f.length() > 0) {
                    set(key, readyOf(f, row!!))
                    return
                }
                dao.delete(key) // file vanished
                row = null
            }
            val limit = prefs.limitBytes.value
            if (!force && row != null && row.sizeBytes > limit) {
                set(key, MediaState.Large(row.sizeBytes))
                return
            }
            set(key, MediaState.Downloading(null))
            gate.withPermit { download(file, key, force, limit) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            set(key, MediaState.Failed(describe(e), e is IOException))
        }
    }

    private suspend fun download(file: RemoteFile, key: String, force: Boolean, limit: Long) {
        val tmp = File(dir, "$key.part")
        try {
            val req = Request.Builder().url(file.fullUrl).header("Accept", "*/*").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw MediaHttpException(resp.code)
                val body = resp.body ?: throw IOException("پاسخ خالی از سرور")
                val total = body.contentLength()
                if (!force && total > limit) {
                    markLarge(file, key, total)
                    return
                }

                var written = 0L
                var overflow = false
                var lastReported = -1f
                body.byteStream().use { input ->
                    tmp.outputStream().buffered().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            if (!force && total < 0 && written > limit) {
                                overflow = true
                                break
                            }
                            if (total > 0) {
                                val p = written.toFloat() / total
                                if (p - lastReported >= 0.02f) {
                                    lastReported = p
                                    set(key, MediaState.Downloading(p))
                                }
                            }
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
                if (overflow) {
                    markLarge(file, key, written)
                    return
                }
                if (written == 0L) throw IOException("فایل خالی است")

                val sniffed = MediaType.sniff(tmp)
                val headerMime = body.contentType()?.let { "${it.type}/${it.subtype}".lowercase() }
                    ?.takeIf { it != "application/octet-stream" && it != "binary/octet-stream" }
                val mime = sniffed?.mime
                    ?: headerMime
                    ?: MediaType.mimeFromExt(MediaType.extensionOf(file.name))
                    ?: "application/octet-stream"
                if (mime == "text/html") throw IOException("سرور به‌جای فایل، یک صفحه‌ی وب برگرداند")
                val kind = MediaType.kindOfMime(mime)
                val ext = sniffed?.ext ?: MediaType.extensionOf(file.name) ?: MediaType.extFromMime(mime) ?: "bin"

                val dest = File(dir, "$key.$ext")
                if (dest.exists()) dest.delete()
                if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)

                dao.upsert(
                    MediaEntity(
                        fileKey = key,
                        url = file.url.substringBefore('?'),
                        name = file.name,
                        mime = mime,
                        kind = kind.name,
                        sizeBytes = written,
                        path = dest.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                set(key, MediaState.Ready(dest, mime, kind))
            }
        } finally {
            tmp.delete()
        }
    }

    private suspend fun markLarge(file: RemoteFile, key: String, size: Long) {
        dao.upsert(
            MediaEntity(
                fileKey = key,
                url = file.url.substringBefore('?'),
                name = file.name,
                mime = null,
                kind = MediaType.guessKind(file.name, file.url).name,
                sizeBytes = size,
                path = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        set(key, MediaState.Large(size))
    }

    // ---------------------------------------------------------------- housekeeping

    /** After the student deleted his own photo on the server. */
    suspend fun forget(file: RemoteFile) {
        val key = keyOf(file)
        try {
            synchronized(jobs) { jobs.remove(key)?.cancel() }
            dao.get(key)?.path?.let { File(dir, it).delete() }
            dao.delete(key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        states.update { it - key }
    }

    /** Settings "clear" button and logout. */
    suspend fun clearAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
        states.value = emptyMap()
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dao.clear()
        }
    }

    // ---------------------------------------------------------------- hand-off to other apps

    /** "Open with…" chooser; false when nothing could be started. */
    fun openExternally(ctx: Context, ready: MediaState.Ready): Boolean {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", ready.file)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, ready.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri("", uri)
        val chooser = Intent.createChooser(view, "باز کردن با…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(chooser) }.isSuccess
    }

    /** Copies the local file to Downloads/Ghalam (no permission needed, works offline). */
    suspend fun saveToDownloads(ready: MediaState.Ready, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val safe = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
                val ext = ready.file.extension
                val name = if (MediaType.extensionOf(safe) == null && ext.isNotEmpty()) "$safe.$ext" else safe
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, ready.mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Ghalam")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("insert failed")
                try {
                    val out = resolver.openOutputStream(uri) ?: throw IOException("no stream")
                    out.use { o -> ready.file.inputStream().use { it.copyTo(o) } }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }.isSuccess
        }

    // ---------------------------------------------------------------- helpers

    private fun set(key: String, s: MediaState) = states.update { it + (key to s) }

    private fun readyOf(f: File, row: MediaEntity) = MediaState.Ready(
        f,
        row.mime ?: "application/octet-stream",
        runCatching { MediaKind.valueOf(row.kind) }.getOrDefault(MediaKind.OTHER),
    )

    private fun describe(e: Throwable): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> "اتصال اینترنت برقرار نیست"
        is MediaHttpException -> when (e.code) {
            401, 403 -> "دسترسی به این فایل مجاز نیست (${e.code})"
            404 -> "فایل روی سرور پیدا نشد"
            else -> "خطای سرور (${e.code})"
        }
        is IOException -> e.message ?: "دریافت فایل ناموفق بود"
        else -> e.message ?: "دریافت فایل ناموفق بود"
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val RETRY_AFTER_MS = 15_000L
    }
}
