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
import android.content.SharedPreferences

object TrackArtworkManager {
    private const val PREFS_NAME = "track_artwork"
    
    // Values:
    // "REMOVED" -> Artwork explicitly removed
    // "content://..." -> Custom artwork URI
    // missing -> Use default album art
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveArtwork(context: Context, uri: String, artworkUri: String) {
        getPrefs(context).edit().putString(uri, artworkUri).apply()
    }
    
    fun removeArtwork(context: Context, uri: String) {
        saveArtwork(context, uri, "REMOVED")
    }
    
    fun resetArtwork(context: Context, uri: String) {
        getPrefs(context).edit().remove(uri).apply()
    }
    
    fun getArtworkUri(context: Context, uri: String): String? {
        return getPrefs(context).getString(uri, null)
    }
    
    fun hasCustomArtwork(context: Context, uri: String): Boolean {
        return getPrefs(context).contains(uri)
    }
}

