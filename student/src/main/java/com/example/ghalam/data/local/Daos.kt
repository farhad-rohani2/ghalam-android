package com.example.ghalam.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT t.* FROM tasks t
        INNER JOIN task_entries e ON e.taskId = t.id
        WHERE e.queryKey = :queryKey
        ORDER BY e.position ASC
        """
    )
    fun pagingSource(queryKey: String): PagingSource<Int, TaskEntity>

    @Upsert
    suspend fun upsertTasks(items: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(items: List<TaskEntryEntity>)

    @Query("DELETE FROM task_entries WHERE queryKey = :queryKey")
    suspend fun clearEntries(queryKey: String)

    @Query("DELETE FROM tasks WHERE id NOT IN (SELECT taskId FROM task_entries)")
    suspend fun deleteOrphans()

    @Query("SELECT * FROM task_remote_keys WHERE queryKey = :queryKey")
    suspend fun remoteKey(queryKey: String): RemoteKeyEntity?

    @Upsert
    suspend fun upsertRemoteKey(key: RemoteKeyEntity)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_json WHERE cacheKey = :key")
    suspend fun get(key: String): CachedJsonEntity?

    @Upsert
    suspend fun put(item: CachedJsonEntity)
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_files WHERE fileKey = :key")
    suspend fun get(key: String): MediaEntity?

    @Upsert
    suspend fun upsert(item: MediaEntity)

    @Query("DELETE FROM media_files WHERE fileKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM media_files")
    suspend fun clear()

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media_files WHERE path IS NOT NULL")
    fun observeUsedBytes(): Flow<Long>
}
