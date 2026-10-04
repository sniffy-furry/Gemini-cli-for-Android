package com.gemini.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object KeyStore {
    private fun prefs(c: Context) = EncryptedSharedPreferences.create(
        c, "secure",
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun get(c: Context): String? = prefs(c).getString("gemini_api_key", null)
    fun set(c: Context, v: String) = prefs(c).edit().putString("gemini_api_key", v).apply()
}
