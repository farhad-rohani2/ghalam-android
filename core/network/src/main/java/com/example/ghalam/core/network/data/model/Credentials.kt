package com.example.ghalam.core.network.data.model

/** The two ways to sign in: student = national code, teacher / manager = username + password. */
sealed interface Credentials {
    data class NationalCode(val code: String) : Credentials
    data class UsernamePassword(val username: String, val password: String) : Credentials
}

data class AuthResult(val token: String)
