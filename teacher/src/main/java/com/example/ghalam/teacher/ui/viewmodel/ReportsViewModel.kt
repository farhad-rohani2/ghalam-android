package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.local.ClassroomEntity
import com.example.ghalam.teacher.data.local.TaskRow
import com.example.ghalam.teacher.data.repository.DEFAULT_REPORT_MODES
import com.example.ghalam.teacher.util.toServerError
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReportSource { TODAY, TOMORROW, SELECTED }

/**
 * Reports (the text the teacher sends to the class group). The server composes the text, so this
 * needs internet; [presetTaskId] != 0 = the report of that one task.
 */
class ReportsViewModel(val presetTaskId: Long) : ViewModel() {
    private val repo = Graph.repo

    val modes: StateFlow<Map<String, String>> = repo.reportModes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_REPORT_MODES)
    val mode = MutableStateFlow("full")
    val source = MutableStateFlow(ReportSource.TODAY)
    val classroomFilter = MutableStateFlow<Long?>(null)
    val selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val classrooms: StateFlow<List<ClassroomEntity>> = repo.classrooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tasks to pick from when the source is "selected tasks". */
    val tasks: StateFlow<List<TaskRow>> = combine(repo.tasks(), classroomFilter) { rows, classroom ->
        rows.filter { classroom == null || it.task.classroomId == classroom }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val text = MutableStateFlow<String?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun toggleTask(id: Long) {
        selectedIds.update { if (id in it) it - id else if (it.size < MAX_TASKS) it + id else it }
    }

    fun generate() {
        if (loading.value) return
        if (!Graph.network.online.value) {
            error.value = "ساخت گزارش به اینترنت نیاز دارد."
            return
        }
        val m = mode.value
        if (presetTaskId == 0L && source.value == ReportSource.SELECTED && selectedIds.value.isEmpty()) {
            error.value = "حداقل یک تکلیف را انتخاب کنید."
            return
        }
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val online = Graph.online
                text.value = when {
                    presetTaskId != 0L -> online.reportForTask(presetTaskId, m)
                    source.value == ReportSource.TODAY -> online.reportToday(m, null, classroomFilter.value)
                    source.value == ReportSource.TOMORROW ->
                        online.reportToday(m, LocalDate.now().plusDays(1).toString(), classroomFilter.value)
                    else -> online.reportSelected(selectedIds.value.toList(), m)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.toServerError().message
            } finally {
                loading.value = false
            }
        }
    }

    private companion object {
        const val MAX_TASKS = 30
    }
}

class ReportsViewModelFactory(private val presetTaskId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportsViewModel(presetTaskId) as T
}
