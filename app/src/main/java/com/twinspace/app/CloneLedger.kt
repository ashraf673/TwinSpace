package com.twinspace.app

import android.content.Context

object CloneLedger {
    private const val PREFS = "twinspace.clones"
    private const val KEY = "packages"

    fun packages(context: Context): Set<String> {
        val raw = prefs(context).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split('\u001f').filter { it.isNotBlank() }.toSet()
    }

    fun add(context: Context, packageName: String) {
        val next = packages(context) + packageName
        prefs(context).edit().putString(KEY, next.joinToString("\u001f")).apply()
    }

    fun remove(context: Context, packageName: String) {
        val next = packages(context) - packageName
        prefs(context).edit().putString(KEY, next.joinToString("\u001f")).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
