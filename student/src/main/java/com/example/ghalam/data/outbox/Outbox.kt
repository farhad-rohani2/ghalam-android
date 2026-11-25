package com.example.ghalam.data.outbox

import android.content.Context
import android.net.Uri
import com.example.ghalam.data.local.LocalMediaEntity
import com.example.ghalam.data.local.OutboxDao
import com.example.ghalam.data.local.PendingAnswerEntity
import com.example.ghalam.data.local.PendingUploadEntity
import com.example.ghalam.data.local.TaskOutboxSummary
import com.example.ghalam.data.local.UploadState
import com.example.ghalam.data.model.AnswerSlot
import com.example.ghalam.util.ImageUtil
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

object OutboxFiles {
    /** Photos waiting for upload. */
    fun outboxDir(context: Context): File = File(context.filesDir, "outbox").apply { mkdirs() }

    /** Compressed copies of photos that were uploaded from this phone (same bytes the server got). */
    fun answersDir(context: Context): File = File(context.filesDir, "answers").apply { mkdirs() }
}

sealed interface EnqueueResult {
    data object Queued : EnqueueResult
    data object Held : EnqueueResult
    data object TooLarge : EnqueueResult
    data class Error(val message: String) : EnqueueResult
}

/**
 * Everything the screens do with answers goes through here: the photo or text is saved on the phone
 * first, and [UploadEngine] sends it whenever the internet allows.
 */
