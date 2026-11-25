package com.example.ghalam.teacher.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.LessonDto
import com.example.ghalam.teacher.data.api.TaskFileDto
import com.example.ghalam.teacher.data.local.ClassroomEntity
import com.example.ghalam.teacher.data.local.TaskEntity
import com.example.ghalam.teacher.data.repository.PickedFile
import com.example.ghalam.teacher.data.repository.TaskSaveResult
import com.example.ghalam.teacher.util.toServerError
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class LinkRow(val page: String = "", val download: String = "")

data class EditForm(
    val classroomId: Long? = null,
    val lessonId: Long? = null,
    val description: String = "",
    val deadline: LocalDateTime? = null,
    val expectedAnswer: String = "",
    val dependencies: Set<Long> = emptySet(),
    val links: List<LinkRow> = emptyList(),
    val newFiles: List<PickedFile> = emptyList(),
    val removeFileIds: Set<Long> = emptySet(),
)

/** Create a task ([taskId] = 0) or edit one. Needs the server; the result goes into Room at once. */
class TaskEditViewModel(val taskId: Long) : ViewModel() {
    val isNew = taskId == 0L

    private val _form = MutableStateFlow(EditForm())
    val form: StateFlow<EditForm> = _form.asStateFlow()
    private var initial = EditForm()

    val classrooms = MutableStateFlow<List<ClassroomEntity>>(emptyList())
    private val lessons = MutableStateFlow<Map<Long, List<LessonDto>>>(emptyMap())

    /** Tasks of the same class and lesson the new task may depend on. */
    val knownTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val existingFiles = MutableStateFlow<List<TaskFileDto>>(emptyList())

    val loading = MutableStateFlow(true)
    val saving = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Set after a successful save; the screen leaves (after showing the notices, if any). */
    val result = MutableStateFlow<TaskSaveResult?>(null)

