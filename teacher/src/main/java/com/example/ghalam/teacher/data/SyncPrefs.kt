package com.example.ghalam.teacher.data

import android.content.Context

/** Small settings and bookkeeping of the sync. Wiped on logout. */
class SyncPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("teacher_sync", Context.MODE_PRIVATE)

    /** Whose data is on the phone. A login by someone else wipes the database first. */
    var ownerUserId: Long
        get() = sp.getLong("owner_id", 0L)
        set(value) {
            sp.edit().putLong("owner_id", value).apply()
        }

    var userName: String
        get() = sp.getString("user_name", "").orEmpty()
        set(value) {
            sp.edit().putString("user_name", value).apply()
        }

    var lastSyncAt: Long
        get() = sp.getLong("last_sync_at", 0L)
        set(value) {
            sp.edit().putLong("last_sync_at", value).apply()
        }

    /** Download the students' files in the background. */
    var mediaAuto: Boolean
        get() = sp.getBoolean("media_auto", true)
        set(value) {
            sp.edit().putBoolean("media_auto", value).apply()
        }

    /** ... only on an unmetered connection (Wi-Fi). */
    var mediaUnmeteredOnly: Boolean
        get() = sp.getBoolean("media_unmetered_only", true)
        set(value) {
            sp.edit().putBoolean("media_unmetered_only", value).apply()
        }

    /** Biggest file the background sync downloads by itself. */
    var mediaLimitBytes: Long
        get() = sp.getLong("media_limit_bytes", 10L * 1024 * 1024)
        set(value) {
            sp.edit().putLong("media_limit_bytes", value).apply()
        }

    fun clear() {
        sp.edit().clear().apply()
    }
}
