package com.aicompanion.core.common

import android.content.Context

class ProactiveSettings(context: Context) {
    private val prefs = context.getSharedPreferences("proactive_msg", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var mode: String
        get() = prefs.getString("mode", "random") ?: "random"
        set(value) = prefs.edit().putString("mode", value).apply()

    var fixedIntervalMinutes: Int
        get() = prefs.getInt("fixed_interval", 60)
        set(value) = prefs.edit().putInt("fixed_interval", value).apply()

    var randomMinMinutes: Int
        get() = prefs.getInt("random_min", 30)
        set(value) = prefs.edit().putInt("random_min", value).apply()

    var randomMaxMinutes: Int
        get() = prefs.getInt("random_max", 120)
        set(value) = prefs.edit().putInt("random_max", value).apply()

    var selectedPersonaIds: Set<String>
        get() = prefs.getStringSet("selected_personas", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("selected_personas", value).apply()

    fun getNextDelayMinutes(): Long {
        return if (mode == "fixed") {
            fixedIntervalMinutes.toLong()
        } else {
            (randomMinMinutes..randomMaxMinutes).random().toLong()
        }
    }
}
