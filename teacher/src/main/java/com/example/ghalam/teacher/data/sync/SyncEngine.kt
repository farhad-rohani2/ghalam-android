package com.example.ghalam.teacher.data.sync

import androidx.room.withTransaction
import com.example.ghalam.core.network.util.userMessage
import com.example.ghalam.teacher.data.SyncPrefs
import com.example.ghalam.teacher.data.api.AnswerDto
import com.example.ghalam.teacher.data.api.AnswerFileDto
import com.example.ghalam.teacher.data.api.SubmissionRowDto
import com.example.ghalam.teacher.data.api.TaskDetailDto
import com.example.ghalam.teacher.data.api.TaskDto
import com.example.ghalam.teacher.data.api.TeacherApi
import com.example.ghalam.teacher.data.local.AnswerEntity
import com.example.ghalam.teacher.data.local.ClassroomEntity
import com.example.ghalam.teacher.data.local.FeedbackState
import com.example.ghalam.teacher.data.local.JsonCacheEntity
import com.example.ghalam.teacher.data.local.TeacherDatabase
import com.example.ghalam.teacher.data.local.toEntity
import com.example.ghalam.teacher.data.media.TeacherMedia
import com.example.ghalam.teacher.data.repository.TeacherRepository
import com.example.ghalam.teacher.util.NetworkMonitor
import com.example.ghalam.teacher.util.teacherMessage
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * The two jobs of the background workers:
 *  1. upload: every feedback written offline (state PENDING) is sent to the server;
 *  2. download: classes, tasks of the last month, who submitted what, the answers and the
 *     already existing feedback, then (on Wi-Fi) the answer files.
 *
 * Both run under one lock, so a manual refresh and the periodic worker never overlap.
 */
