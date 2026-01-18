package com.shejan.musicbox

import android.content.Context

object HiddenTracksManager {
    private const val PREF_NAME = "MusicBoxHidden"
    private const val KEY_HIDDEN = "hidden_uris"

    fun isHidden(context: Context, uri: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        return hidden.contains(uri)
    }

    fun hideTrack(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet(KEY_HIDDEN, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        hidden.add(uri)
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply()
    }
    
    // Optional: If we want to unhide later, but basic feature is just Hide.
    fun unhideTrack(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hidden = prefs.getStringSet(KEY_HIDDEN, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        hidden.remove(uri)
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply()
    }
    
    fun getHiddenTracks(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    }
}
