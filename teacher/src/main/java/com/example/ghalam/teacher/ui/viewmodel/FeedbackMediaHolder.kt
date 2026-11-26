package com.example.ghalam.teacher.ui.viewmodel

import com.example.ghalam.core.ui.voice.VoiceClip
import com.example.ghalam.teacher.data.api.FeedbackMediaDto
import com.example.ghalam.teacher.util.teacherMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The voice / media part of one feedback (a student's or the whole class's). It needs the server:
 * a recording that could not be sent stays here ([pending]) until it is retried or discarded.
 */
class FeedbackMediaHolder(
    private val scope: CoroutineScope,
    private val load: suspend () -> List<FeedbackMediaDto>,
    private val upload: suspend (VoiceClip) -> List<FeedbackMediaDto>,
    private val delete: suspend (Long) -> List<FeedbackMediaDto>,
) {
    private val _media = MutableStateFlow<List<FeedbackMediaDto>>(emptyList())
    val media: StateFlow<List<FeedbackMediaDto>> = _media.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pending = MutableStateFlow<VoiceClip?>(null)
    val pending: StateFlow<VoiceClip?> = _pending.asStateFlow()

    fun refresh() {
        scope.launch {
            try {
                _media.value = load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline: the list stays as it is
                if (_media.value.isEmpty()) _error.value = e.teacherMessage()
            }
        }
    }

    fun add(clip: VoiceClip) {
        _pending.value?.file?.delete()
        _pending.value = clip
        send()
    }

    fun retry() = send()

    private fun send() {
        val clip = _pending.value ?: return
        if (_busy.value) return
        scope.launch {
            _busy.value = true
            _error.value = null
            try {
                _media.value = upload(clip)
                clip.file.delete()
                _pending.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.teacherMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    fun discard() {
        _pending.value?.file?.delete()
        _pending.value = null
        _error.value = null
    }

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
