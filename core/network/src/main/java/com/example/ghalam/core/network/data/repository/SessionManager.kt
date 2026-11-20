package com.example.ghalam.core.network.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Current token as a flow; null = logged out (collected by the UI).
 * [onSignedOut] runs whenever the token is cleared (manual logout or a 401), so each app can
 * wipe its own cached data and never leave one user's data for the next login.
 */
class SessionManager(
    private val store: TokenStore,
    private val onSignedOut: () -> Unit = {},
) {
    private val _token = MutableStateFlow(store.token)
    val token: StateFlow<String?> = _token.asStateFlow()

    fun setToken(value: String?) {
        store.token = value
        _token.value = value
        if (value == null) onSignedOut()
    }
}
