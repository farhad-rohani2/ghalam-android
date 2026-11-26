package com.example.ghalam.teacher.ui.viewmodel

import com.example.ghalam.core.ui.voice.VoiceClip
import com.example.ghalam.core.ui.voice.VoiceConfig
import com.example.ghalam.teacher.data.Graph
import com.example.ghalam.teacher.data.api.FeedbackMediaDto
import com.example.ghalam.teacher.data.local.QueuedMediaEntity
import com.example.ghalam.teacher.util.teacherMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The media part of one feedback (a student's, or the whole class's when studentId is
 * GENERAL_FEEDBACK). New recordings go into the offline queue ([queue]) and are uploaded by the sync;
 * the list of files already on the server ([media]) needs the internet.
 */
class FeedbackMediaHolder(
    private val scope: CoroutineScope,
    private val taskId: Long,
    private val studentId: Long,
    private val load: suspend () -> List<FeedbackMediaDto>,
    private val delete: suspend (Long) -> List<FeedbackMediaDto>,
) {
    private val _media = MutableStateFlow<List<FeedbackMediaDto>>(emptyList())
    val media: StateFlow<List<FeedbackMediaDto>> = _media.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Recordings that have not reached the server yet (waiting, or refused). */
    val queue: StateFlow<List<QueuedMediaEntity>> = Graph.mediaQueue.observe(taskId, studentId)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // an item that leaves the queue was uploaded (or discarded): look at the server list again
        scope.launch {
            var last = -1
            queue.collect { list ->
                if (last >= 0 && list.size < last) refresh()
                last = list.size
            }
        }
    }

    fun refresh() {
        scope.launch {
            try {
                _media.value = load()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline: the list stays as it is
            }
        }
    }

    /** Saved on the phone at once; uploaded as soon as there is internet. */
    fun add(clip: VoiceClip) {
        scope.launch { Graph.mediaQueue.enqueue(taskId, studentId, clip.file, clip.durationMs, VoiceConfig.MIME) }
    }

    fun retry(id: String) {
        scope.launch { Graph.mediaQueue.retry(id) }
    }

    fun discard(id: String) {
        scope.launch { Graph.mediaQueue.discard(id) }
    }

    /** Needs the server. */
    fun remove(id: Long) {
        if (_busy.value) return
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                _media.value = delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.teacherMessage()
            } finally {
                _busy.value = false
            }
        }
    }
}
