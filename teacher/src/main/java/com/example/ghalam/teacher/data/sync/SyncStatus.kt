package com.example.ghalam.teacher.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class SyncUiState(
    val running: Boolean = false,
    val message: String? = null,
    val lastSuccessAt: Long = 0L,
    val lastError: String? = null,
)

/** What the home screen shows about the background sync. */
object SyncStatus {
    val state = MutableStateFlow(SyncUiState())

    fun update(block: (SyncUiState) -> SyncUiState) = state.update(block)

    fun reset() {
        state.value = SyncUiState()
    }
}
