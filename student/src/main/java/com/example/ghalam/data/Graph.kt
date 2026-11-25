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
import com.example.ghalam.data.media.MediaManager
import com.example.ghalam.data.media.MediaPrefs
import com.example.ghalam.data.outbox.Outbox
import com.example.ghalam.data.outbox.SubmitPrefs
import com.example.ghalam.data.outbox.UploadEngine
import com.example.ghalam.data.outbox.UploadNotifier
import com.example.ghalam.data.outbox.UploadScheduler
import com.example.ghalam.data.repository.StudentAuthService
import com.example.ghalam.data.repository.TaskRepository
import com.example.ghalam.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Student app wiring: the shared pieces come from :core:network, everything that belongs to
 * this app (database name, what to wipe on logout, the API interface) is decided here.
 *
 * Contains both the media part (MediaManager) and the upload outbox part
 * (SubmitPrefs, NetworkMonitor, UploadScheduler, UploadEngine, Outbox).
 */
object Graph {
    private const val DB_NAME = "ghalam.db"

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val json: Json = ApiJson

    /** true while MainActivity is on screen; the upload notification is not shown then. */
    @Volatile
    var appVisible: Boolean = false

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
    lateinit var media: MediaManager
        private set

    lateinit var prefs: SubmitPrefs
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var scheduler: UploadScheduler
        private set
    lateinit var engine: UploadEngine
        private set
    lateinit var outbox: Outbox
        private set

    /** Current token; null = logged out. Collected by the UI. */
    val token: StateFlow<String?> get() = apiClient.session.token

    private fun signedIn(): Boolean = !apiClient.session.token.value.isNullOrBlank()

    fun init(context: Context) {
        if (::apiClient.isInitialized) return
        appContext = context.applicationContext

        db = Room.databaseBuilder(appContext, AppDatabase::class.java, DB_NAME)
            .fallbackToDestructiveMigration() // it is only a cache, the server is the source of truth
            .build()

        prefs = SubmitPrefs(appContext)
        scheduler = UploadScheduler(appContext, prefs)

        apiClient = ApiClient(
            context = appContext,
            config = ApiConfig(), // base URL https://ghalam.razzar.ir/, prefs "secure_prefs"
            // logged out (manually or 401): never leave one student's cached data for the next login.
            // The outbox (queued photos and answers) is kept on purpose, so an expired token
            // does not lose what the student already prepared.
            onSignedOut = {
                ioScope.launch {
                    runCatching { media.clearAll() } // downloaded files too
                    runCatching { clearCache() }
                }
            },
        )
        store = apiClient.store
        client = apiClient.client
        api = apiClient.create(GhalamApi::class.java)
        repo = TaskRepository(api, db, apiClient.json)
        auth = AuthRepository(StudentAuthService(api), apiClient.session)
        imageLoader = createAuthImageLoader(appContext, client)
        media = MediaManager(appContext, client, db.mediaDao(), MediaPrefs(appContext))

        network = NetworkMonitor(appContext)
        engine = UploadEngine(
            appContext,
            api,
            db.outboxDao(),
            repo,
            prefs,
            network,
            { signedIn() },
            UploadNotifier(appContext, prefs) { appVisible },
        )
        outbox = Outbox(appContext, db.outboxDao(), prefs) { kickUpload() }

        network.start { onNetworkImproved() }
    }

    fun setToken(value: String?) = apiClient.session.setToken(value)

    /** Starts the background worker and, when online, one upload pass right now. */
    fun kickUpload() {
        if (!signedIn()) return
        scheduler.ensureWorker()
        if (network.online.value) ioScope.launch { engine.run() }
    }

    private fun onNetworkImproved() {
        if (!signedIn()) return
        scheduler.ensureWorker()
        ioScope.launch { engine.run(true) }
    }

    /** Call after login / app start: periodic safety net + one pass now. */
    fun resumeUploads() {
        if (!signedIn()) return
        scheduler.ensurePeriodic()
        kickUpload()
    }

    /**
     * Wipes every cache table (tasks, entries, remote keys, cached json, media list ...) but keeps the
     * outbox tables. Done by table name on purpose, so it keeps working when a DAO is renamed.
     */
    private suspend fun clearCache() = withContext(Dispatchers.IO) {
        val keep = setOf("pending_uploads", "pending_answers", "local_media")
        val sdb = db.openHelper.writableDatabase
        val names = mutableListOf<String>()
        sdb.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name NOT LIKE 'room_%'",
        ).use { c ->
            while (c.moveToNext()) names += c.getString(0)
        }
        db.runInTransaction(
            Runnable {
                names.filter { it !in keep }.forEach { sdb.execSQL("DELETE FROM `$it`") }
            },
        )
    }
}