    init {
        viewModelScope.launch {
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.toServerError().message
            } finally {
                loading.value = false
            }
        }
    }

    private suspend fun load() {
        val repo = Graph.repo
        val rooms = repo.classrooms().first()
        classrooms.value = rooms
        val serializer = ListSerializer(LessonDto.serializer())
        lessons.value = rooms.associate { room ->
            room.id to (runCatching { Graph.apiClient.json.decodeFromString(serializer, room.lessonsJson) }.getOrNull() ?: emptyList())
        }
        knownTasks.value = repo.tasks().first().map { it.task }

        if (isNew) {
            val fromServer = repo.defaultDeadline()?.let { runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.getOrNull() }
            val deadline = fromServer ?: LocalDateTime.now().plusDays(1).withHour(17).withMinute(0).withSecond(0).withNano(0)
            var f = EditForm(deadline = deadline)
            if (rooms.size == 1) {
                f = f.copy(classroomId = rooms[0].id)
                lessons.value[rooms[0].id]?.singleOrNull()?.let { f = f.copy(lessonId = it.id) }
            }
            initial = f
            _form.value = f
            return
        }

        val task = repo.task(taskId).first()
        val detail = repo.taskDetail(taskId).first()
            ?: runCatching { Graph.online.fetchTask(taskId) }.getOrNull()
        if (task == null && detail == null) {
            error.value = "این تکلیف روی گوشی نیست؛ ابتدا همگام‌سازی کنید."
            return
        }
        val deadline = (detail?.deadlineAt ?: null)?.let { runCatching { OffsetDateTime.parse(it).toLocalDateTime() }.getOrNull() }
        val f = EditForm(
            classroomId = detail?.classroom?.id ?: task?.classroomId,
            lessonId = detail?.lesson?.id ?: task?.lessonId,
            description = detail?.description ?: task?.description.orEmpty(),
            deadline = deadline,
            expectedAnswer = detail?.expectedAnswer.orEmpty(),
            dependencies = detail?.dependencies?.map { it.id }?.toSet().orEmpty(),
            links = detail?.links?.map { LinkRow(it.linkPage.orEmpty(), it.linkDownload.orEmpty()) }.orEmpty(),
        )
        existingFiles.value = detail?.files.orEmpty()
        initial = f
        _form.value = f
    }

    fun lessonsOf(classroomId: Long?): List<LessonDto> = lessons.value[classroomId].orEmpty()

    fun edit(transform: (EditForm) -> EditForm) {
        _form.update(transform)
    }

    fun selectClassroom(id: Long) {
        _form.update { f ->
            val only = lessons.value[id]?.singleOrNull()
            f.copy(classroomId = id, lessonId = only?.id, dependencies = emptySet())
        }
    }

    fun selectLesson(id: Long) {
        _form.update { it.copy(lessonId = id, dependencies = emptySet()) }
    }

    fun toggleDependency(id: Long) {
        _form.update { f -> f.copy(dependencies = if (id in f.dependencies) f.dependencies - id else f.dependencies + id) }
    }

    fun addLink() {
        _form.update { it.copy(links = it.links + LinkRow()) }
    }

    fun updateLink(index: Int, row: LinkRow) {
        _form.update { f -> f.copy(links = f.links.mapIndexed { i, l -> if (i == index) row else l }) }
    }

    fun removeLink(index: Int) {
        _form.update { f -> f.copy(links = f.links.filterIndexed { i, _ -> i != index }) }
    }

    fun addFiles(resolver: ContentResolver, uris: List<Uri>) {
        val picked = uris.map { uri -> describe(resolver, uri) }
        _form.update { f -> f.copy(newFiles = f.newFiles + picked.filter { p -> f.newFiles.none { it.uri == p.uri } }) }
    }

    fun removeNewFile(index: Int) {
        _form.update { f -> f.copy(newFiles = f.newFiles.filterIndexed { i, _ -> i != index }) }
    }

    fun toggleRemoveExisting(id: Long) {
        _form.update { f -> f.copy(removeFileIds = if (id in f.removeFileIds) f.removeFileIds - id else f.removeFileIds + id) }
    }

    private fun describe(resolver: ContentResolver, uri: Uri): PickedFile {
        var name = "file"
        var size = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        return PickedFile(uri, name, size, resolver.getType(uri) ?: "application/octet-stream")
    }

    fun submit() {
        if (saving.value) return
        val f = _form.value
        val problems = validate(f)
        fieldErrors.value = problems
        error.value = null
        if (problems.isNotEmpty()) return
        if (!Graph.network.online.value) {
            error.value = "برای ذخیره‌ی تکلیف باید به اینترنت وصل باشید."
            return
        }

        viewModelScope.launch {
            saving.value = true
            try {
                val res = if (isNew) Graph.online.createTask(createBody(f), f.newFiles)
                else Graph.online.updateTask(taskId, updateBody(f), f.newFiles)
                // a new task has no submission rows yet: fetch them (and the rest) in the background
                Graph.scheduler.syncNow(full = false)
                result.value = res
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val se = e.toServerError()
                error.value = se.message
                fieldErrors.value = se.fields
            } finally {
                saving.value = false
            }
        }
    }

    private fun validate(f: EditForm): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (isNew) {
            if (classrooms.value.size > 1 && f.classroomId == null) errors["classroom_id"] = "کلاس را انتخاب کنید."
            if (f.lessonId == null) errors["lesson_id"] = "درس را انتخاب کنید."
        }
        if (f.description.isBlank()) errors["description"] = "شرح تکلیف را بنویسید."
        if (f.deadline == null) errors["deadline_at"] = "مهلت تحویل را مشخص کنید."
        f.links.forEachIndexed { i, l ->
            val url = l.download.trim()
            if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) {
                errors["links.$i.linkDownload"] = "آدرس باید با http:// یا https:// شروع شود."
            }
        }
        return errors
    }

    private fun linksJson(f: EditForm): JsonArray = JsonArray(
        f.links
            .filter { it.download.isNotBlank() || it.page.isNotBlank() }
            .map { l ->
                buildJsonObject {
                    if (l.page.isNotBlank()) put("linkPage", JsonPrimitive(l.page.trim()))
                    if (l.download.isNotBlank()) put("linkDownload", JsonPrimitive(l.download.trim()))
                }
            },
    )

    private fun deadlineText(dt: LocalDateTime): String = dt.format(DATE_TIME)

    private fun createBody(f: EditForm): JsonObject = buildJsonObject {
        if (classrooms.value.size > 1) f.classroomId?.let { put("classroom_id", JsonPrimitive(it)) }
        put("lesson_id", JsonPrimitive(f.lessonId))
        put("description", JsonPrimitive(f.description.trim()))
        f.deadline?.let { put("deadline_at", JsonPrimitive(deadlineText(it))) }
        if (f.expectedAnswer.isNotBlank()) put("expected_answer", JsonPrimitive(f.expectedAnswer.trim()))
        if (f.dependencies.isNotEmpty()) put("dependencies", JsonArray(f.dependencies.map { JsonPrimitive(it) }))
        val links = linksJson(f)
        if (links.isNotEmpty()) put("links", links)
    }

    /** Only what changed: sending the old links again would make the server download them again. */
    private fun updateBody(f: EditForm): JsonObject = buildJsonObject {
        put("description", JsonPrimitive(f.description.trim()))
        if (f.deadline != initial.deadline) f.deadline?.let { put("deadline_at", JsonPrimitive(deadlineText(it))) }
        if (f.expectedAnswer.trim() != initial.expectedAnswer.trim()) put("expected_answer", JsonPrimitive(f.expectedAnswer.trim()))
        if (f.dependencies != initial.dependencies) put("dependencies", JsonArray(f.dependencies.map { JsonPrimitive(it) }))
        if (f.links != initial.links) put("links", linksJson(f))
        if (f.removeFileIds.isNotEmpty()) put("remove_media_ids", JsonArray(f.removeFileIds.map { JsonPrimitive(it) }))
    }

    private companion object {
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

class TaskEditViewModelFactory(private val taskId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskEditViewModel(taskId) as T
}
