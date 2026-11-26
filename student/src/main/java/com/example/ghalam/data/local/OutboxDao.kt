package com.example.ghalam.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    // ---------------- photos ----------------

    @Query("SELECT * FROM pending_uploads WHERE taskUuid = :taskUuid ORDER BY createdAt ASC")
    fun uploadsFor(taskUuid: String): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads WHERE state = 'PENDING' ORDER BY createdAt ASC")
    suspend fun pendingUploads(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE state = 'HELD' ORDER BY createdAt ASC")
    suspend fun heldUploads(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE id = :id")
    suspend fun upload(id: String): PendingUploadEntity?

    @Insert
    suspend fun insertUpload(item: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deleteUpload(id: String)

    @Query("UPDATE pending_uploads SET state = :state, lastError = :error WHERE id = :id")
    suspend fun setState(id: String, state: String, error: String?)

    /** After a crash or a killed process nothing is really uploading any more. */
    @Query("UPDATE pending_uploads SET state = 'PENDING' WHERE state = 'UPLOADING'")
    suspend fun resetUploading()

    /** The answer record was created by an earlier upload: the others of the question join it. */
    @Query("UPDATE pending_uploads SET answerId = :answerId WHERE taskUuid = :taskUuid AND questionId = :questionId AND answerId = 0")
    suspend fun adoptAnswerId(taskUuid: String, questionId: Int, answerId: Int)

    @Query("UPDATE pending_uploads SET maybeSent = 1 WHERE id = :id")
    suspend fun markMaybeSent(id: String)

    @Query("UPDATE pending_uploads SET state = 'PENDING', attempts = :attempts, nextAttemptAt = :next, lastError = :error WHERE id = :id")
    suspend fun scheduleRetry(id: String, attempts: Int, next: Long, error: String?)

    @Query("UPDATE pending_uploads SET state = 'PENDING', attempts = 0, nextAttemptAt = 0, lastError = NULL WHERE id = :id AND state IN ('FAILED', 'HELD')")
    suspend fun revive(id: String)

    @Query("DELETE FROM pending_uploads")
    suspend fun clearUploads()

    // ---------------- text answers ----------------

    @Query("SELECT * FROM pending_answers WHERE taskUuid = :taskUuid")
    fun answersFor(taskUuid: String): Flow<List<PendingAnswerEntity>>

    @Query("SELECT * FROM pending_answers WHERE dirty = 1 AND failed = 0 ORDER BY updatedAt ASC")
    suspend fun dirtyAnswers(): List<PendingAnswerEntity>

    @Upsert
    suspend fun upsertAnswer(item: PendingAnswerEntity)

    /** Only when the text did not change while it was being sent ([stamp] = updatedAt at send time). */
    @Query("UPDATE pending_answers SET dirty = 0, attempts = 0, nextAttemptAt = 0, lastError = NULL WHERE taskUuid = :taskUuid AND questionId = :questionId AND updatedAt = :stamp")
    suspend fun markClean(taskUuid: String, questionId: Int, stamp: Long): Int

    @Query("UPDATE pending_answers SET failed = 1, lastError = :error WHERE taskUuid = :taskUuid AND questionId = :questionId AND updatedAt = :stamp")
    suspend fun failAnswer(taskUuid: String, questionId: Int, stamp: Long, error: String?)

    @Query("UPDATE pending_answers SET attempts = :attempts, nextAttemptAt = :next, lastError = :error WHERE taskUuid = :taskUuid AND questionId = :questionId AND updatedAt = :stamp")
    suspend fun retryAnswer(taskUuid: String, questionId: Int, stamp: Long, attempts: Int, next: Long, error: String?)

    @Query("UPDATE pending_answers SET failed = 0, attempts = 0, nextAttemptAt = 0, lastError = NULL WHERE taskUuid = :taskUuid AND questionId = :questionId")
    suspend fun reviveAnswer(taskUuid: String, questionId: Int)

    @Query("DELETE FROM pending_answers WHERE taskUuid = :taskUuid AND dirty = 0")
    suspend fun dropCleanAnswers(taskUuid: String)

    @Query("DELETE FROM pending_answers")
    suspend fun clearAnswers()

    // ---------------- local copies of uploaded photos ----------------

    @Query("SELECT * FROM local_media WHERE taskUuid = :taskUuid ORDER BY createdAt DESC")
    fun localFor(taskUuid: String): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE taskUuid = :taskUuid")
    suspend fun localOnce(taskUuid: String): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE remoteUuid = :remoteUuid")
    suspend fun local(remoteUuid: String): LocalMediaEntity?

    @Upsert
    suspend fun putLocal(item: LocalMediaEntity)

    @Query("DELETE FROM local_media WHERE remoteUuid = :remoteUuid")
    suspend fun deleteLocal(remoteUuid: String)

    @Query("DELETE FROM local_media")
    suspend fun clearLocal()

    // ---------------- list badges ----------------

    @Query(
        """
        SELECT taskUuid AS taskUuid, COUNT(*) AS total, SUM(failed) AS failed, SUM(held) AS held FROM (
            SELECT taskUuid AS taskUuid,
                   CASE WHEN state = 'FAILED' THEN 1 ELSE 0 END AS failed,
                   CASE WHEN state = 'HELD' THEN 1 ELSE 0 END AS held
            FROM pending_uploads
            UNION ALL
            SELECT taskUuid AS taskUuid, failed AS failed, 0 AS held
            FROM pending_answers
            WHERE dirty = 1
        ) GROUP BY taskUuid
        """
    )
    fun summary(): Flow<List<TaskOutboxSummary>>
}
