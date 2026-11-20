package com.example.ghalam.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.example.ghalam.core.network.data.api.ApiClient
import com.example.ghalam.core.network.data.api.ApiConfig
import com.example.ghalam.core.network.data.api.ApiJson
import com.example.ghalam.core.network.data.repository.AuthRepository
import com.example.ghalam.core.network.data.repository.TokenStore
import com.example.ghalam.core.ui.util.createAuthImageLoader
import com.example.ghalam.data.api.GhalamApi
import com.example.ghalam.data.local.AppDatabase
import com.example.ghalam.data.repository.StudentAuthService
import com.example.ghalam.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Student app wiring: the shared pieces come from :core:network, everything that belongs to
 * this app (database name, what to wipe on logout, the API interface) is decided here.
 */
object Graph {
    private const val DB_NAME = "ghalam.db"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json: Json = ApiJson

    lateinit var appContext: Context
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var store: TokenStore
        private set
    lateinit var client: OkHttpClient
        private set
    lateinit var api: GhalamApi
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var repo: TaskRepository
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var imageLoader: ImageLoader
        private set

    /** Current token; null = logged out. Collected by the UI. */
    val token: StateFlow<String?> get() = apiClient.session.token

    fun init(context: Context) {
        if (::apiClient.isInitialized) return
        appContext = context.applicationContext

        db = Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration() // it is only a cache, the server is the source of truth
            .build()

        apiClient = ApiClient(
            context = appContext,
            config = ApiConfig(), // base URL https://ghalam.razzar.ir/, prefs "secure_prefs"
            // logged out (manually or 401): never leave one student's data for the next login
            onSignedOut = { ioScope.launch { runCatching { db.clearAllTables() } } },
        )
        store = apiClient.store
        client = apiClient.client
        api = apiClient.create(GhalamApi::class.java)
        repo = TaskRepository(api, db, apiClient.json)
        auth = AuthRepository(StudentAuthService(api), apiClient.session)
        imageLoader = createAuthImageLoader(appContext, client)
    }

    fun setToken(value: String?) = apiClient.session.setToken(value)
}
