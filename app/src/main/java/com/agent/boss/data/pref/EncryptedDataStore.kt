package com.agent.boss.data.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedDataStore(context: Context) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getDeepSeekApiKey(): String {
        return securePrefs.getString(KEY_DEEPSEEK_API_KEY, "")?.trim() ?: ""
    }

    fun setDeepSeekApiKey(apiKey: String) {
        securePrefs.edit().putString(KEY_DEEPSEEK_API_KEY, apiKey.trim()).apply()
    }

    fun hasValidApiKey(): Boolean {
        return getDeepSeekApiKey().isNotEmpty()
    }

    fun getResumeMarkdown(): String {
        return securePrefs.getString(KEY_RESUME_MARKDOWN, "") ?: ""
    }

    fun setResumeMarkdown(markdown: String) {
        securePrefs.edit().putString(KEY_RESUME_MARKDOWN, markdown).apply()
    }

    fun clearAll() {
        securePrefs.edit().clear().apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "boss_agent_secure_prefs"
        private const val KEY_DEEPSEEK_API_KEY = "key_deepseek_api_key"
        private const val KEY_RESUME_MARKDOWN = "key_resume_markdown"

        @Volatile
        private var instance: EncryptedDataStore? = null

        fun getInstance(context: Context): EncryptedDataStore {
            return instance ?: synchronized(this) {
                instance ?: EncryptedDataStore(context).also { instance = it }
            }
        }
    }
}
