package com.example.ghalam.teacher.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

object MediaQueueState {
    const val PENDING = "PENDING" // waiting for the internet
    const val ERROR = "ERROR"     // the server refused it; the teacher retries or discards
}

/**
 * A recording of a feedback that is waiting for the upload. studentId = 0 means the class-wide
 * feedback of the task. The file lives in filesDir/voice_queue until it is sent.
 */
@Entity(tableName = "feedback_media_queue")
data class QueuedMediaEntity(
    @PrimaryKey val id: String,
    val taskId: Long,
    val studentId: Long,
    val localPath: String,
    val fileName: String,
    val mime: String,
    val durationMs: Long,
    val createdAt: Long,
    val state: String,
    val lastError: String?,
)

@Dao
interface MediaQueueDao {
    @Query("SELECT * FROM feedback_media_queue WHERE taskId = :taskId AND studentId = :studentId ORDER BY createdAt")
    fun observe(taskId: Long, studentId: Long): Flow<List<QueuedMediaEntity>>

    @Query("SELECT * FROM feedback_media_queue WHERE state = 'PENDING' ORDER BY createdAt")
    suspend fun pending(): List<QueuedMediaEntity>

    @Query("SELECT * FROM feedback_media_queue WHERE id = :id")
    suspend fun get(id: String): QueuedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueuedMediaEntity)

    @Query("UPDATE feedback_media_queue SET state = :state, lastError = :error WHERE id = :id")
    suspend fun setState(id: String, state: String, error: String?)

    @Query("DELETE FROM feedback_media_queue WHERE id = :id")
    suspend fun delete(id: String)
}
