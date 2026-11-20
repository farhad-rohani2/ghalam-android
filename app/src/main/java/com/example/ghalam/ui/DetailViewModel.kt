package com.example.ghalam.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ghalam.data.DetailUi
import com.example.ghalam.data.Graph
import com.example.ghalam.data.PAGE_TASK
import com.example.ghalam.data.PageBody
import com.example.ghalam.data.QuestionUi
import com.example.ghalam.data.RemoteFile
import com.example.ghalam.data.parseMedia
import com.example.ghalam.data.toUi
import com.example.ghalam.util.ImageUtil
import com.example.ghalam.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class DetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val detail: DetailUi? = null,
    /** true while the visible detail comes from the local cache (network failed / still loading). */
    val fromCache: Boolean = false,
    val uploading: Int = 0,
    val deleting: Set<String> = emptySet(),
    val message: String? = null,
)

class DetailViewModel(private val uuid: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    init {
        load()
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
                if (cached != null) _state.update { it.copy(detail = cached.toUi(), fromCache = true) }
            }

            try {
                val fresh = Graph.repo.fetchDetail(uuid)
                _state.update { it.copy(loading = false, detail = fresh.toUi(), fromCache = false) }
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

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Compresses and uploads the photos one after another. */
    fun upload(questionId: Int, uris: List<Uri>) {
        val slot = _state.value.detail?.questions?.firstOrNull { it.id == questionId }?.slot
        if (slot == null) {
            _state.update { it.copy(message = "ارسال پاسخ برای این تکلیف ممکن نیست") }
            return
        }
        viewModelScope.launch {
            var failed = 0
            var succeeded = 0
            for (uri in uris) {
                _state.update { it.copy(uploading = it.uploading + 1) }
                try {
                    val bytes = withContext(Dispatchers.IO) { ImageUtil.compress(Graph.appContext, uri) }
                    val name = "photo_${System.currentTimeMillis()}.jpg"
                    val json = """{"extraData":{"morphedModel":"answer","modelId":${slot.answerId}},"metadata":null}"""
                    val res = Graph.api.upload(
                        file = MultipartBody.Part.createFormData(
                            "file", name, bytes.toRequestBody("image/jpeg".toMediaType()),
                        ),
                        fileName = name.toRequestBody(),
                        fieldUuid = slot.fieldUuid.toRequestBody(),
                        json = json.toRequestBody(),
                    )
                    val file = parseMedia(res["data"]) ?: error("پاسخ ارسال نامعتبر بود")
                    updateFiles(questionId) { it + file }
                    succeeded++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    _state.update { it.copy(message = e.userMessage()) }
                } finally {
                    _state.update { it.copy(uploading = it.uploading - 1) }
                }
            }
            if (failed == 0) _state.update { it.copy(message = "ارسال شد") }
            // keep the offline copy in sync with what is now on the server
            if (succeeded > 0) Graph.repo.refreshDetailQuietly(uuid)
        }
    }

    fun delete(questionId: Int, file: RemoteFile) {
        val id = file.uuid ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = it.deleting + id) }
            try {
                Graph.api.removeMedia(id, PageBody(PAGE_TASK)).close()
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
        fun factory(uuid: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DetailViewModel(uuid) as T
        }
    }
}
