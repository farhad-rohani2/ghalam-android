package com.example.ghalam.teacher.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.ghalam.teacher.data.api.ComposeResponse
import com.example.ghalam.teacher.data.api.FeedbackMediaDto
import com.example.ghalam.teacher.data.api.MessageContextDto
import com.example.ghalam.teacher.data.api.MessageTemplateDto
import com.example.ghalam.teacher.data.api.TaskDetailDto
import com.example.ghalam.teacher.data.api.TeacherApi
import com.example.ghalam.teacher.data.api.TemplatesDto
import com.example.ghalam.teacher.data.local.TeacherDatabase
import com.example.ghalam.teacher.data.media.TeacherMedia
import com.example.ghalam.teacher.util.teacherMessage
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** A file the teacher picked for a task. */
data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String)

/**
 * What happened when a task was saved: the saved task, the links the server could not download, and
 * (if the task itself was saved but attaching the files failed) why the files are missing.
 */
data class TaskSaveResult(
    val task: TaskDetailDto,
    val failedLinks: List<String>,
    val fileError: String?,
)

/**
 * The parts that need the server right now (they do not work offline): creating / editing /
 * deleting tasks, reports and messages. After a task changes, Room is updated at once.
 */
class OnlineRepository(
    private val api: TeacherApi,
    private val db: TeacherDatabase,
    private val repo: TeacherRepository,
    private val media: TeacherMedia,
    private val resolver: ContentResolver,
) {
    // ---------------------------------------------------------------- tasks

    suspend fun fetchTask(taskId: Long): TaskDetailDto {
        val dto = api.task(taskId).data
        repo.applyTaskDetail(dto)
        return dto
    }

    suspend fun createTask(body: JsonObject, files: List<PickedFile>): TaskSaveResult {
        val res = api.createTask(body)
        repo.applyTaskDetail(res.data)
        return attachFiles(res.data, res.failedLinks, files)
    }

    suspend fun updateTask(taskId: Long, body: JsonObject, files: List<PickedFile>): TaskSaveResult {
        val res = api.updateTask(taskId, body)
        repo.applyTaskDetail(res.data)
        return attachFiles(res.data, res.failedLinks, files)
    }

    /** The task exists now; a failing upload must not look like a failed save. */
    private suspend fun attachFiles(task: TaskDetailDto, failedLinks: List<String>, files: List<PickedFile>): TaskSaveResult {
        if (files.isEmpty()) return TaskSaveResult(task, failedLinks, null)
        return try {
            val parts = files.map { f ->
                MultipartBody.Part.createFormData("files[]", f.name, UriRequestBody(resolver, f.uri, f.mime))
            }
            val updated = api.addTaskFiles(task.id, parts).data
            repo.applyTaskDetail(updated)
            TaskSaveResult(updated, failedLinks, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TaskSaveResult(task, failedLinks, e.teacherMessage())
        }
    }

    suspend fun deleteTask(taskId: Long) {
        api.deleteTask(taskId)
        repo.removeTaskLocal(taskId)
        media.deleteForTasks(listOf(taskId))
    }

    // ---------------------------------------------------------------- feedback voice (online only)

    /** The feedback media of one student, from the server. */
    suspend fun studentFeedbackMedia(taskId: Long, studentId: Long): List<FeedbackMediaDto> =
        api.feedback(taskId, studentId).data.media

    suspend fun removeStudentFeedbackMedia(taskId: Long, studentId: Long, mediaId: Long): List<FeedbackMediaDto> {
        val dto = api.removeFeedbackMedia(taskId, studentId, mediaId).data
        syncAfterMedia(taskId, studentId, dto)
        return dto.media
    }

    suspend fun generalFeedbackMedia(taskId: Long): List<FeedbackMediaDto> =
        api.generalFeedback(taskId).data.media

    suspend fun removeGeneralFeedbackMedia(taskId: Long, mediaId: Long): List<FeedbackMediaDto> =
        api.removeGeneralFeedbackMedia(taskId, mediaId).data.media

    /** The upload created / changed the feedback row on the server: Room follows (see advanceFeedbackBase). */
    private suspend fun syncAfterMedia(taskId: Long, studentId: Long, dto: com.example.ghalam.teacher.data.api.FeedbackDto) {
        repo.applyServerFeedback(taskId, studentId, dto, System.currentTimeMillis())
        repo.advanceFeedbackBase(taskId, studentId, if (dto.exists) dto.updatedAt else null)
    }

    // ---------------------------------------------------------------- reports

    suspend fun reportForTask(taskId: Long, mode: String): String = api.reportTask(taskId, mode).data.text

    suspend fun reportToday(mode: String, date: String?, classroomId: Long?): String =
        api.reportToday(mode = mode, date = date, classroomId = classroomId).data.text

    suspend fun reportSelected(taskIds: List<Long>, mode: String): String {
        val body = buildJsonObject {
            put("task_ids", JsonArray(taskIds.map { JsonPrimitive(it) }))
            put("mode", JsonPrimitive(mode))
        }
        return api.reportSelected(body).data.text
    }

    // ---------------------------------------------------------------- messages

    suspend fun messageContext(taskId: Long, studentId: Long): MessageContextDto =
        api.messageContext(taskId, studentId).data

    /** [body] overrides the saved template text for this message only; [taskIds] fill {تکالیف}. */
    suspend fun composeMessage(taskId: Long, studentId: Long, template: String, body: String?, taskIds: List<Long>): String {
        val request = buildJsonObject {
            put("template", JsonPrimitive(template))
            if (body != null) put("body", JsonPrimitive(body))
            if (taskIds.isNotEmpty()) put("task_ids", JsonArray(taskIds.map { JsonPrimitive(it) }))
        }
        val res: ComposeResponse = api.composeMessage(taskId, studentId, request)
        return res.data.text
    }

    suspend fun saveTemplate(key: String, body: String): TemplatesDto =
        api.updateTemplate(key, buildJsonObject { put("body", JsonPrimitive(body)) }).data

    suspend fun createTemplate(label: String, body: String): TemplatesDto =
        api.createTemplate(
            buildJsonObject {
                put("label", JsonPrimitive(label))
                put("body", JsonPrimitive(body))
            },
        ).data

    /** Deletes a custom template, or resets an edited default one. */
    suspend fun deleteTemplate(key: String): List<MessageTemplateDto> = api.deleteTemplate(key).data.templates
}

/** Streams a picked file (content://) into the multipart body without loading it into memory. */
private class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mime: String,
) : RequestBody() {
    override fun contentType() = mime.toMediaTypeOrNull()

    // unknown on purpose (the size column can be wrong): the body is sent chunked
    override fun contentLength(): Long = -1L

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("فایل قابل خواندن نیست")
        input.use { sink.writeAll(it.source()) }
    }
}
