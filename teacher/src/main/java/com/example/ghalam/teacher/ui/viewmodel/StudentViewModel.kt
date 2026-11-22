package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.core.network.util.userMessage
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.AnswerDto
import com.example.ghalam.teacher.data.api.AnswerFileDto
import com.example.ghalam.teacher.data.local.SubmissionEntity
import com.example.ghalam.teacher.data.local.TaskEntity
import com.example.ghalam.teacher.data.repository.FeedbackForm
import com.example.ghalam.teacher.data.repository.FeedbackScreenState
import com.example.ghalam.teacher.data.sync.absoluteUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One student's answer and the feedback form (an editable copy: [draft]) of one task. */
class StudentViewModel(val taskId: Long, val studentId: Long) : ViewModel() {
    private val repo = Graph.repo

    val task: StateFlow<TaskEntity?> = repo.task(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val submission: StateFlow<SubmissionEntity?> = repo.submission(taskId, studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val answer: StateFlow<AnswerDto?> = repo.answer(taskId, studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _screen = MutableStateFlow<FeedbackScreenState?>(null)

    /** The saved form and its sync state. */
    val screen: StateFlow<FeedbackScreenState?> = _screen.asStateFlow()

    private val _draft = MutableStateFlow<FeedbackForm?>(null)

    /** What is on screen right now (saved form + the teacher's unsaved edits). */
    val draft: StateFlow<FeedbackForm?> = _draft.asStateFlow()

    val dirty: StateFlow<Boolean> = combine(_draft, _screen) { d, s -> d != null && s != null && d != s.form }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _neighbors = MutableStateFlow<Pair<Long?, Long?>>(null to null)

    /** (previous student id, next student id) in name order. */
    val neighbors: StateFlow<Pair<Long?, Long?>> = _neighbors.asStateFlow()

    private val _fileErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** url -> why the last manual download failed. */
    val fileErrors: StateFlow<Map<String, String>> = _fileErrors.asStateFlow()

    init {
        viewModelScope.launch {
            repo.feedbackScreen(taskId, studentId).collect { s ->
                val previouslySaved = _screen.value?.form
                // follow changes from the sync as long as the teacher has not edited anything
                if (_draft.value == null || _draft.value == previouslySaved) _draft.value = s.form
                _screen.value = s
            }
        }
        viewModelScope.launch {
            val ids = repo.sortedStudentIds(taskId)
            val i = ids.indexOf(studentId)
            if (i >= 0) _neighbors.value = ids.getOrNull(i - 1) to ids.getOrNull(i + 1)
        }
        viewModelScope.launch { downloadSmallFiles() }
    }

    fun edit(transform: (FeedbackForm) -> FeedbackForm) {
        _draft.update { d -> d?.let(transform) }
    }

    /** Called when leaving the screen: edits are never lost. */
    suspend fun saveIfDirty() {
        val d = _draft.value ?: return
        val s = _screen.value ?: return
        if (d != s.form) withContext(NonCancellable) { repo.saveFeedback(taskId, studentId, d) }
    }

    fun retry() {
        viewModelScope.launch { repo.retryFeedback(taskId, studentId) }
    }

    fun keepMine() {
        viewModelScope.launch { repo.keepMine(taskId, studentId) }
    }

    fun takeServer() {
        viewModelScope.launch {
            _draft.value = null // the draft follows the server copy
            repo.takeServer(taskId, studentId)
        }
    }

    /** The "download" button of one file (ignores the size limit). */
    fun downloadFile(file: AnswerFileDto) {
        val url = absoluteUrl(file.url)
        viewModelScope.launch {
            try {
                _fileErrors.update { it - url }
                Graph.media.ensure(url, file.name, taskId, Long.MAX_VALUE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _fileErrors.update { it + (url to e.userMessage()) }
            }
        }
    }

    /** Opening the answer while online downloads the files below the size limit (when not already done by the sync). */
    private suspend fun downloadSmallFiles() {
        val a = answer.filterNotNull().first()
        val limit = Graph.prefs.mediaLimitBytes
        for (f in a.answers.flatMap { it.files }) {
            if (f.url.isBlank() || (f.size > 0 && f.size > limit)) continue
            try {
                Graph.media.ensure(absoluteUrl(f.url), f.name, taskId, limit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline: the file shows as "not downloaded" with a download button
            }
        }
    }
}

class StudentViewModelFactory(private val taskId: Long, private val studentId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = StudentViewModel(taskId, studentId) as T
}
