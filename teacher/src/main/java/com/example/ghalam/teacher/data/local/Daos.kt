package com.example.ghalam.teacher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassroomDao {
    @Query("SELECT * FROM classrooms ORDER BY title")
    fun observeAll(): Flow<List<ClassroomEntity>>

    @Query("DELETE FROM classrooms")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ClassroomEntity>)
}

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.*, (SELECT COUNT(*) FROM feedback f WHERE f.taskId = t.id AND f.syncState != 'SYNCED') AS pendingCount
        FROM tasks t
        ORDER BY (t.deadlineEpoch IS NULL), t.deadlineEpoch DESC
        """
    )
    fun observeRows(): Flow<List<TaskRow>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Upsert
    suspend fun upsertAll(items: List<TaskEntity>)

    @Query("UPDATE tasks SET submissionsSyncedAt = :at WHERE id = :id")
    suspend fun markSubmissionsSynced(id: Long, at: Long)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface SubmissionDao {
    @Query(
        """
        SELECT s.*, f.syncState AS feedbackState, f.overallLevel AS feedbackLevel
        FROM submissions s
        LEFT JOIN feedback f ON f.taskId = s.taskId AND f.studentId = s.studentId
        WHERE s.taskId = :taskId
        """
    )
    fun observeForTask(taskId: Long): Flow<List<SubmissionRow>>

    @Query("SELECT * FROM submissions WHERE taskId = :taskId AND studentId = :studentId")
    fun observe(taskId: Long, studentId: Long): Flow<SubmissionEntity?>

    @Query("SELECT * FROM submissions WHERE taskId = :taskId")
    suspend fun listForTask(taskId: Long): List<SubmissionEntity>

    @Query("DELETE FROM submissions WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM submissions WHERE taskId IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SubmissionEntity>)
}

@Dao
interface AnswerDao {
    @Query("SELECT * FROM answers WHERE taskId = :taskId AND studentId = :studentId")
    fun observe(taskId: Long, studentId: Long): Flow<AnswerEntity?>

    @Query("SELECT studentId, answeredAtKey FROM answers WHERE taskId = :taskId")
    suspend fun keys(taskId: Long): List<AnswerKeyRow>

    @Query("SELECT * FROM answers WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): List<AnswerEntity>

    @Upsert
    suspend fun upsert(item: AnswerEntity)

    @Query("DELETE FROM answers WHERE taskId = :taskId AND studentId = :studentId")
    suspend fun delete(taskId: Long, studentId: Long)

    @Query("DELETE FROM answers WHERE taskId IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<Long>)
}

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE taskId = :taskId AND studentId = :studentId")
    fun observe(taskId: Long, studentId: Long): Flow<FeedbackEntity?>

    @Query("SELECT * FROM feedback WHERE taskId = :taskId")
    fun observeForTask(taskId: Long): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM feedback WHERE taskId = :taskId AND studentId = :studentId")
    suspend fun get(taskId: Long, studentId: Long): FeedbackEntity?

    @Query("SELECT * FROM feedback WHERE syncState = 'PENDING' ORDER BY localUpdatedAt")
    suspend fun pending(): List<FeedbackEntity>

    @Query("SELECT COUNT(*) FROM feedback WHERE syncState != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM feedback WHERE syncState != 'SYNCED'")
    suspend fun unsyncedCount(): Int

    @Upsert
    suspend fun upsert(item: FeedbackEntity)

    @Query("DELETE FROM feedback WHERE taskId = :taskId AND studentId = :studentId")
    suspend fun delete(taskId: Long, studentId: Long)

    @Query("DELETE FROM feedback WHERE taskId IN (:taskIds) AND syncState = 'SYNCED'")
    suspend fun deleteSyncedForTasks(taskIds: List<Long>)

    @Query("SELECT DISTINCT taskId FROM feedback WHERE syncState != 'SYNCED'")
    suspend fun taskIdsWithUnsynced(): List<Long>
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_files WHERE fileKey = :key")
    suspend fun get(key: String): MediaEntity?

    @Query("SELECT * FROM media_files WHERE fileKey = :key")
    fun observe(key: String): Flow<MediaEntity?>

    @Upsert
    suspend fun upsert(item: MediaEntity)

    @Query("DELETE FROM media_files")
    suspend fun clear()

    @Query("SELECT * FROM media_files WHERE taskId IN (:taskIds)")
    suspend fun byTasks(taskIds: List<Long>): List<MediaEntity>

    @Query("DELETE FROM media_files WHERE taskId IN (:taskIds)")
    suspend fun deleteByTasks(taskIds: List<Long>)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media_files WHERE path IS NOT NULL")
    fun observeUsedBytes(): Flow<Long>
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_json WHERE cacheKey = :key")
    suspend fun get(key: String): JsonCacheEntity?

    @Query("SELECT * FROM cached_json WHERE cacheKey = :key")
    fun observe(key: String): Flow<JsonCacheEntity?>

    @Upsert
    suspend fun put(item: JsonCacheEntity)

    @Query("DELETE FROM cached_json WHERE cacheKey IN (:keys)")
    suspend fun deleteKeys(keys: List<String>)
}
