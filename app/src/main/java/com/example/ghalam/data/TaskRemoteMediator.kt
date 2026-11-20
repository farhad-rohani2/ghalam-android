package com.example.ghalam.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException

/**
 * Network -> Room. The UI only ever reads from Room (single source of truth), so the list
 * keeps working offline. A failed REFRESH never touches the cache: old rows are replaced
 * only after the new first page arrived successfully.
 */
@OptIn(ExperimentalPagingApi::class)
class TaskRemoteMediator(
    private val api: GhalamApi,
    private val db: AppDatabase,
    private val filters: Map<String, String>,
    private val sort: String,
    private val queryKey: String,
) : RemoteMediator<Int, TaskEntity>() {

    private val dao get() = db.taskDao()

    // Always try to refresh on start; if offline it fails and the cached list stays.
    override suspend fun initialize(): InitializeAction = InitializeAction.LAUNCH_INITIAL_REFRESH

    override suspend fun load(loadType: LoadType, state: PagingState<Int, TaskEntity>): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> 1
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val key = dao.remoteKey(queryKey)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                key.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }
        return try {
            val res = api.tasks(page = page, sort = sort, filters = filters)
            val base = (res.meta.currentPage - 1) * res.meta.perPage
            val next = if (res.meta.currentPage < res.meta.lastPage) res.meta.currentPage + 1 else null

            db.withTransaction {
                if (loadType == LoadType.REFRESH) dao.clearEntries(queryKey)
                dao.upsertTasks(res.data.map { it.toEntity() })
                dao.insertEntries(res.data.mapIndexed { i, t -> TaskEntryEntity(queryKey, t.id, base + i) })
                dao.upsertRemoteKey(RemoteKeyEntity(queryKey, next))
                dao.deleteOrphans()
            }
            MediatorResult.Success(endOfPaginationReached = next == null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }
}
