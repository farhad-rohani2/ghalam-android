package com.example.ghalam.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

// ---------------------------------------------------------------- entities

/** One task as shown in the list. Shared by every filter/sort combination. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Int,
    val description: String,
    val deadlineAt: String?,
    val deadlineFinished: Boolean,
    val classroom: String,
    val lesson: String,
    val exam: String?,
    val updatedAt: String,
    val mediaMain: String?,
    val mediaHover: String?,
    val url: String,
    val ratingFill: Int?,
    val ratingEmpty: Int?,
)

/**
 * Which tasks belong to which list query, and in what order.
 * NOTE: deliberately no ForeignKey -> REPLACE/upsert on `tasks` must never cascade-delete entries.
 */
@Entity(
    tableName = "task_entries",
    primaryKeys = ["queryKey", "taskId"],
    indices = [Index("queryKey", "position")],
)
data class TaskEntryEntity(
    val queryKey: String,
    val taskId: Int,
    val position: Int,
)

/** Next page to request for a query (null = last page reached). */
@Entity(tableName = "task_remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val queryKey: String,
    val nextPage: Int?,
)

/** Generic raw-JSON cache: "detail:<uuid>", "filters". */
@Entity(tableName = "cached_json")
data class CachedJsonEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- DAOs

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

// ---------------------------------------------------------------- database

@Database(
    entities = [TaskEntity::class, TaskEntryEntity::class, RemoteKeyEntity::class, CachedJsonEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun cacheDao(): CacheDao
}

// ---------------------------------------------------------------- mappers

fun Task.toEntity() = TaskEntity(
    id = id,
    description = description,
    deadlineAt = deadlineAt,
    deadlineFinished = deadlineFinished,
    classroom = classroom,
    lesson = lesson,
    exam = exam,
    updatedAt = updatedAt,
    mediaMain = media?.main,
    mediaHover = media?.hover,
    url = url,
    ratingFill = rating?.fill,
    ratingEmpty = rating?.empty,
)

fun TaskEntity.toTask() = Task(
    id = id,
    description = description,
    deadlineAt = deadlineAt,
    deadlineFinished = deadlineFinished,
    classroom = classroom,
    lesson = lesson,
    exam = exam,
    updatedAt = updatedAt,
    media = if (mediaMain != null || mediaHover != null) Media(mediaMain, mediaHover) else null,
    url = url,
    rating = if (ratingFill != null && ratingEmpty != null) Rating(ratingFill, ratingEmpty) else null,
)
