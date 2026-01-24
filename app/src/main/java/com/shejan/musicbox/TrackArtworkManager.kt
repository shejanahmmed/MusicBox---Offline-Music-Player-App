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
    
    fun saveArtwork(context: Context, trackId: Long, uri: String) {
        getPrefs(context).edit().putString(trackId.toString(), uri).apply()
    }
    
    fun removeArtwork(context: Context, trackId: Long) {
        saveArtwork(context, trackId, "REMOVED")
    }
    
    fun resetArtwork(context: Context, trackId: Long) {
        getPrefs(context).edit().remove(trackId.toString()).apply()
    }
    
    fun getArtworkUri(context: Context, trackId: Long): String? {
        return getPrefs(context).getString(trackId.toString(), null)
    }
    
    fun hasCustomArtwork(context: Context, trackId: Long): Boolean {
        return getPrefs(context).contains(trackId.toString())
    }
}

