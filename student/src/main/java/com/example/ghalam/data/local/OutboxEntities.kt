package com.example.ghalam.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object UploadState {
    const val PENDING = "PENDING"
    const val UPLOADING = "UPLOADING"

    /** Saved on the phone but waiting for the student to press "send" (auto-send off or file too big for auto-send). */
    const val HELD = "HELD"
    const val FAILED = "FAILED"
}

/** A photo that was taken or picked and still has to be uploaded. The JPEG lives in filesDir/outbox. */
@Entity(tableName = "pending_uploads", indices = [Index("taskUuid")])
data class PendingUploadEntity(
    @PrimaryKey val id: String,
    val taskUuid: String,
    val questionId: Int,
    val answerId: Int,
    val fieldUuid: String,
    /** Stable name sent to the server; used to detect an upload that reached the server but whose reply was lost. */
    val fileName: String,
    val localPath: String,
    val sizeBytes: Long,
    val state: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val createdAt: Long,
    val maybeSent: Boolean,
    val lastError: String?,
)

/** The text answer of one question. [dirty] = changed on the phone and not yet confirmed by the server. */
@Entity(tableName = "pending_answers", primaryKeys = ["taskUuid", "questionId"])
data class PendingAnswerEntity(
    val taskUuid: String,
    val questionId: Int,
    val text: String,
    val updatedAt: Long,
    val dirty: Boolean,
    val failed: Boolean,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
)

/** Copy of an answer photo that was uploaded from this phone, so it never has to be downloaded again. */
@Entity(tableName = "local_media", indices = [Index("taskUuid")])
data class LocalMediaEntity(
    @PrimaryKey val remoteUuid: String,
    val taskUuid: String,
    val questionId: Int,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** What the task card shows: how many items of that task are still on their way to the server. */
data class TaskOutboxSummary(
    val taskUuid: String,
    val total: Int,
    val failed: Int,
    val held: Int,
)
