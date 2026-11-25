package com.example.ghalam.data.outbox

import android.content.Context
import com.example.ghalam.data.api.GhalamApi
import com.example.ghalam.data.api.PAGE_TASK
import com.example.ghalam.data.local.LocalMediaEntity
import com.example.ghalam.data.local.OutboxDao
import com.example.ghalam.data.local.PendingAnswerEntity
import com.example.ghalam.data.local.PendingUploadEntity
import com.example.ghalam.data.local.UploadState
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.data.model.parseMedia
import com.example.ghalam.data.model.toUi
import com.example.ghalam.data.repository.TaskRepository
import com.example.ghalam.util.NetworkMonitor
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Sends what is waiting in the outbox. It runs in two places that never overlap (one lock):
 *  - in the app process, right after something was saved and every time the internet comes back;
 *  - in WorkManager, so it also happens when the app is closed.
 *
 * No connection (IOException) is not a failure of the item: it just waits for the next
 * connectivity event. Server errors (5xx) are retried with a growing delay. Answers the server
 * refuses for good (4xx) are marked as failed so the student can see why.
 */
class UploadEngine(
    private val appContext: Context,
    private val api: GhalamApi,
    private val dao: OutboxDao,
    private val repo: TaskRepository,
    private val prefs: SubmitPrefs,
    private val network: NetworkMonitor,
    private val isSignedIn: () -> Boolean,
    private val notifier: UploadNotifier,
) {
    enum class Outcome { DONE, RETRY, SIGNED_OUT }

    private class Pass {
        var again = false
        var netDown = false
        var signedOut = false
        var waiting = false
        var sentPhotos = 0
        var failed = 0
        val touched = linkedSetOf<String>()
    }

    private val lock = Mutex()

    /** [ignoreBackoff] = the internet just came back: try everything now. */
    suspend fun run(ignoreBackoff: Boolean = false): Outcome = lock.withLock { runLocked(ignoreBackoff) }

    private suspend fun runLocked(ignoreBackoff: Boolean): Outcome {
        if (!isSignedIn()) return Outcome.SIGNED_OUT
        if (prefs.wifiOnly && network.isMetered()) return Outcome.RETRY

        dao.resetUploading()
        val pass = Pass()
        for (round in 0 until MAX_ROUNDS) {
            pass.again = false
            sendTexts(pass, ignoreBackoff)
            if (!pass.netDown && !pass.signedOut) sendPhotos(pass, ignoreBackoff)
            if (!pass.again || pass.netDown || pass.signedOut) break
        }

        // keep the offline copy of the task in step with the server
        if (!pass.netDown) pass.touched.forEach { repo.refreshDetailQuietly(it) }
        notifier.report(pass.sentPhotos, pass.failed)

        return when {
            pass.signedOut -> Outcome.SIGNED_OUT
            pass.netDown || pass.waiting -> Outcome.RETRY
            else -> Outcome.DONE
        }
    }

    // ---------------------------------------------------------------- text answers

    private suspend fun sendTexts(pass: Pass, ignoreBackoff: Boolean) {
        val now = System.currentTimeMillis()
        for (a in dao.dirtyAnswers()) {
            if (!ignoreBackoff && a.nextAttemptAt > now) {
                pass.waiting = true
                continue
            }
            try {
                api.answer(a.taskUuid, answerBody(a)).close()
                // 0 rows = the student typed more while this was being sent: another round sends it
                if (dao.markClean(a.taskUuid, a.questionId, a.updatedAt) == 0) pass.again = true
                pass.touched += a.taskUuid
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val code = e.code()
                when {
                    code == 401 -> {
                        pass.signedOut = true
                        return
                    }
                    isPermanent(code) -> {
                        dao.failAnswer(a.taskUuid, a.questionId, a.updatedAt, permanentMessage(code))
                        pass.failed++
                    }
                    else -> retryTextLater(a, now, pass)
                }
            } catch (e: IOException) {
                pass.netDown = true
                return
            } catch (e: Exception) {
                retryTextLater(a, now, pass)
            }
        }
    }

    private suspend fun retryTextLater(a: PendingAnswerEntity, now: Long, pass: Pass) {
        val attempts = a.attempts + 1
        if (attempts >= MAX_ATTEMPTS) {
            dao.failAnswer(a.taskUuid, a.questionId, a.updatedAt, GIVE_UP_MESSAGE)
            pass.failed++
        } else {
            dao.retryAnswer(a.taskUuid, a.questionId, a.updatedAt, attempts, now + backoffMs(attempts), "خطای سرور")
            pass.waiting = true
        }
    }

    private fun answerBody(a: PendingAnswerEntity): JsonObject = buildJsonObject {
        put("pageId", PAGE_TASK)
        // the server reads every key that starts with "question-"; "answer" must always be present
        put("question-${a.questionId}", buildJsonObject { put("answer", a.text) })
    }

    // ---------------------------------------------------------------- photos

    private suspend fun sendPhotos(pass: Pass, ignoreBackoff: Boolean) {
        val now = System.currentTimeMillis()
        for (item in dao.pendingUploads()) {
            if (!ignoreBackoff && item.nextAttemptAt > now) {
                pass.waiting = true
                continue
            }
            val file = File(item.localPath)
            if (!file.exists()) {
                dao.deleteUpload(item.id)
                continue
            }
            dao.setState(item.id, UploadState.UPLOADING, null)
            try {
                val remote = findAlreadyUploaded(item) ?: upload(item, file)
                keepLocalCopy(item, file, remote)
                dao.deleteUpload(item.id)
                pass.sentPhotos++
                pass.touched += item.taskUuid
            } catch (e: CancellationException) {
                withContext(NonCancellable) { dao.setState(item.id, UploadState.PENDING, null) }
                throw e
            } catch (e: HttpException) {
                val code = e.code()
                when {
                    code == 401 -> {
                        dao.setState(item.id, UploadState.PENDING, null)
                        pass.signedOut = true
                        return
                    }
                    isPermanent(code) -> {
                        dao.setState(item.id, UploadState.FAILED, permanentMessage(code))
                        pass.failed++
                    }
                    else -> retryPhotoLater(item, now, pass)
                }
            } catch (e: IOException) {
                // the connection broke: the server may or may not have received the file
                dao.markMaybeSent(item.id)
                dao.setState(item.id, UploadState.PENDING, null)
                pass.netDown = true
                return
            } catch (e: Exception) {
                retryPhotoLater(item, now, pass)
            }
        }
    }

    private suspend fun retryPhotoLater(item: PendingUploadEntity, now: Long, pass: Pass) {
        dao.markMaybeSent(item.id)
        val attempts = item.attempts + 1
        if (attempts >= MAX_ATTEMPTS || now - item.createdAt > MAX_AGE_MS) {
            dao.setState(item.id, UploadState.FAILED, GIVE_UP_MESSAGE)
            pass.failed++
        } else {
            dao.scheduleRetry(item.id, attempts, now + backoffMs(attempts), "خطای سرور")
            pass.waiting = true
        }
    }

    /** After a broken connection: did the file reach the server anyway? Then it must not be sent twice. */
    private suspend fun findAlreadyUploaded(item: PendingUploadEntity): RemoteFile? {
        if (!item.maybeSent) return null
        val detail = repo.fetchDetail(item.taskUuid).toUi()
        val question = detail.questions.firstOrNull { it.id == item.questionId } ?: return null
        return question.files.firstOrNull {
            it.name == item.fileName || it.url.substringBefore('?').endsWith("/" + item.fileName)
        }
    }

    private suspend fun upload(item: PendingUploadEntity, file: File): RemoteFile {
        val json = """{"extraData":{"morphedModel":"answer","modelId":${item.answerId}},"metadata":null}"""
        val res = api.upload(
            file = MultipartBody.Part.createFormData("file", item.fileName, file.asRequestBody("image/jpeg".toMediaType())),
            fileName = item.fileName.toRequestBody(),
            fieldUuid = item.fieldUuid.toRequestBody(),
            json = json.toRequestBody(),
        )
        return parseMedia(res["data"]) ?: error("پاسخ ارسال نامعتبر بود")
    }

    /** The student keeps what was sent: the same compressed file is shown from the phone, never downloaded again. */
    private suspend fun keepLocalCopy(item: PendingUploadEntity, file: File, remote: RemoteFile) {
        val uuid = remote.uuid
        if (uuid == null) {
            file.delete()
            return
        }
        val dest = File(OutboxFiles.answersDir(appContext), "$uuid.jpg")
        if (file.absolutePath != dest.absolutePath) {
            runCatching {
                if (!file.renameTo(dest)) {
                    file.copyTo(dest, overwrite = true)
                    file.delete()
                }
            }
        }
        if (dest.exists()) {
            dao.putLocal(
                LocalMediaEntity(
                    remoteUuid = uuid,
                    taskUuid = item.taskUuid,
                    questionId = item.questionId,
                    name = remote.name,
                    path = dest.absolutePath,
                    sizeBytes = dest.length(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- rules

    /** A 4xx means the same request will never work (except "too many requests" / "timeout"). */
    private fun isPermanent(code: Int): Boolean = code in 400..499 && code != 408 && code != 429

    private fun permanentMessage(code: Int): String = when (code) {
        403 -> "دسترسی مجاز نیست"
        404 -> "تکلیف یا پاسخ پیدا نشد"
        413 -> "حجم فایل زیاد است"
        422 -> "درخواست نامعتبر است"
        423 -> "مهلت این کار گذشته است"
        else -> "خطای سرور ($code)"
    }

    /** 30 s, 1 m, 2 m, 4 m, 8 m, then 15 m, plus up to 10 % random spread. */
    private fun backoffMs(attempts: Int): Long {
        val base = (30_000L shl (attempts - 1).coerceIn(0, 5)).coerceAtMost(15 * 60_000L)
        return base + Random.nextLong(base / 10 + 1)
    }

    private companion object {
        const val MAX_ROUNDS = 3
        const val MAX_ATTEMPTS = 30
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        const val GIVE_UP_MESSAGE = "سرور پاسخ نمی‌دهد؛ بعداً دوباره تلاش کنید"
    }
}
