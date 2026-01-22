package com.shejan.musicbox

import android.content.Context
import androidx.core.content.edit

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
        val currentHidden = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        val newHidden = HashSet(currentHidden)
        newHidden.add(uri)
        prefs.edit {
            remove(KEY_HIDDEN)
            putStringSet(KEY_HIDDEN, newHidden)
        }
    }
    
    fun getHiddenTracks(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    }
    
    fun restoreTrack(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentHidden = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        val newHidden = HashSet(currentHidden)
        newHidden.remove(uri)
        prefs.edit {
            remove(KEY_HIDDEN)
            putStringSet(KEY_HIDDEN, newHidden)
        }
    }
}
