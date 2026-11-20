package com.example.ghalam.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.ghalam.data.api.GhalamApi
import com.example.ghalam.data.model.Task

class TaskPagingSource(
    private val api: GhalamApi,
    private val filters: Map<String, String>,
) : PagingSource<Int, Task>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Task> {
        val page = params.key ?: 1
        return try {
            val res = api.tasks(page = page, filters = filters)
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
