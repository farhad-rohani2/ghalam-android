package com.example.ghalam.teacher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.MessageTaskDto
import com.example.ghalam.teacher.data.api.MessageTemplateDto
import com.example.ghalam.teacher.data.local.SubmissionEntity
import com.example.ghalam.teacher.util.toServerError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A message to one student (reminder, thanks, feedback ...). The server fills the {tokens} from the
 * student's real data, so this needs internet. The templates can be edited and saved per teacher.
 */
class MessageViewModel(val taskId: Long, val studentId: Long) : ViewModel() {
    private val online = Graph.online

    val submission: StateFlow<SubmissionEntity?> = Graph.repo.submission(taskId, studentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val templates = MutableStateFlow<List<MessageTemplateDto>>(emptyList())
    val tokens = MutableStateFlow<Map<String, String>>(emptyMap())
    val tasks = MutableStateFlow<List<MessageTaskDto>>(emptyList())
    val selectedKey = MutableStateFlow<String?>(null)
    val body = MutableStateFlow("")
    val selectedTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val result = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    val current: StateFlow<MessageTemplateDto?> = combine(templates, selectedKey) { list, key ->
        list.firstOrNull { it.key == key }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                val ctx = online.messageContext(taskId, studentId)
                templates.value = ctx.templates
                tokens.value = ctx.tokens
                tasks.value = ctx.tasks
                selectedTaskIds.value = ctx.pendingTaskIds.toSet()
                if (selectedKey.value == null || ctx.templates.none { it.key == selectedKey.value }) {
                    ctx.templates.firstOrNull()?.let { select(it.key) }
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

    fun select(key: String) {
        selectedKey.value = key
        body.value = templates.value.firstOrNull { it.key == key }?.body.orEmpty()
        result.value = null
    }

    fun setBody(text: String) {
        body.value = text.take(MAX_BODY)
    }

    /** Adds a {token} at the end of the text. */
    fun insertToken(token: String) {
        setBody(body.value + token)
    }

    fun toggleTask(id: Long) {
        selectedTaskIds.update { if (id in it) it - id else if (it.size < MAX_TASKS) it + id else it }
    }

    /** The body differs from the saved template. */
    fun modified(): Boolean = current.value?.let { it.body != body.value } ?: false

    fun compose() {
        val template = current.value ?: return
        launchOnline {
            val usesTasks = body.value.contains("{تکالیف}")
            result.value = online.composeMessage(
                taskId = taskId,
                studentId = studentId,
                template = template.key,
                body = body.value.takeIf { it != template.body },
                taskIds = if (usesTasks) selectedTaskIds.value.toList() else emptyList(),
            )
        }
    }

    fun saveTemplate() {
        val template = current.value ?: return
        launchOnline {
            val data = online.saveTemplate(template.key, body.value)
            templates.value = data.templates
            select(template.key)
        }
    }

    /** Custom template: delete it. Edited default template: back to the default text. */
    fun resetOrDelete() {
        val template = current.value ?: return
        launchOnline {
            val list = online.deleteTemplate(template.key)
            templates.value = list
            if (list.any { it.key == template.key }) select(template.key) else list.firstOrNull()?.let { select(it.key) }
        }
    }

    fun createTemplate(label: String) {
        launchOnline {
            val data = online.createTemplate(label.trim(), body.value)
            templates.value = data.templates
            data.key?.let { select(it) }
        }
    }

    private fun launchOnline(block: suspend () -> Unit) {
        if (busy.value) return
        if (!Graph.network.online.value) {
            error.value = "این بخش به اینترنت نیاز دارد."
            return
        }
        viewModelScope.launch {
            busy.value = true
            error.value = null
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.toServerError().message
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val MAX_BODY = 3000
        const val MAX_TASKS = 15
    }
}

class MessageViewModelFactory(private val taskId: Long, private val studentId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MessageViewModel(taskId, studentId) as T
}
