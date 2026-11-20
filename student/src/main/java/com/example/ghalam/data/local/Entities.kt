package com.example.ghalam.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
