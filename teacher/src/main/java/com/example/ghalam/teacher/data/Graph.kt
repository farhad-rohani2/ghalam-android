package com.example.ghalam.teacher.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.example.ghalam.core.network.data.api.ApiClient
import com.example.ghalam.core.network.data.api.ApiConfig
import com.example.ghalam.core.network.data.repository.AuthRepository
import com.example.ghalam.core.ui.util.createAuthImageLoader
import com.example.ghalam.teacher.data.api.TeacherApi
import com.example.ghalam.teacher.data.local.TeacherDatabase
import com.example.ghalam.teacher.data.media.TeacherMedia
import com.example.ghalam.teacher.data.repository.TeacherAuthService
import com.example.ghalam.teacher.data.repository.TeacherRepository
import com.example.ghalam.teacher.data.sync.SyncEngine
import com.example.ghalam.teacher.data.sync.SyncScheduler
import com.example.ghalam.teacher.data.sync.SyncStatus
import com.example.ghalam.teacher.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Teacher app wiring on top of :core:network.
 *
 * Offline-first: everything the teacher sees comes from Room, the background workers fill it, and
 * feedback written offline waits in Room until it is uploaded. For that reason a 401 (expired
 * token) does NOT wipe the database like in the student app: the unsent feedback must survive.
 * Data is wiped only on an explicit logout ([signOut]) and when a different teacher logs in.
 */
object Graph {
    private const val DB_NAME = "ghalam_teacher.db"

    lateinit var appContext: Context
        private set
    lateinit var apiClient: ApiClient
        private set
    lateinit var api: TeacherApi
        private set
    lateinit var auth: AuthRepository
        private set
    lateinit var db: TeacherDatabase
        private set
    lateinit var prefs: SyncPrefs
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var scheduler: SyncScheduler
        private set
    lateinit var media: TeacherMedia
        private set
    lateinit var repo: TeacherRepository
        private set
    lateinit var sync: SyncEngine
        private set
    lateinit var imageLoader: ImageLoader
        private set

    /** Current token; null = logged out. Collected by the UI. */
    val token: StateFlow<String?> get() = apiClient.session.token

    fun init(context: Context) {
        if (::apiClient.isInitialized) return
        appContext = context.applicationContext

        db = Room.databaseBuilder(appContext, TeacherDatabase::class.java, DB_NAME).build()
        prefs = SyncPrefs(appContext)
        network = NetworkMonitor(appContext)
        scheduler = SyncScheduler(appContext)

        apiClient = ApiClient(
            context = appContext,
            config = ApiConfig(), // base URL https://ghalam.razzar.ir/, prefs "secure_prefs"
            onSignedOut = {}, // see the class comment: nothing is wiped here
        )
        api = apiClient.create(TeacherApi::class.java)
        media = TeacherMedia(appContext, apiClient.client, db.mediaDao())
        repo = TeacherRepository(db, apiClient.json) { scheduler.enqueueUpload() }
        sync = SyncEngine(api, db, repo, media, prefs, network, apiClient.json)
        imageLoader = createAuthImageLoader(appContext, apiClient.client)
        auth = AuthRepository(TeacherAuthService(api) { id, name -> onLoggedIn(id, name) }, apiClient.session)

        SyncStatus.update { it.copy(lastSuccessAt = prefs.lastSyncAt) }
    }

    /** Someone else's data must never be shown to the next teacher who logs in on this phone. */
    private suspend fun onLoggedIn(id: Long, name: String) {
        if (prefs.ownerUserId != id) wipeData()
        prefs.ownerUserId = id
        prefs.userName = name
    }

    /** The logout button: tells the server, then removes everything of this account from the phone. */
    suspend fun signOut() {
        auth.logout()
        wipeData()
        prefs.clear()
    }

    private suspend fun wipeData() {
        scheduler.cancelAll()
        media.clearAll()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        SyncStatus.reset()
    }
}
