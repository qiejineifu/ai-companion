package com.aicompanion.core.common

import android.content.Context

/** Tracks unread proactive message counts per persona via SharedPreferences. */
object UnreadTracker {
    private const val PREFS = "unread_counts"

    fun increment(context: Context, personaId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(personaId, get(context, personaId) + 1).apply()
    }

    fun get(context: Context, personaId: String): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(personaId, 0)
    }

    fun clear(context: Context, personaId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(personaId).apply()
    }

    fun getAll(context: Context, personaIds: List<String>): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return personaIds.associateWith { prefs.getInt(it, 0) }
    }
}
