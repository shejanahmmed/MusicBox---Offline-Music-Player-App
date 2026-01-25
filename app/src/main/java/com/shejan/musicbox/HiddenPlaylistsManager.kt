/*
 * Copyright (C) 2026 Shejan
 *
 * This file is part of MusicBox.
 *
 * MusicBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MusicBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MusicBox.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shejan.musicbox

import android.content.Context
import androidx.core.content.edit

@Suppress("unused")
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
        prefs.edit { putStringSet(KEY_HIDDEN_IDS, current) }
    }

    fun unhidePlaylist(context: Context, playlistId: Long) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = getHiddenPlaylists(context).toMutableSet()
        current.remove(playlistId.toString())
        prefs.edit { putStringSet(KEY_HIDDEN_IDS, current) }
    }
}