class Outbox(
    private val context: Context,
    private val dao: OutboxDao,
    private val prefs: SubmitPrefs,
    private val kick: () -> Unit,
) {
    private val appContext = context.applicationContext

    fun uploadsFor(taskUuid: String): Flow<List<PendingUploadEntity>> = dao.uploadsFor(taskUuid)
    fun answersFor(taskUuid: String): Flow<List<PendingAnswerEntity>> = dao.answersFor(taskUuid)
    fun localMediaFor(taskUuid: String): Flow<List<LocalMediaEntity>> = dao.localFor(taskUuid)
    fun summary(): Flow<List<TaskOutboxSummary>> = dao.summary()

    // ---------------------------------------------------------------- photos

    /** Compresses the photo, stores it permanently and queues it. Works without internet. */
    suspend fun enqueuePhoto(taskUuid: String, questionId: Int, slot: AnswerSlot, uri: Uri): EnqueueResult =
        withContext(Dispatchers.IO) {
            try {
                val bytes = ImageUtil.compress(appContext, uri)
                if (bytes.size > MAX_UPLOAD_BYTES) return@withContext EnqueueResult.TooLarge

                val id = UUID.randomUUID().toString()
                val file = File(OutboxFiles.outboxDir(appContext), "$id.jpg")
                file.writeBytes(bytes)

                val tooBigForAuto = prefs.limitAuto && bytes.size > prefs.limitAutoMb * MB
                val hold = !prefs.autoUpload || tooBigForAuto
                val now = System.currentTimeMillis()
                dao.insertUpload(
                    PendingUploadEntity(
                        id = id,
                        taskUuid = taskUuid,
                        questionId = questionId,
                        answerId = slot.answerId,
                        fieldUuid = slot.fieldUuid,
                        fileName = "photo_${now}_${id.take(6)}.jpg",
                        localPath = file.absolutePath,
                        sizeBytes = bytes.size.toLong(),
                        state = if (hold) UploadState.HELD else UploadState.PENDING,
                        attempts = 0,
                        nextAttemptAt = 0L,
                        createdAt = now,
                        maybeSent = false,
                        lastError = null,
                    ),
                )
                // the camera's temporary file is not needed any more
                runCatching {
                    if (uri.authority == "${appContext.packageName}.fileprovider") appContext.contentResolver.delete(uri, null, null)
                }
                if (hold) EnqueueResult.Held else {
                    kick()
                    EnqueueResult.Queued
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EnqueueResult.Error(e.message ?: "عکس خوانده نشد")
            }
        }

    /** The student removes a photo that was not sent yet. */
    suspend fun discardUpload(id: String) = withContext(NonCancellable + Dispatchers.IO) {
        dao.upload(id)?.let { File(it.localPath).delete() }
        dao.deleteUpload(id)
    }

    /** "Send" / "try again" on one photo. */
    suspend fun retryUpload(id: String) = withContext(Dispatchers.IO) {
        dao.revive(id)
        kick()
    }

    /**
     * Photos that were only waiting for permission (auto-send was off, or they were too big for
     * auto-send): they go now, unless the current settings still hold them back (and [force] is false).
     */
    suspend fun releaseHeld(force: Boolean) = withContext(Dispatchers.IO) {
        val limitBytes = prefs.limitAutoMb * MB
        var any = false
        for (item in dao.heldUploads()) {
            val stillHeld = !force && (!prefs.autoUpload || (prefs.limitAuto && item.sizeBytes > limitBytes))
            if (!stillHeld) {
                dao.revive(item.id)
                any = true
            }
        }
        if (any) kick()
    }

    // ---------------------------------------------------------------- text

    /** Called after the student stopped typing for a moment. Saved at once, sent by the engine. */
    suspend fun saveAnswerText(taskUuid: String, questionId: Int, text: String) =
        withContext(NonCancellable + Dispatchers.IO) {
            dao.upsertAnswer(
                PendingAnswerEntity(
                    taskUuid = taskUuid,
                    questionId = questionId,
                    text = text,
                    updatedAt = System.currentTimeMillis(),
                    dirty = true,
                    failed = false,
                    attempts = 0,
                    nextAttemptAt = 0L,
                    lastError = null,
                ),
            )
            kick()
        }

    suspend fun retryAnswer(taskUuid: String, questionId: Int) = withContext(Dispatchers.IO) {
        dao.reviveAnswer(taskUuid, questionId)
        kick()
    }

    /** Called after a fresh download of the task: texts that are confirmed by the server are not needed locally. */
    suspend fun dropCleanAnswers(taskUuid: String) = withContext(Dispatchers.IO) {
        dao.dropCleanAnswers(taskUuid)
    }

    // ---------------------------------------------------------------- local copies

    suspend fun forgetLocalMedia(remoteUuid: String) = withContext(Dispatchers.IO) {
        dao.local(remoteUuid)?.let { File(it.path).delete() }
        dao.deleteLocal(remoteUuid)
    }

    /** The server list is the truth: a local copy the server no longer lists (and is not brand new) goes away. */
    suspend fun reconcileLocal(taskUuid: String, serverUuids: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 2 * 60_000L
        for (item in dao.localOnce(taskUuid)) {
            if (item.remoteUuid !in serverUuids && item.createdAt < cutoff) {
                File(item.path).delete()
                dao.deleteLocal(item.remoteUuid)
            }
        }
    }

    // ---------------------------------------------------------------- account changes

    /**
     * Unsent work belongs to the student who made it: another national code on the same phone must
     * never send it, so it is removed when somebody else logs in.
     */
    suspend fun onLogin(ownerHash: String) {
        if (prefs.ownerHash.isNotEmpty() && prefs.ownerHash != ownerHash) clearAll()
        prefs.ownerHash = ownerHash
        kick()
    }

    /** The logout button: everything of this student leaves the phone. */
    suspend fun onLogout() {
        clearAll()
        prefs.ownerHash = ""
    }

    suspend fun clearAll() = withContext(NonCancellable + Dispatchers.IO) {
        dao.clearUploads()
        dao.clearAnswers()
        dao.clearLocal()
        OutboxFiles.outboxDir(appContext).deleteRecursively()
        OutboxFiles.answersDir(appContext).deleteRecursively()
    }

    companion object {
        const val MB = 1024L * 1024L

        /** Nothing bigger than this is ever sent. */
        const val MAX_UPLOAD_BYTES = 60L * MB

        /** One-way fingerprint of the national code (the code itself is never stored). */
        fun ownerKey(nationalCode: String): String =
            MessageDigest.getInstance("SHA-256").digest(nationalCode.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