class SyncEngine(
    private val api: TeacherApi,
    private val db: TeacherDatabase,
    private val repo: TeacherRepository,
    private val media: TeacherMedia,
    private val prefs: SyncPrefs,
    private val network: NetworkMonitor,
    private val json: Json,
) {
    enum class Outcome { OK, RETRY, SIGNED_OUT }

    private val lock = Mutex()

    /**
     * [full] also refreshes task details, already known feedback and the submissions of older tasks.
     * [uploadOnly] only sends pending feedback.
     */
    suspend fun run(full: Boolean, uploadOnly: Boolean = false): Outcome = lock.withLock {
        SyncStatus.update { it.copy(running = true, message = "ارسال بازخوردها…", lastError = null) }
        try {
            val upload = uploadPending()
            if (upload == Outcome.SIGNED_OUT) return@withLock finish(Outcome.SIGNED_OUT, uploadOnly, null)
            if (!uploadOnly) download(full)
            finish(if (upload == Outcome.RETRY) Outcome.RETRY else Outcome.OK, uploadOnly, null)
        } catch (e: CancellationException) {
            SyncStatus.update { it.copy(running = false, message = null) }
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401) finish(Outcome.SIGNED_OUT, uploadOnly, null)
            else finish(Outcome.RETRY, uploadOnly, e.teacherMessage())
        } catch (e: IOException) {
            finish(Outcome.RETRY, uploadOnly, e.userMessage())
        }
    }

    private fun finish(outcome: Outcome, uploadOnly: Boolean, error: String?): Outcome {
        val now = System.currentTimeMillis()
        if (outcome == Outcome.OK && !uploadOnly) prefs.lastSyncAt = now
        SyncStatus.update {
            it.copy(
                running = false,
                message = null,
                lastError = error,
                lastSuccessAt = if (outcome == Outcome.OK && !uploadOnly) now else it.lastSuccessAt,
            )
        }
        return outcome
    }

    // ---------------------------------------------------------------- upload

    /** Network failures (IOException) propagate: the work is retried later. */
    private suspend fun uploadPending(): Outcome {
        var transient = false
        for (entry in db.feedbackDao().pending()) {
            try {
                uploadOne(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> return Outcome.SIGNED_OUT
                    403, 404, 422 -> repo.markError(entry, e.teacherMessage())
                    else -> transient = true
                }
            }
        }
        return if (transient) Outcome.RETRY else Outcome.OK
    }

    private suspend fun uploadOne(entry: com.example.ghalam.teacher.data.local.FeedbackEntity) {
        // never overwrite what someone else (the web panel) changed since this edit was started
        val server = api.feedback(entry.taskId, entry.studentId).data
        val serverStamp = if (server.exists) server.updatedAt else null
        if (serverStamp != entry.baseUpdatedAt) {
            repo.markConflict(entry, server)
            return
        }
        val saved = api.saveFeedback(entry.taskId, entry.studentId, repo.buildSaveBody(entry)).data
        repo.onUploaded(entry, saved, System.currentTimeMillis())
    }

    // ---------------------------------------------------------------- download

    private suspend fun download(full: Boolean) {
        val now = System.currentTimeMillis()

        status("دریافت اطلاعات پایه…")
        val meta = api.meta().data
        db.cacheDao().put(JsonCacheEntity("meta", json.encodeToString(com.example.ghalam.teacher.data.api.MetaDto.serializer(), meta), now))

        val classrooms = api.classrooms().data
        val lessonsSerializer = ListSerializer(com.example.ghalam.teacher.data.api.LessonDto.serializer())
        db.withTransaction {
            db.classroomDao().clear()
            db.classroomDao().insertAll(
                classrooms.map {
                    ClassroomEntity(it.id, it.title, it.degree, it.studentsCount, json.encodeToString(lessonsSerializer, it.lessons))
                },
            )
        }

        status("دریافت فهرست تکالیف…")
        val from = LocalDate.now().minusDays(WINDOW_DAYS).toString()
        val fetched = mutableListOf<TaskDto>()
        var page = 1
        while (true) {
            val res = api.tasks(page = page, from = from)
            fetched += res.data
            if (page >= res.meta.lastPage || res.data.isEmpty()) break
            page++
        }

        val existing = db.taskDao().getAll().associateBy { it.id }
        db.taskDao().upsertAll(fetched.map { it.toEntity(existing[it.id]?.submissionsSyncedAt ?: 0L) })

        // tasks that left the window (or were deleted on the server) go away, unless feedback for
        // them is still waiting to be uploaded
        val fetchedIds = fetched.map { it.id }.toSet()
        val keep = db.feedbackDao().taskIdsWithUnsynced().toSet()
        removeTasks(existing.keys.filter { it !in fetchedIds && it !in keep })

        status("دریافت پاسخ‌های دانش‌آموزان…")
        val gate = Semaphore(4)
        coroutineScope {
            fetched.map { task -> async { syncTask(task, full, now, gate) } }.awaitAll()
        }

        prefetchFiles(now)
    }

    private suspend fun syncTask(t: TaskDto, full: Boolean, now: Long, gate: Semaphore) {
        val epoch = com.example.ghalam.teacher.data.local.parseEpoch(t.deadlineAt)
        val active = epoch == null || epoch >= now - ACTIVE_DAYS * DAY_MS
        val local = db.taskDao().get(t.id)
        if (!(active || full || (local?.submissionsSyncedAt ?: 0L) == 0L)) return

        try {
            val cached = db.cacheDao().get("task:${t.id}")
            if (cached == null || full || now - cached.updatedAt > DETAIL_REFRESH_MS) {
                val detail = gate.withPermit { api.task(t.id) }.data
                db.cacheDao().put(JsonCacheEntity("task:${t.id}", json.encodeToString(TaskDetailDto.serializer(), detail), now))
            }

            val rows = mutableListOf<SubmissionRowDto>()
            var page = 1
            while (true) {
                val res = gate.withPermit { api.submissions(t.id, page) }
                rows += res.data
                if (page >= res.meta.lastPage || res.data.isEmpty()) break
                page++
            }
            db.withTransaction {
                db.submissionDao().deleteForTask(t.id)
                db.submissionDao().insertAll(rows.map { it.toEntity(t.id) })
            }

            val knownKeys = db.answerDao().keys(t.id).associate { it.studentId to it.answeredAtKey }
            coroutineScope {
                rows.map { r -> async { syncStudent(t.id, r, knownKeys[r.id], full, now, gate) } }.awaitAll()
            }
            db.taskDao().markSubmissionsSynced(t.id, now)
        } catch (e: HttpException) {
            // 401: the session is over. Anything else (deleted task, no access, server error): skip this task.
            if (e.code() == 401) throw e
        }
    }

    private suspend fun syncStudent(
        taskId: Long,
        r: SubmissionRowDto,
        knownKey: String?,
        full: Boolean,
        now: Long,
        gate: Semaphore,
    ) {
        try {
            if (r.status == "none") {
                db.answerDao().delete(taskId, r.id)
            } else {
                val key = "${r.answeredAt}|${r.filesCount}|${r.status}"
                if (knownKey != key) {
                    val dto = gate.withPermit { api.answer(taskId, r.id) }.data
                    db.answerDao().upsert(AnswerEntity(taskId, r.id, json.encodeToString(AnswerDto.serializer(), dto), key, now))
                }
            }

            val local = db.feedbackDao().get(taskId, r.id)
            when {
                // unsent changes on the phone win
                local != null && local.syncState != FeedbackState.SYNCED -> Unit
                !r.hasFeedback -> if (local != null) db.feedbackDao().delete(taskId, r.id)
                local == null || full || now - local.syncedAt > FEEDBACK_REFRESH_MS -> {
                    val fb = gate.withPermit { api.feedback(taskId, r.id) }.data
                    repo.applyServerFeedback(taskId, r.id, fb, now)
                }
            }
        } catch (e: HttpException) {
            if (e.code() == 401) throw e
        }
    }

    private suspend fun removeTasks(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.answerDao().deleteForTasks(ids)
            db.submissionDao().deleteForTasks(ids)
            db.feedbackDao().deleteSyncedForTasks(ids)
            db.cacheDao().deleteKeys(ids.map { "task:$it" })
            db.taskDao().deleteByIds(ids)
        }
        media.deleteForTasks(ids)
    }

    // ---------------------------------------------------------------- files

    /** Downloads the answer files of current tasks: Wi-Fi only (setting) and below the size limit. */
    private suspend fun prefetchFiles(now: Long) {
        if (!prefs.mediaAuto) return
        if (prefs.mediaUnmeteredOnly && network.isMetered()) return

        status("دریافت فایل‌های دانش‌آموزان…")
        val limit = prefs.mediaLimitBytes
        val files = mutableListOf<Pair<Long, AnswerFileDto>>()
        for (task in db.taskDao().getAll()) {
            val epoch = task.deadlineEpoch
            if (epoch != null && epoch < now - ACTIVE_DAYS * DAY_MS) continue
            for (row in db.answerDao().forTask(task.id)) {
                val answer = runCatching { json.decodeFromString(AnswerDto.serializer(), row.json) }.getOrNull() ?: continue
                answer.answers.flatMap { it.files }
                    .filter { it.url.isNotBlank() && (it.size <= 0 || it.size <= limit) }
                    .forEach { files += task.id to it }
            }
        }

        coroutineScope {
            files.map { (taskId, file) ->
                async {
                    try {
                        media.ensure(absoluteUrl(file.url), file.name, taskId, limit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // one bad file must not stop the rest; it can be downloaded by hand later
                    }
                }
            }.awaitAll()
        }
    }

    private fun status(message: String) = SyncStatus.update { it.copy(running = true, message = message) }

    private companion object {
        const val WINDOW_DAYS = 30L
        const val ACTIVE_DAYS = 14L
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val DETAIL_REFRESH_MS = 12L * 60 * 60 * 1000
        const val FEEDBACK_REFRESH_MS = 24L * 60 * 60 * 1000
    }
}

/** The server sends absolute URLs; this only guards against a relative path. */
fun absoluteUrl(url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) url
    else com.example.ghalam.core.network.data.api.DEFAULT_BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
