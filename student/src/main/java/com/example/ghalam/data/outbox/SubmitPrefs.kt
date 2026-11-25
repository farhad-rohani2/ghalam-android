package com.example.ghalam.data.outbox

import android.content.Context

/** The student's choices about sending answers. Kept across logins (they belong to the phone). */
class SubmitPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("student_submit", Context.MODE_PRIVATE)

    /** Send photos in the background as soon as there is internet. */
    var autoUpload: Boolean
        get() = sp.getBoolean("auto_upload", true)
        set(value) {
            sp.edit().putBoolean("auto_upload", value).apply()
        }

    /** Only on an unmetered connection (Wi-Fi). Default: any internet. */
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", false)
        set(value) {
            sp.edit().putBoolean("wifi_only", value).apply()
        }

    /** Files bigger than [limitAutoMb] wait for a manual "send". Default: no limit. */
    var limitAuto: Boolean
        get() = sp.getBoolean("limit_auto", false)
        set(value) {
            sp.edit().putBoolean("limit_auto", value).apply()
        }

    var limitAutoMb: Int
        get() = sp.getInt("limit_auto_mb", 10)
        set(value) {
            sp.edit().putInt("limit_auto_mb", value).apply()
        }

    /** A simple notification about the result (never asks for a permission). */
    var notifyResult: Boolean
        get() = sp.getBoolean("notify_result", true)
        set(value) {
            sp.edit().putBoolean("notify_result", value).apply()
        }

    /** Hash of the national code whose unsent answers are on the phone ("" = nobody). */
    var ownerHash: String
        get() = sp.getString("owner_hash", "").orEmpty()
        set(value) {
            sp.edit().putString("owner_hash", value).apply()
        }
}
