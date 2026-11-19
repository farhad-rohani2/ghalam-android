package com.example.ghalam.data

import androidx.paging.PagingSource
import androidx.paging.PagingState

class TaskPagingSource(
    private val api: GhalamApi,
    private val lessonIds: List<Int>,
    private val onlyWithDeadline: Boolean,
) : PagingSource<Int, Task>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Task> {
        val page = params.key ?: 1
        return try {
            val res = api.tasks(
                page = page,
                lessonIds = lessonIds.ifEmpty { null },
                deadlineUnfinished = if (onlyWithDeadline) "ok" else null,
            )
            LoadResult.Page(
                data = res.data,
                prevKey = if (page > 1) page - 1 else null,
                nextKey = if (page < res.meta.lastPage) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Task>): Int? =
        state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }
}
