package com.example.ghalam.core.network.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Keeps the API token encrypted on the device. */
class TokenStore(context: Context, prefsName: String = "secure_prefs") {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        prefsName,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var token: String?
        get() = prefs.getString("token", null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove("token") else putString("token", value)
            }.apply()
        }
}
