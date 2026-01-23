package com.shejan.musicbox

import android.content.Context

object HiddenPlaylistsManager {
    private const val PREF_NAME = "HiddenPlaylistsPrefs"
    private const val KEY_HIDDEN_IDS = "hidden_playlist_ids"

    fun getHiddenPlaylists(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_HIDDEN_IDS, emptySet()) ?: emptySet()
    }

    fun hidePlaylist(context: Context, playlistId: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getHiddenPlaylists(context).toMutableSet()
        current.add(playlistId.toString())
        prefs.edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }

    fun unhidePlaylist(context: Context, playlistId: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getHiddenPlaylists(context).toMutableSet()
        current.remove(playlistId.toString())
        prefs.edit().putStringSet(KEY_HIDDEN_IDS, current).apply()
    }
}
