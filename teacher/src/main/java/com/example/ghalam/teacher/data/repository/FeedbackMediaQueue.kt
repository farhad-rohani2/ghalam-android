package com.example.ghalam.teacher.data.repository

import android.content.Context
import com.example.ghalam.teacher.data.local.MediaQueueDao
import com.example.ghalam.teacher.data.local.MediaQueueState
import com.example.ghalam.teacher.data.local.QueuedMediaEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** studentId of the class-wide feedback in the queue. */
const val GENERAL_FEEDBACK = 0L

/** Parts of POST .../feedback/media: collection, duration (seconds) and files[]. */
fun feedbackMediaParts(file: File, fileName: String, durationMs: Long, mime: String): List<MultipartBody.Part> = listOf(
    MultipartBody.Part.createFormData("collection", null, "voice".toRequestBody()),
    MultipartBody.Part.createFormData("duration", null, (durationMs / 1000.0).toString().toRequestBody()),
    MultipartBody.Part.createFormData("files[]", fileName, file.asRequestBody(mime.toMediaTypeOrNull())),
)

/**
 * Recordings for feedbacks are written to Room first and uploaded by the sync (as soon as there is
 * internet), exactly like the feedback texts.
 */
class FeedbackMediaQueue(
    private val dao: MediaQueueDao,
    private val appContext: Context,
    private val onChanged: () -> Unit,
) {
    fun observe(taskId: Long, studentId: Long): Flow<List<QueuedMediaEntity>> = dao.observe(taskId, studentId)

    /** [studentId] = [GENERAL_FEEDBACK] for the class-wide feedback. Takes over [source]. */
    suspend fun enqueue(taskId: Long, studentId: Long, source: File, durationMs: Long, mime: String) {
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val dir = File(appContext.filesDir, "voice_queue").apply { mkdirs() }
            val ext = source.extension.ifBlank { "m4a" }
            val dest = File(dir, "$id.$ext")
            if (!source.renameTo(dest)) {
                source.copyTo(dest, overwrite = true)
                source.delete()
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            dao.insert(
                QueuedMediaEntity(
                    id = id,
                    taskId = taskId,
                    studentId = studentId,
                    localPath = dest.absolutePath,
                    fileName = "voice_$stamp.$ext",
                    mime = mime,
                    durationMs = durationMs,
                    createdAt = System.currentTimeMillis(),
                    state = MediaQueueState.PENDING,
                    lastError = null,
                ),
            )
        }
        onChanged()
    }

    suspend fun retry(id: String) {
        dao.setState(id, MediaQueueState.PENDING, null)
        onChanged()
    }

    suspend fun discard(id: String) {
        withContext(Dispatchers.IO) { dao.get(id)?.let { File(it.localPath).delete() } }
        dao.delete(id)
    }

    /** Logout: nothing of this account stays on the phone. */
    fun deleteFiles() {
        File(appContext.filesDir, "voice_queue").deleteRecursively()
        File(appContext.filesDir, "voice").deleteRecursively()
    }
}
