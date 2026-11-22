package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.TaskDetailDto
import com.example.ghalam.teacher.data.local.SubmissionRow
import com.example.ghalam.teacher.data.local.TaskEntity
import com.example.ghalam.teacher.util.normalizeFa
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TaskViewModel(val taskId: Long) : ViewModel() {
    private val repo = Graph.repo
    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("fa"))

    val task: StateFlow<TaskEntity?> = repo.task(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val detail: StateFlow<TaskDetailDto?> = repo.taskDetail(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null = all, or done / no_file / none. */
    val statusFilter = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    /** null = Room has not answered yet. */
    val all: StateFlow<List<SubmissionRow>?> = repo.submissions(taskId)
        .map<List<SubmissionRow>, List<SubmissionRow>?> { rows ->
            rows.sortedWith { a, b -> collator.compare(a.s.name, b.s.name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val visible: StateFlow<List<SubmissionRow>> = combine(all, statusFilter, query) { rows, status, q ->
        val needle = q.normalizeFa()
        rows.orEmpty().filter { row ->
            (status == null || row.s.status == status) &&
                (needle.isEmpty() || row.s.name.normalizeFa().contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<Map<String, Int>> = all
        .map { rows -> rows.orEmpty().groupingBy { it.s.status }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

class TaskViewModelFactory(private val taskId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskViewModel(taskId) as T
}
