package com.example.ghalam.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.core.network.util.userMessage
import com.example.ghalam.data.Graph
import com.example.ghalam.data.api.PAGE_TASK
import com.example.ghalam.data.api.PageBody
import com.example.ghalam.data.local.LocalMediaEntity
import com.example.ghalam.data.local.PendingAnswerEntity
import com.example.ghalam.data.local.PendingUploadEntity
import com.example.ghalam.data.local.UploadState
import com.example.ghalam.data.model.DetailUi
import com.example.ghalam.data.model.QuestionUi
import com.example.ghalam.data.model.RemoteFile
import com.example.ghalam.data.model.toUi
import com.example.ghalam.data.outbox.EnqueueResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: DetailUi? = null,
    /** true while the visible detail comes from the local cache (network failed / still loading). */
    val fromCache: Boolean = false,
    val deleting: Set<String> = emptySet(),
    val message: String? = null,
    /** What every answer box shows (question id -> text). */
    val texts: Map<Int, String> = emptyMap(),
    /** Text answers saved on the phone (question id -> row); [PendingAnswerEntity.dirty] = not sent yet. */
    val drafts: Map<Int, PendingAnswerEntity> = emptyMap(),
    /** Photos of this task that are still on the phone (waiting, sending, held or failed). */
    val uploads: List<PendingUploadEntity> = emptyList(),
    /** Local copies of photos that were uploaded from this phone. */
    val local: List<LocalMediaEntity> = emptyList(),
) {
    /** Photos that are waiting or being sent right now. */
    val uploading: Int
        get() = uploads.count { it.state == UploadState.PENDING || it.state == UploadState.UPLOADING }
}

