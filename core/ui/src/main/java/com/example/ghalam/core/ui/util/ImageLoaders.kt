package com.example.ghalam.core.ui.util

import android.content.Context
import coil.ImageLoader
import okhttp3.OkHttpClient

/**
 * Images under /storage need the Bearer header too, so Coil reuses the app's own OkHttp client.
 * respectCacheHeaders(false): keep images in Coil's disk cache even if the server says no-cache,
 * so already-seen photos are still visible offline.
 */
fun createAuthImageLoader(context: Context, client: OkHttpClient): ImageLoader =
    ImageLoader.Builder(context.applicationContext)
        .okHttpClient(client)
        .respectCacheHeaders(false)
        .build()
