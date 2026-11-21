package com.example.ghalam.data.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User settings for media. Files with size <= [limitBytes] are downloaded automatically. */
class MediaPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("media_settings", Context.MODE_PRIVATE)
    private val _limit = MutableStateFlow(sp.getLong(KEY_LIMIT, DEFAULT_LIMIT))

    val limitBytes: StateFlow<Long> = _limit.asStateFlow()

    fun setLimitBytes(value: Long) {
        sp.edit().putLong(KEY_LIMIT, value).apply()
        _limit.value = value
    }

    companion object {
        private const val KEY_LIMIT = "auto_download_limit_bytes"
        const val KB = 1024L
        const val MB = 1024L * 1024L
        const val DEFAULT_LIMIT = 1 * MB

        /** 0 = never auto-download, Long.MAX_VALUE = everything. */
        val OPTIONS: List<Long> = listOf(
            0L, 256 * KB, 512 * KB, 1 * MB, 2 * MB, 5 * MB, 10 * MB, 25 * MB, 50 * MB, 100 * MB, Long.MAX_VALUE,
        )
    }
}
