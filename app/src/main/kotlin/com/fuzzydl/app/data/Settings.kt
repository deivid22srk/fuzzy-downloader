package com.fuzzydl.app.data

import android.content.Context

data class AppSettings(
    val threads: Int = 4,
    val wifiOnly: Boolean = true,
    val theme: String = "dark",
    val dataSourceUrl: String = DEFAULT_SOURCE,
)

/** Preferências do app (sem nada específico de catálogo). */
object SettingsStore {
    const val DEFAULT_SOURCE = "https://raw.githubusercontent.com/deivid22srk/port-db/main/"
    private const val PREFS = "fuzzy_settings"

    fun load(context: Context): AppSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppSettings(
            threads = p.getInt("threads", 4).coerceIn(1, 8),
            wifiOnly = p.getBoolean("wifiOnly", true),
            theme = p.getString("theme", "dark") ?: "dark",
            dataSourceUrl = p.getString("dataSourceUrl", DEFAULT_SOURCE)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_SOURCE,
        )
    }

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("threads", settings.threads)
            .putBoolean("wifiOnly", settings.wifiOnly)
            .putString("theme", settings.theme)
            .putString("dataSourceUrl", settings.dataSourceUrl)
            .apply()
    }
}