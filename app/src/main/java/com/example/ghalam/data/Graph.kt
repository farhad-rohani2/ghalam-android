package com.example.ghalam.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/** Single place that owns the token, the HTTP client, the API, the local DB and the image loader. */
object Graph {
    private const val BASE_URL = "https://ghalam.razzar.ir/"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var appContext: Context
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
    lateinit var imageLoader: ImageLoader
        private set

    /** Current token; null = logged out. Collected by the UI. */
    val token = MutableStateFlow<String?>(null)

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true // pageId has a default value and must still be sent
    }

    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        store = TokenStore(appContext)
        token.value = store.token

        db = Room.databaseBuilder(appContext, AppDatabase::class.java, "ghalam.db")
            .fallbackToDestructiveMigration() // it is only a cache, the server is the source of truth
            .build()

        client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("Accept", "application/json, text/plain, */*")
                    .apply { store.token?.let { header("Authorization", "Bearer $it") } }
                    .build()
                val res = chain.proceed(req)
                val path = req.url.encodedPath
                // expired / revoked token -> back to the login screen
                if (res.code == 401 && path.startsWith("/api/") && !path.endsWith("/login")) {
                    setToken(null)
                }
                res
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC // never BODY/HEADERS: leaks token
            })
            .build()

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GhalamApi::class.java)

        repo = TaskRepository(api, db, json)

        // images under /storage need the Bearer header too, so Coil reuses the same client.
        // respectCacheHeaders(false): keep images in Coil's disk cache even if the server says no-cache,
        // so already-seen photos are still visible offline.
        imageLoader = ImageLoader.Builder(appContext)
            .okHttpClient(client)
            .respectCacheHeaders(false)
            .build()
    }

    fun setToken(value: String?) {
        store.token = value
        token.value = value
        // logged out (manually or 401): never leave one student's data for the next login
        if (value == null) ioScope.launch { runCatching { db.clearAllTables() } }
    }
}
