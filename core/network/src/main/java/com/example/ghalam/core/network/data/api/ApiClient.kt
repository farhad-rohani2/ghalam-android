package com.example.ghalam.core.network.data.api

import android.content.Context
import com.example.ghalam.core.network.data.repository.SessionManager
import com.example.ghalam.core.network.data.repository.TokenStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Token store + session + OkHttp + Retrofit for one app. Nothing app-specific is hard-coded here:
 * the base URL and the 401 rules come from [config], and what must happen when the session
 * ends (for example wiping that app's database) is passed as [onSignedOut].
 */
class ApiClient(
    context: Context,
    val config: ApiConfig = ApiConfig(),
    onSignedOut: () -> Unit = {},
) {
    val json: Json = ApiJson

    val store = TokenStore(context.applicationContext, config.tokenPrefsName)
    val session = SessionManager(store, onSignedOut)

    val client: OkHttpClient = OkHttpClient.Builder()
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
            if (res.code == 401 && path.startsWith(config.apiPathPrefix) && !config.isAuthPath(path)) {
                session.setToken(null)
            }
            res
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC // never BODY/HEADERS: leaks token
        })
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(config.baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun <T : Any> create(service: Class<T>): T = retrofit.create(service)
}
