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

