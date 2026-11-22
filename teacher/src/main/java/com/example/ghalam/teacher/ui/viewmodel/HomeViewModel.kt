package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.local.ClassroomEntity
import com.example.ghalam.teacher.data.local.TaskRow
import com.example.ghalam.teacher.data.sync.SyncStatus
import com.example.ghalam.teacher.data.sync.SyncUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimeFilter { ALL, ACTIVE, PAST }

class HomeViewModel : ViewModel() {
    private val repo = Graph.repo

    val classroomFilter = MutableStateFlow<Long?>(null)
    val timeFilter = MutableStateFlow(TimeFilter.ALL)

    /** null = Room has not answered yet. */
    val allTasks: StateFlow<List<TaskRow>?> = repo.tasks()
        .map<List<TaskRow>, List<TaskRow>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val visibleTasks: StateFlow<List<TaskRow>?> = combine(allTasks, classroomFilter, timeFilter) { tasks, classroom, time ->
        val now = System.currentTimeMillis()
        tasks?.filter { row ->
            val t = row.task
            val past = t.deadlineEpoch != null && t.deadlineEpoch < now
            (classroom == null || t.classroomId == classroom) &&
                when (time) {
                    TimeFilter.ALL -> true
                    TimeFilter.ACTIVE -> !past
                    TimeFilter.PAST -> past
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val classrooms: StateFlow<List<ClassroomEntity>> = repo.classrooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unsynced: StateFlow<Int> = repo.unsyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sync: StateFlow<SyncUiState> = SyncStatus.state
    val online: StateFlow<Boolean> = Graph.network.online

    init {
        Graph.scheduler.ensurePeriodic()
        Graph.scheduler.syncNow(full = false)

        // the connection came back: sync again
        viewModelScope.launch {
            var wasOnline = Graph.network.online.value
            Graph.network.online.collect { nowOnline ->
                if (nowOnline && !wasOnline) Graph.scheduler.syncNow(full = false)
                wasOnline = nowOnline
            }
        }
    }

    /** The refresh button. */
    fun refresh() = Graph.scheduler.syncNow(full = true)
}
