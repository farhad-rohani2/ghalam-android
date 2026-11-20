package com.example.ghalam.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.example.ghalam.data.api.GhalamApi
import com.example.ghalam.data.local.AppDatabase
import com.example.ghalam.data.local.CachedJsonEntity
import com.example.ghalam.data.local.toTask
import com.example.ghalam.data.model.FiltersResponse
import com.example.ghalam.data.model.Task
import com.example.ghalam.data.model.TaskDetail
import com.example.ghalam.data.paging.TaskRemoteMediator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Offline-first access to tasks. UI code should talk to this instead of [GhalamApi] for reads. */
class TaskRepository(
    private val api: GhalamApi,
    private val db: AppDatabase,
    private val json: Json,
) {

    // ---------------- list ----------------

    /** Same element type as before ([Task]); remember `.cachedIn(viewModelScope)` in the ViewModel. */
    @OptIn(ExperimentalPagingApi::class)
    fun tasks(filters: Map<String, String>, sort: String = "-id"): Flow<PagingData<Task>> {
        val key = queryKey(filters, sort)
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            remoteMediator = TaskRemoteMediator(api, db, filters, sort, key),
            pagingSourceFactory = { db.taskDao().pagingSource(key) },
        ).flow.map { page -> page.map { it.toTask() } }
    }

    private fun queryKey(filters: Map<String, String>, sort: String): String =
        sort + "|" + filters.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

    // ---------------- detail ----------------

    suspend fun cachedDetail(uuid: String): TaskDetail? {
        val row = db.cacheDao().get("detail:$uuid") ?: return null
        return runCatching { json.decodeFromString(TaskDetail.serializer(), row.json) }.getOrNull()
    }

    /** Network first; the raw payload is stored so it can be re-mapped with [toUi] later. */
    suspend fun fetchDetail(uuid: String): TaskDetail {
        val detail = api.taskDetail(uuid).data
        db.cacheDao().put(
            CachedJsonEntity(
                cacheKey = "detail:$uuid",
                json = json.encodeToString(TaskDetail.serializer(), detail),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return detail
    }

    /** Used after upload/delete so the cache matches the server; errors are ignored. */
    suspend fun refreshDetailQuietly(uuid: String) {
        try {
            fetchDetail(uuid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // ---------------- filters ----------------

    /** Network first, falls back to the last saved copy when the request fails. */
    suspend fun filtersMeta(): FiltersResponse {
        try {
            val res = api.filtersMeta()
            db.cacheDao().put(
                CachedJsonEntity(
                    cacheKey = "filters",
                    json = json.encodeToString(FiltersResponse.serializer(), res),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return res
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cached = db.cacheDao().get("filters")
                ?.let { row -> runCatching { json.decodeFromString(FiltersResponse.serializer(), row.json) }.getOrNull() }
            return cached ?: throw e
        }
    }
}
