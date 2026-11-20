package com.example.ghalam.core.network.data.api

const val DEFAULT_BASE_URL = "https://ghalam.razzar.ir/"

/** Everything that differs between the apps (student / teacher / manager) when talking to the server. */
class ApiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    /** File name of the encrypted prefs that keep the token. Keep "secure_prefs" for the student app. */
    val tokenPrefsName: String = "secure_prefs",
    /** Only requests under this prefix are treated as API calls for the 401 handling. */
    val apiPathPrefix: String = "/api/",
    /** A 401 on these paths is a failed login attempt, not an expired session. */
    val isAuthPath: (encodedPath: String) -> Boolean = { it.endsWith("/login") },
)
