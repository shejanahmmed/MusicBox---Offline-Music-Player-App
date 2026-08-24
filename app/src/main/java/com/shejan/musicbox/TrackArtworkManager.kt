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
import androidx.core.content.edit

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
        getPrefs(context).edit { putString(uri, artworkUri) }
    }

    fun saveArtworkFromUri(context: Context, trackUri: String, selectedUri: android.net.Uri): Boolean {
        return try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val dir = java.io.File(context.filesDir, "custom_artwork")
            if (!dir.exists()) dir.mkdirs()

            val hash = Math.abs(trackUri.hashCode())
            val destFile = java.io.File(dir, "art_$hash.jpg")

            context.contentResolver.openInputStream(selectedUri)?.use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                saveArtwork(context, trackUri, android.net.Uri.fromFile(destFile).toString())
                true
            } else {
                saveArtwork(context, trackUri, selectedUri.toString())
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to storing raw URI string
            saveArtwork(context, trackUri, selectedUri.toString())
            true
        }
    }
    
    fun removeArtwork(context: Context, uri: String) {
        saveArtwork(context, uri, "REMOVED")
    }
    
    fun resetArtwork(context: Context, uri: String) {
        try {
            val hash = Math.abs(uri.hashCode())
            val destFile = java.io.File(java.io.File(context.filesDir, "custom_artwork"), "art_$hash.jpg")
            if (destFile.exists()) destFile.delete()
        } catch (_: Exception) {}
        getPrefs(context).edit { remove(uri) }
    }
    
    fun getArtworkUri(context: Context, uri: String): String? {
        return getPrefs(context).getString(uri, null)
    }
    
    fun hasCustomArtwork(context: Context, uri: String): Boolean {
        return getPrefs(context).contains(uri)
    }
}


