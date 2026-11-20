package com.example.ghalam.core.network.data.repository

import com.example.ghalam.core.network.data.model.AuthResult
import com.example.ghalam.core.network.data.model.Credentials

/** Implemented once per app: knows that app's login / logout endpoints. */
interface AuthService {
    suspend fun login(credentials: Credentials): AuthResult
    suspend fun logout()
}

/** The server answered, but the answer had no token. */
class MissingTokenException : Exception("Login response had no token")

/** An app was given a kind of credentials it does not support. */
class UnsupportedCredentialsException(credentials: Credentials) :
    IllegalArgumentException("Unsupported credentials: ${credentials::class.simpleName}")
