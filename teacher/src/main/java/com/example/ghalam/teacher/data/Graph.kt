package com.example.ghalam.teacher.data

import android.content.Context
import com.example.ghalam.core.network.data.api.ApiClient
import com.example.ghalam.core.network.data.api.ApiConfig
import com.example.ghalam.core.network.data.repository.AuthRepository
import com.example.ghalam.teacher.data.api.TeacherApi
import com.example.ghalam.teacher.data.repository.TeacherAuthService
import kotlinx.coroutines.flow.StateFlow

/**
 * Teacher app wiring on top of :core:network.
 * No Room database yet: when the first teacher feature needs one, create it in data/local,
 * open it here and wipe it in [ApiClient]'s onSignedOut (like the student app does).
 */
object Graph {
    lateinit var apiClient: ApiClient
        private set
    lateinit var api: TeacherApi
        private set
    lateinit var auth: AuthRepository
        private set

    /** Current token; null = logged out. Collected by the UI. */
    val token: StateFlow<String?> get() = apiClient.session.token

    fun init(context: Context) {
        if (::apiClient.isInitialized) return
        apiClient = ApiClient(
            context = context.applicationContext,
            config = ApiConfig(), // base URL https://ghalam.razzar.ir/, prefs "secure_prefs"
            onSignedOut = {},
        )
        api = apiClient.create(TeacherApi::class.java)
        auth = AuthRepository(TeacherAuthService(api), apiClient.session)
    }

    fun setToken(value: String?) = apiClient.session.setToken(value)
}
