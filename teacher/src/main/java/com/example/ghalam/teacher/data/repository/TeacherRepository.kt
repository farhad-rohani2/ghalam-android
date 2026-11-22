package com.example.ghalam.teacher.data.repository

import androidx.room.withTransaction
import com.example.ghalam.teacher.data.api.AnswerDto
import com.example.ghalam.teacher.data.api.FeedbackDto
import com.example.ghalam.teacher.data.api.FeedbackRowDto
import com.example.ghalam.teacher.data.api.LessonDto
import com.example.ghalam.teacher.data.api.LevelDto
import com.example.ghalam.teacher.data.api.MetaDto
import com.example.ghalam.teacher.data.api.ParamDto
import com.example.ghalam.teacher.data.api.TaskDetailDto
import com.example.ghalam.teacher.data.local.ClassroomEntity
import com.example.ghalam.teacher.data.local.FeedbackEntity
import com.example.ghalam.teacher.data.local.FeedbackState
import com.example.ghalam.teacher.data.local.SubmissionEntity
import com.example.ghalam.teacher.data.local.SubmissionRow
import com.example.ghalam.teacher.data.local.TaskEntity
import com.example.ghalam.teacher.data.local.TaskRow
import com.example.ghalam.teacher.data.local.TeacherDatabase
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Offline-first access to everything the teacher sees. The UI only reads from Room; the sync
 * engine fills it. Feedback is written to Room first (state PENDING) and uploaded later.
 */
