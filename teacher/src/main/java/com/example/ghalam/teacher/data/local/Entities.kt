package com.example.ghalam.teacher.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Sync state of one student's feedback. Everything except SYNCED still has to reach the server. */
object FeedbackState {
    const val SYNCED = "SYNCED"
    const val PENDING = "PENDING"   // saved on the phone, waiting for the upload
    const val CONFLICT = "CONFLICT" // the server copy changed meanwhile; the teacher decides
    const val ERROR = "ERROR"       // the server refused it (validation / no access)
}

@Entity(tableName = "classrooms")
data class ClassroomEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val degree: Int?,
    val studentsCount: Int,
    /** JSON of List<LessonDto>: lessons with their feedback parameters. */
    val lessonsJson: String,
)

@Entity(tableName = "tasks", indices = [Index("deadlineEpoch")])
data class TaskEntity(
    @PrimaryKey val id: Long,
    val uuid: String,
    val classroomId: Long,
    val classroomTitle: String,
    val lessonId: Long,
    val lessonTitle: String,
    val description: String,
    val deadlineEpoch: Long?,
    val deadlineJalali: String?,
    val statsTotal: Int,
    val statsAnswered: Int,
    val statsDone: Int,
    /** 0 = submissions were never downloaded for this task. */
    val submissionsSyncedAt: Long,
)

/** A task plus how many of its feedbacks are still waiting for the server. */
data class TaskRow(@Embedded val task: TaskEntity, val pendingCount: Int)

@Entity(tableName = "submissions", primaryKeys = ["taskId", "studentId"], indices = [Index("taskId")])
data class SubmissionEntity(
    val taskId: Long,
    val studentId: Long,
    val name: String,
    val firstName: String,
    val lastName: String,
    val photo: String?,
    /** done | no_file | none */
    val status: String,
    val statusLabel: String,
    val filesCount: Int,
    val answeredAt: String?,
    val answeredAtJalali: String?,
    val serverHasFeedback: Boolean,
)

/** A submission plus the state of the local feedback copy (null = no feedback saved). */
data class SubmissionRow(
    @Embedded val s: SubmissionEntity,
    val feedbackState: String?,
    val feedbackLevel: Int?,
)

/** Raw JSON of AnswerDto. [answeredAtKey] says which version of the submission it was fetched for. */
@Entity(tableName = "answers", primaryKeys = ["taskId", "studentId"])
data class AnswerEntity(
    val taskId: Long,
    val studentId: Long,
    val json: String,
    val answeredAtKey: String?,
    val syncedAt: Long,
)

data class AnswerKeyRow(val studentId: Long, val answeredAtKey: String?)

/**
 * The local copy of one student's feedback and, at the same time, its upload queue entry:
 * every state except SYNCED is waiting for the server.
 */
@Entity(tableName = "feedback", primaryKeys = ["taskId", "studentId"], indices = [Index("syncState")])
data class FeedbackEntity(
    val taskId: Long,
    val studentId: Long,
    val overallLevel: Int?,
    val positives: String,
    val negatives: String,
    val suggestions: String,
    /** JSON of List<FeedbackRowDto>: base parameters and extras with their levels. */
    val rowsJson: String,
    /** The server's updated_at this copy is based on (null = the server had no feedback). */
    val baseUpdatedAt: String?,
    val syncState: String,
    val lastError: String?,
    /** Raw FeedbackDto of the server, kept only while the state is CONFLICT. */
    val serverJson: String?,
    val localUpdatedAt: Long,
    val syncedAt: Long,
)

data class FeedbackStamp(val studentId: Long, val syncedAt: Long)

/** Index of downloaded files. path == null: known but not downloaded. */
@Entity(tableName = "media_files", indices = [Index("taskId")])
data class MediaEntity(
    @PrimaryKey val fileKey: String,
    val url: String,
    val name: String,
    val mime: String?,
    val kind: String,
    val sizeBytes: Long,
    val path: String?,
    val taskId: Long,
    val updatedAt: Long,
)

/** Raw-JSON cache: "meta", "task:<id>". */
@Entity(tableName = "cached_json")
data class JsonCacheEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAt: Long,
)

/**
 * The teacher's feedback for the whole class on one task (no per-student part). The server keeps no
 * modification time for it, so a newer write simply wins.
 */
@Entity(tableName = "general_feedback")
data class GeneralFeedbackEntity(
    @PrimaryKey val taskId: Long,
    val positives: String,
    val negatives: String,
    val suggestions: String,
    /** SYNCED | PENDING | ERROR (see [FeedbackState]). */
    val syncState: String,
    val lastError: String?,
    val localUpdatedAt: Long,
    val syncedAt: Long,
)
