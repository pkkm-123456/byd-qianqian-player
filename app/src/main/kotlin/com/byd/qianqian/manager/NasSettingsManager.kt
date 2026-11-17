package com.byd.qianqian.manager

import android.content.Context
import android.content.SharedPreferences

data class NasConfig(
    val serverUrl: String = "http://192.168.1.100:5005",
    val username: String = "",
    val password: String = "",
    val targetDir: String = "Music",
    val webdavEnabled: Boolean = true,
    val cacheModeEnabled: Boolean = true,
    val autoArchiveOnFavorite: Boolean = true,
    val cacheQuotaGb: Float = 4f
)

object NasSettingsManager {
    private const val PREF_NAME = "qianqian_nas_settings"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): NasConfig {
        val prefs = getPrefs(context)
        return NasConfig(
            serverUrl = prefs.getString("server_url", "http://192.168.1.100:5005") ?: "http://192.168.1.100:5005",
            username = prefs.getString("username", "") ?: "",
            password = prefs.getString("password", "") ?: "",
            targetDir = prefs.getString("target_dir", "Music") ?: "Music",
            webdavEnabled = prefs.getBoolean("webdav_enabled", true),
            cacheModeEnabled = prefs.getBoolean("cache_mode_enabled", true),
            autoArchiveOnFavorite = prefs.getBoolean("auto_archive_fav", true),
            cacheQuotaGb = prefs.getFloat("cache_quota_gb", 4f)
        )
    }

    fun save(context: Context, config: NasConfig) {
        getPrefs(context).edit().apply {
            putString("server_url", config.serverUrl)
            putString("username", config.username)
            putString("password", config.password)
            putString("target_dir", config.targetDir)
            putBoolean("webdav_enabled", config.webdavEnabled)
            putBoolean("cache_mode_enabled", config.cacheModeEnabled)
            putBoolean("auto_archive_fav", config.autoArchiveOnFavorite)
            putFloat("cache_quota_gb", config.cacheQuotaGb)
            apply()
        }
    }
}