class TeacherRepository(
    private val db: TeacherDatabase,
    private val json: Json,
    private val onFeedbackChanged: () -> Unit,
) {
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("fa"))

    private val rowsSerializer = ListSerializer(FeedbackRowDto.serializer())
    private val lessonsSerializer = ListSerializer(LessonDto.serializer())

    private fun <T> decode(serializer: KSerializer<T>, text: String): T? =
        runCatching { json.decodeFromString(serializer, text) }.getOrNull()

    // ---------------------------------------------------------------- reads

    fun tasks(): Flow<List<TaskRow>> = db.taskDao().observeRows()

    fun task(id: Long): Flow<TaskEntity?> = db.taskDao().observe(id)

    fun classrooms(): Flow<List<ClassroomEntity>> = db.classroomDao().observeAll()

    fun taskDetail(id: Long): Flow<TaskDetailDto?> =
        db.cacheDao().observe("task:$id").map { row -> row?.let { decode(TaskDetailDto.serializer(), it.json) } }

    fun submissions(taskId: Long): Flow<List<SubmissionRow>> = db.submissionDao().observeForTask(taskId)

    fun submission(taskId: Long, studentId: Long): Flow<SubmissionEntity?> =
        db.submissionDao().observe(taskId, studentId)

    fun answer(taskId: Long, studentId: Long): Flow<AnswerDto?> =
        db.answerDao().observe(taskId, studentId).map { row -> row?.let { decode(AnswerDto.serializer(), it.json) } }

    fun unsyncedCount(): Flow<Int> = db.feedbackDao().observeUnsyncedCount()

    fun levels(): Flow<List<LevelDto>> = db.cacheDao().observe("meta").map { row ->
        row?.let { decode(MetaDto.serializer(), it.json) }?.levels?.takeIf { it.isNotEmpty() } ?: DEFAULT_LEVELS
    }

    /** Student ids of the task in name order: the previous / next buttons walk through them. */
    suspend fun sortedStudentIds(taskId: Long): List<Long> =
        db.submissionDao().listForTask(taskId)
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .map { it.studentId }

    /**
     * The feedback form of one student. The base rows come from the lesson's parameters (saved with
     * the classrooms), so the form works even when the student has no feedback yet; extra
     * parameters that other students of the task got also show up (without a level).
     */
    fun feedbackScreen(taskId: Long, studentId: Long): Flow<FeedbackScreenState> = combine(
        db.feedbackDao().observe(taskId, studentId),
        db.feedbackDao().observeForTask(taskId),
        db.taskDao().observe(taskId),
        db.classroomDao().observeAll(),
        levels(),
    ) { mine, all, task, classrooms, levels ->
        val params = task?.let { paramsOf(it, classrooms) } ?: emptyList()
        val extraTitles = all.flatMap { decodeRows(it.rowsJson) }.filter { it.extra }.map { it.title }
        FeedbackScreenState(
            form = buildForm(mine, params, extraTitles),
            state = mine?.syncState,
            lastError = mine?.lastError,
            levels = levels,
        )
    }

    private fun paramsOf(task: TaskEntity, classrooms: List<ClassroomEntity>): List<ParamDto> {
        val lessons = classrooms.firstOrNull { it.id == task.classroomId }
            ?.let { decode(lessonsSerializer, it.lessonsJson) } ?: return emptyList()
        return lessons.firstOrNull { it.id == task.lessonId }?.parameters ?: emptyList()
    }

    private fun buildForm(saved: FeedbackEntity?, params: List<ParamDto>, extraTitlesOnTask: List<String>): FeedbackForm {
        val rows = saved?.let { decodeRows(it.rowsJson) } ?: emptyList()
        val byKey = rows.associateBy { it.key }
        val base = params.map { p -> FeedbackRowDto(key = p.key, title = p.title, extra = false, level = byKey[p.key]?.level) }
        val savedExtras = rows.filter { it.extra }
        val titles = (savedExtras.map { it.title } + extraTitlesOnTask).filter { it.isNotBlank() }.distinct()
        val extras = titles.map { t ->
            val s = savedExtras.firstOrNull { it.title == t }
            FeedbackRowDto(key = s?.key ?: "local:$t", title = t, extra = true, level = s?.level)
        }
        return FeedbackForm(
            overallLevel = saved?.overallLevel,
            rows = base + extras,
            positives = saved?.positives.orEmpty(),
            negatives = saved?.negatives.orEmpty(),
            suggestions = saved?.suggestions.orEmpty(),
        )
    }

    // ---------------------------------------------------------------- writes by the teacher

    /** Writes the feedback to Room (state PENDING) and asks for an upload; works fully offline. */
    suspend fun saveFeedback(taskId: Long, studentId: Long, form: FeedbackForm) {
        val now = System.currentTimeMillis()
        var changed = false
        db.withTransaction {
            val dao = db.feedbackDao()
            val old = dao.get(taskId, studentId)
            if (old == null && form.isEmpty) return@withTransaction
            val state = if (old?.syncState == FeedbackState.CONFLICT) FeedbackState.CONFLICT else FeedbackState.PENDING
            dao.upsert(
                FeedbackEntity(
                    taskId = taskId,
                    studentId = studentId,
                    overallLevel = form.overallLevel,
                    positives = form.positives,
                    negatives = form.negatives,
                    suggestions = form.suggestions,
                    rowsJson = encodeRows(form.rows),
                    baseUpdatedAt = old?.baseUpdatedAt,
                    syncState = state,
                    lastError = null,
                    serverJson = old?.serverJson,
                    localUpdatedAt = now,
                    syncedAt = old?.syncedAt ?: 0L,
                ),
            )
            changed = true
        }
        if (changed) onFeedbackChanged()
    }

    /** After an ERROR (e.g. the teacher fixed the problem or the server was fixed). */
    suspend fun retryFeedback(taskId: Long, studentId: Long) {
        db.withTransaction {
            val cur = db.feedbackDao().get(taskId, studentId) ?: return@withTransaction
            if (cur.syncState == FeedbackState.ERROR) {
                db.feedbackDao().upsert(cur.copy(syncState = FeedbackState.PENDING, lastError = null))
            }
        }
        onFeedbackChanged()
    }

    /** CONFLICT -> "keep mine": overwrite the server copy with the phone's version. */
    suspend fun keepMine(taskId: Long, studentId: Long) {
        db.withTransaction {
            val cur = db.feedbackDao().get(taskId, studentId) ?: return@withTransaction
            val server = cur.serverJson?.let { decode(FeedbackDto.serializer(), it) }
            db.feedbackDao().upsert(
                cur.copy(
                    baseUpdatedAt = if (server?.exists == true) server.updatedAt else null,
                    syncState = FeedbackState.PENDING,
                    serverJson = null,
                    lastError = null,
                    localUpdatedAt = System.currentTimeMillis(),
                ),
            )
        }
        onFeedbackChanged()
    }

    /** CONFLICT -> "take the server's": the phone's edit is dropped. */
    suspend fun takeServer(taskId: Long, studentId: Long) {
        db.withTransaction {
            val cur = db.feedbackDao().get(taskId, studentId) ?: return@withTransaction
            val server = cur.serverJson?.let { decode(FeedbackDto.serializer(), it) }
            if (server == null || !server.exists) {
                db.feedbackDao().delete(taskId, studentId)
            } else {
                db.feedbackDao().upsert(entityFromServer(taskId, studentId, server, System.currentTimeMillis()))
            }
        }
    }

    // ---------------------------------------------------------------- used by the sync engine

    /** Server -> phone. Never overwrites a copy that still has unsent changes. */
    suspend fun applyServerFeedback(taskId: Long, studentId: Long, dto: FeedbackDto, now: Long) {
        db.withTransaction {
            val dao = db.feedbackDao()
            val cur = dao.get(taskId, studentId)
            if (cur != null && cur.syncState != FeedbackState.SYNCED) return@withTransaction
            if (!dto.exists) {
                if (cur != null) dao.delete(taskId, studentId)
            } else {
                dao.upsert(entityFromServer(taskId, studentId, dto, now))
            }
        }
    }

    suspend fun markConflict(sent: FeedbackEntity, server: FeedbackDto) {
        db.withTransaction {
            val cur = db.feedbackDao().get(sent.taskId, sent.studentId) ?: return@withTransaction
            db.feedbackDao().upsert(
                cur.copy(
                    syncState = FeedbackState.CONFLICT,
                    serverJson = json.encodeToString(FeedbackDto.serializer(), server),
                    lastError = null,
                ),
            )
        }
    }

    suspend fun markError(sent: FeedbackEntity, message: String) {
        db.withTransaction {
            val cur = db.feedbackDao().get(sent.taskId, sent.studentId) ?: return@withTransaction
            // edited again in the meantime: leave it PENDING, the next upload sends the newer text
            if (cur.localUpdatedAt == sent.localUpdatedAt) {
                db.feedbackDao().upsert(cur.copy(syncState = FeedbackState.ERROR, lastError = message))
            }
        }
    }

    /** The server accepted [sent]; [saved] is what it stored. */
    suspend fun onUploaded(sent: FeedbackEntity, saved: FeedbackDto, now: Long) {
        db.withTransaction {
            val dao = db.feedbackDao()
            val cur = dao.get(sent.taskId, sent.studentId) ?: return@withTransaction
            val stamp = if (saved.exists) saved.updatedAt else null
            if (cur.localUpdatedAt == sent.localUpdatedAt) {
                if (!saved.exists) dao.delete(sent.taskId, sent.studentId)
                else dao.upsert(entityFromServer(sent.taskId, sent.studentId, saved, now))
            } else {
                // edited again while uploading: keep the newer edit, based on what the server has now
                dao.upsert(cur.copy(baseUpdatedAt = stamp, syncState = FeedbackState.PENDING))
            }
        }
    }

    /** The body of PUT .../feedback for [e]. */
    fun buildSaveBody(e: FeedbackEntity): JsonObject {
        val rows = decodeRows(e.rowsJson).map { r ->
            buildJsonObject {
                if (r.extra) {
                    put("title", JsonPrimitive(r.title))
                    put("extra", JsonPrimitive(true))
                } else {
                    put("key", JsonPrimitive(r.key))
                }
                put("level", r.level?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        return buildJsonObject {
            put("overall_level", e.overallLevel?.let { JsonPrimitive(it) } ?: JsonNull)
            put("rows", JsonArray(rows))
            put("positives", JsonPrimitive(e.positives.trim()))
            put("negatives", JsonPrimitive(e.negatives.trim()))
            put("suggestions", JsonPrimitive(e.suggestions.trim()))
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun entityFromServer(taskId: Long, studentId: Long, dto: FeedbackDto, now: Long) = FeedbackEntity(
        taskId = taskId,
        studentId = studentId,
        overallLevel = dto.overallLevel,
        positives = dto.positives,
        negatives = dto.negatives,
        suggestions = dto.suggestions,
        rowsJson = encodeRows(dto.rows),
        baseUpdatedAt = dto.updatedAt,
        syncState = FeedbackState.SYNCED,
        lastError = null,
        serverJson = null,
        localUpdatedAt = now,
        syncedAt = now,
    )

    private fun encodeRows(rows: List<FeedbackRowDto>): String = json.encodeToString(rowsSerializer, rows)

    private fun decodeRows(text: String): List<FeedbackRowDto> = decode(rowsSerializer, text) ?: emptyList()
}