class DetailViewModel(private val uuid: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    /** Questions the student typed in: the text from the server must not replace what is on screen. */
    private val touched = mutableSetOf<Int>()

    /** Typed but not yet written to the outbox (the student is still typing). */
    private val unsaved = mutableMapOf<Int, String>()
    private val textJobs = mutableMapOf<Int, Job>()

    init {
        observeOutbox()
        load()
    }

    private fun observeOutbox() {
        viewModelScope.launch {
            var previous = -1
            Graph.outbox.uploadsFor(uuid).collect { list ->
                _state.update { it.copy(uploads = list) }
                // a photo left the queue (it was sent, or removed): bring the server list up to date
                if (previous >= 0 && list.size < previous) refreshQuietly()
                previous = list.size
            }
        }
        viewModelScope.launch {
            Graph.outbox.localMediaFor(uuid).collect { list -> _state.update { it.copy(local = list) } }
        }
        viewModelScope.launch {
            Graph.outbox.answersFor(uuid).collect { rows ->
                _state.update { s ->
                    val texts = s.texts.toMutableMap()
                    rows.forEach { if (it.questionId !in touched) texts[it.questionId] = it.text }
                    s.copy(texts = texts, drafts = rows.associateBy { it.questionId })
                }
            }
        }
    }

    /** Cache first (instant, works offline), then the network replaces it. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }

            if (_state.value.detail == null) {
                val cached = try {
                    Graph.repo.cachedDetail(uuid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (cached != null) {
                    val ui = cached.toUi()
                    _state.update { it.copy(detail = ui, fromCache = true) }
                    applyServerTexts(ui)
                }
            }

            try {
                val fresh = Graph.repo.fetchDetail(uuid).toUi()
                // texts the server already has are not needed in the outbox any more
                Graph.outbox.dropCleanAnswers(uuid)
                if (fresh.questions.any { it.slot != null }) {
                    val serverFiles = fresh.questions.flatMap { q -> q.files.mapNotNull { it.uuid } }
                    Graph.outbox.reconcileLocal(uuid, serverFiles.toSet())
                }
                _state.update { it.copy(loading = false, detail = fresh, fromCache = false) }
                applyServerTexts(fresh)
                // save every file of this task now, so it also opens without internet later
                Graph.media.prefetch(fresh.questions.flatMap { it.teacherFiles + it.files })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    if (it.detail != null) {
                        it.copy(loading = false, message = "اتصال برقرار نشد؛ نسخه‌ی ذخیره‌شده نمایش داده می‌شود")
                    } else {
                        it.copy(loading = false, error = e.userMessage())
                    }
                }
            }
        }
    }

    /** Silent refresh after a background upload: no spinner, no error message. */
    private fun refreshQuietly() {
        viewModelScope.launch {
            try {
                val fresh = Graph.repo.fetchDetail(uuid).toUi()
                _state.update { it.copy(detail = fresh, fromCache = false) }
                applyServerTexts(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // offline or server trouble: the next normal load will fix it
            }
        }
    }

    /** Text the server has, for every question the student did not type in and has no unsent text for. */
    private fun applyServerTexts(detail: DetailUi) {
        _state.update { s ->
            val texts = s.texts.toMutableMap()
            detail.questions.forEach { q ->
                if (q.id in touched) return@forEach
                if (s.drafts[q.id]?.dirty == true) return@forEach
                texts[q.id] = q.answerText.orEmpty()
            }
            s.copy(texts = texts)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ---------------------------------------------------------------- photos

    /** Saves the photos on the phone and queues them; they are sent when the internet allows. */
    fun upload(questionId: Int, uris: List<Uri>) {
        val slot = _state.value.detail?.questions?.firstOrNull { it.id == questionId }?.slot
        if (slot == null) {
            _state.update { it.copy(message = "ارسال پاسخ برای این تکلیف ممکن نیست") }
            return
        }
        viewModelScope.launch {
            var queued = 0
            var held = 0
            var tooBig = 0
            var error: String? = null
            for (uri in uris) {
                when (val r = Graph.outbox.enqueuePhoto(uuid, questionId, slot, uri)) {
                    EnqueueResult.Queued -> queued++
                    EnqueueResult.Held -> held++
                    EnqueueResult.TooLarge -> tooBig++
                    is EnqueueResult.Error -> error = r.message
                }
            }
            val msg = error ?: when {
                tooBig > 0 -> "حجم فایل بیش از ۶۰ مگابایت است و ارسال نمی‌شود"
                held > 0 -> "عکس روی گوشی ذخیره شد؛ برای ارسال دکمهٔ «ارسال» را بزنید"
                queued > 0 && !Graph.network.online.value -> "اینترنت ندارید؛ عکس ذخیره شد و با اتصال خودکار ارسال می‌شود"
                else -> null
            }
            if (msg != null) _state.update { it.copy(message = msg) }
        }
    }

    /** The student removes a photo that was not sent yet. */
    fun discard(item: PendingUploadEntity) {
        viewModelScope.launch { Graph.outbox.discardUpload(item.id) }
    }

    /** "Send" (held photo) or "try again" (failed photo). */
    fun retryUpload(item: PendingUploadEntity) {
        viewModelScope.launch { Graph.outbox.retryUpload(item.id) }
    }

    /** Deletes a photo that is already on the server. */
    fun delete(questionId: Int, file: RemoteFile) {
        val id = file.uuid ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = it.deleting + id) }
            try {
                Graph.api.removeMedia(id, PageBody(PAGE_TASK)).close()
                Graph.media.forget(file)
                Graph.outbox.forgetLocalMedia(id)
                updateFiles(questionId) { list -> list.filterNot { it.uuid == id } }
                _state.update { it.copy(message = "حذف شد") }
                Graph.repo.refreshDetailQuietly(uuid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.userMessage()) }
            } finally {
                _state.update { it.copy(deleting = it.deleting - id) }
            }
        }
    }

    // ---------------------------------------------------------------- text answers

    /** Called on every key press; the text is written to the outbox when the student pauses. */
    fun onTextChange(questionId: Int, text: String) {
        touched.add(questionId)
        _state.update { it.copy(texts = it.texts + (questionId to text)) }
        unsaved[questionId] = text
        textJobs[questionId]?.cancel()
        textJobs[questionId] = viewModelScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            val latest = unsaved.remove(questionId) ?: return@launch
            Graph.outbox.saveAnswerText(uuid, questionId, latest)
        }
    }

    /** Writes everything typed so far (screen closed, app going away). */
    fun flush() {
        if (unsaved.isEmpty()) return
        val pending = unsaved.toMap()
        unsaved.clear()
        textJobs.values.forEach { it.cancel() }
        textJobs.clear()
        Graph.ioScope.launch {
            for ((questionId, text) in pending) Graph.outbox.saveAnswerText(uuid, questionId, text)
        }
    }

    fun retryText(questionId: Int) {
        viewModelScope.launch { Graph.outbox.retryAnswer(uuid, questionId) }
    }

    override fun onCleared() {
        flush()
    }

    private fun updateFiles(questionId: Int, transform: (List<RemoteFile>) -> List<RemoteFile>) {
        _state.update { s ->
            val d = s.detail ?: return@update s
            s.copy(
                detail = d.copy(
                    questions = d.questions.map { q: QuestionUi ->
                        if (q.id == questionId) q.copy(files = transform(q.files)) else q
                    },
                ),
            )
        }
    }

    companion object {
        private const val TEXT_DEBOUNCE_MS = 700L

        fun factory(uuid: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(uuid) as T
        }
    }
}
