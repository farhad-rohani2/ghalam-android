package com.example.ghalam.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.ghalam.core.network.data.model.Credentials
import com.example.ghalam.core.network.data.repository.MissingTokenException
import com.example.ghalam.core.network.util.toAsciiDigits
import com.example.ghalam.core.network.util.userMessage
import com.example.ghalam.data.Graph
import com.example.ghalam.data.model.Filter
import com.example.ghalam.data.model.FilterKind
import com.example.ghalam.data.model.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class LoginState(val loading: Boolean = false, val error: String? = null)

/** One selected option, shown as a removable chip under the search bar. */
data class ActiveFilter(val key: String, val id: String, val label: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TasksViewModel : ViewModel() {

    val token: StateFlow<String?> = Graph.token
    val loginState = MutableStateFlow(LoginState())

    /** Filter definitions from filters-meta (only the types the app can render). */
    val filters = MutableStateFlow<List<Filter>>(emptyList())

    /** filter key -> selected option ids (single-select filters hold at most one). */
    val selected = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** Free-text search -> filter[title]. */
    val query = MutableStateFlow("")

    val active: StateFlow<List<ActiveFilter>> =
        combine(filters, selected) { fs, sel ->
            fs.flatMap { f ->
                val labels = f.parsedOptions().associate { it.id to it.label }
                sel[f.key].orEmpty().map { id -> ActiveFilter(f.key, id, labels[id] ?: id) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** List identity: token + query params. The token makes every new login start a fresh pager. */
    private data class ListKey(val token: String, val params: Map<String, String>)

    /** Offline-first: Room is the source, the network only refreshes it (see TaskRemoteMediator). */
    val tasks: Flow<PagingData<Task>> =
        combine(
            Graph.token,
            filters,
            selected,
            query.debounce { if (it.isEmpty()) 0L else 500L },
        ) { t, fs, sel, q -> if (t.isNullOrBlank()) null else ListKey(t, buildParams(fs, sel, q)) }
            .filter { it != null }
            .map { it!! }
            .distinctUntilChanged()
            .flatMapLatest { key -> Graph.repo.tasks(key.params) }
            .cachedIn(viewModelScope)

    init {
        if (!token.value.isNullOrBlank()) loadFilters()
    }

    // ---------- filters ----------

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggle(filter: Filter, id: String) {
        selected.update { m ->
            val cur = m[filter.key].orEmpty()
            val next = when (filter.kind) {
                FilterKind.SINGLE -> if (id in cur) emptySet() else setOf(id)
                else -> if (id in cur) cur - id else cur + id
            }
            if (next.isEmpty()) m - filter.key else m + (filter.key to next)
        }
    }

    fun remove(key: String, id: String) {
        selected.update { m ->
            val next = m[key].orEmpty() - id
            if (next.isEmpty()) m - key else m + (key to next)
        }
    }

    fun clearFilters() {
        selected.value = emptyMap()
        query.value = ""
    }

    private fun buildParams(fs: List<Filter>, sel: Map<String, Set<String>>, q: String): Map<String, String> =
        buildMap {
            fs.forEach { f ->
                val values = sel[f.key].orEmpty()
                if (values.isEmpty()) return@forEach
                when (f.kind) {
                    FilterKind.SINGLE -> put("filter[${f.key}]", values.first())
                    FilterKind.MULTI -> values.forEachIndexed { i, v -> put("filter[${f.key}][$i]", v) }
                    FilterKind.UNSUPPORTED -> Unit
                }
            }
            if (q.isNotBlank()) put("filter[title]", q.trim())
        }

    private fun loadFilters() {
        viewModelScope.launch {
            runCatching { Graph.repo.filtersMeta() }.onSuccess { res ->
                filters.value = res.filters.filter { it.kind != FilterKind.UNSUPPORTED }
            }
        }
    }

    // ---------- auth ----------

    /** Login with the national code; on success the token is stored by [Graph.auth]. */
    fun login(code: String) {
        val clean = code.toAsciiDigits()
        if (clean.isBlank() || loginState.value.loading) return
        viewModelScope.launch {
            loginState.value = LoginState(loading = true)
            try {
                Graph.auth.login(Credentials.NationalCode(clean))
                loginState.value = LoginState()
                loadFilters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: MissingTokenException) {
                loginState.value = LoginState(error = "پاسخ ورود توکن نداشت؛ کد ملی را بررسی کنید")
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
        loadFilters()
    }

    fun logout() {
        viewModelScope.launch {
            Graph.auth.logout()
            filters.value = emptyList()
            selected.value = emptyMap()
            query.value = ""
            loginState.value = LoginState()
        }
    }
}
