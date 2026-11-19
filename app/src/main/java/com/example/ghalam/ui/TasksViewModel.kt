package com.example.ghalam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ghalam.data.FilterOption
import com.example.ghalam.data.Graph
import com.example.ghalam.data.LoginBody
import com.example.ghalam.data.Task
import com.example.ghalam.data.TaskPagingSource
import com.example.ghalam.util.toAsciiDigits
import com.example.ghalam.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class LoginState(val loading: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel : ViewModel() {

    private val api get() = Graph.api

    val token: StateFlow<String?> = Graph.token
    val loginState = MutableStateFlow(LoginState())
    val lessons = MutableStateFlow<List<FilterOption>>(emptyList())
    val selectedLessons = MutableStateFlow<Set<Int>>(emptySet())
    val onlyWithDeadline = MutableStateFlow(false)

    val tasks: Flow<PagingData<Task>> =
        combine(Graph.token, selectedLessons, onlyWithDeadline) { t, s, d -> Triple(t, s, d) }
            .filter { !it.first.isNullOrBlank() }
            .flatMapLatest { (_, lessonIds, deadline) ->
                Pager(PagingConfig(pageSize = 6, enablePlaceholders = false)) {
                    TaskPagingSource(api, lessonIds.toList(), deadline)
                }.flow
            }
            .cachedIn(viewModelScope)

    init {
        if (!token.value.isNullOrBlank()) loadLessons()
    }

    /** Login with the national code; the server answers with the user object incl. "token". */
    fun login(code: String) {
        val clean = code.toAsciiDigits()
        if (clean.isBlank() || loginState.value.loading) return
        viewModelScope.launch {
            loginState.value = LoginState(loading = true)
            try {
                val res = api.login(LoginBody(clean))
                val tok = res.tokenOrNull()
                if (tok.isNullOrBlank()) {
                    loginState.value = LoginState(error = "پاسخ ورود توکن نداشت؛ کد ملی را بررسی کنید")
                } else {
                    Graph.setToken(tok)
                    loginState.value = LoginState()
                    loadLessons()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginState.value = LoginState(error = e.userMessage())
            }
        }
    }

    /** Fallback: paste a token captured from the browser. */
    fun saveToken(raw: String) {
        val clean = raw.trim().removePrefix("Bearer").trim()
        if (clean.isBlank()) return
        Graph.setToken(clean)
        loadLessons()
    }

    fun logout() {
        viewModelScope.launch {
            // tell the server first (needs the token), but never block the user on it
            withTimeoutOrNull(5_000) { runCatching { api.logout().close() } }
            Graph.setToken(null)
            lessons.value = emptyList()
            selectedLessons.value = emptySet()
            onlyWithDeadline.value = false
            loginState.value = LoginState()
        }
    }

    fun toggleLesson(id: Int) {
        selectedLessons.value = selectedLessons.value.let { if (id in it) it - id else it + id }
    }

    fun toggleDeadline() {
        onlyWithDeadline.value = !onlyWithDeadline.value
    }

    private fun loadLessons() {
        viewModelScope.launch {
            runCatching { api.filtersMeta() }.onSuccess { res ->
                lessons.value = res.filters.firstOrNull { it.key == "lesson_id" }
                    ?.parsedOptions().orEmpty()
            }
        }
    }

    private fun JsonObject.tokenOrNull(): String? =
        (get("token") as? JsonPrimitive)?.contentOrNull
            ?: ((get("data") as? JsonObject)?.get("token") as? JsonPrimitive)?.contentOrNull
}
