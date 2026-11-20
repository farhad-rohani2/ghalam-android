package com.example.ghalam.core.network.data.repository

import com.example.ghalam.core.network.data.model.Credentials
import kotlinx.coroutines.withTimeoutOrNull

class AuthRepository(
    private val service: AuthService,
    private val session: SessionManager,
    private val logoutTimeoutMs: Long = 5_000,
) {
    /** Stores the token on success; throws [MissingTokenException] when the answer had none. */
    suspend fun login(credentials: Credentials) {
        val result = service.login(credentials)
        if (result.token.isBlank()) throw MissingTokenException()
        session.setToken(result.token)
    }

    /** Tells the server first (needs the token), but never blocks the user on it. */
    suspend fun logout() {
        withTimeoutOrNull(logoutTimeoutMs) { runCatching { service.logout() } }
        session.setToken(null)
    }
}
