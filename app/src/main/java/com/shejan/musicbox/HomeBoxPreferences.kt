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

object HomeBoxPreferences {
    private const val PREF_NAME = "HomeBoxPrefs"
    
    // Box IDs
    const val BOX_FAVORITES = "favorites"
    const val BOX_PLAYLISTS = "playlists"
    const val BOX_ALBUMS = "albums"
    const val BOX_ARTISTS = "artists"
    const val BOX_TRACKS = "tracks"
    const val BOX_EQUALIZER = "equalizer"
    
    fun isBoxVisible(context: Context, boxId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val defaultValue = when (boxId) {
            BOX_ARTISTS, BOX_EQUALIZER -> false // Default hidden
            else -> true // Default visible
        }
        return prefs.getBoolean(boxId, defaultValue)
    }
    
    fun setBoxVisibility(context: Context, boxId: String, visible: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(boxId, visible) }
    }
    
    fun getAllBoxes(): List<HomeBox> {
        return listOf(
            HomeBox(BOX_FAVORITES, "Favorites", R.drawable.ic_heart),
            HomeBox(BOX_TRACKS, "Tracks", R.drawable.ic_music_note),
            HomeBox(BOX_ARTISTS, "Artists", R.drawable.ic_person),
            HomeBox(BOX_PLAYLISTS, "Playlists", R.drawable.ic_queue_music),
            HomeBox(BOX_ALBUMS, "Albums", R.drawable.ic_album),
            HomeBox(BOX_EQUALIZER, "Equalizer", R.drawable.ic_equalizer)
        )
    }
    
    // Box order management
    private const val KEY_BOX_ORDER = "box_order"
    
    fun getBoxOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val orderString = prefs.getString(KEY_BOX_ORDER, null)
        
        return orderString?.split(",") ?: listOf(
            BOX_FAVORITES, BOX_TRACKS, BOX_ARTISTS, BOX_PLAYLISTS, BOX_ALBUMS, BOX_EQUALIZER
        )
    }
    
    fun saveBoxOrder(context: Context, order: List<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val orderString = order.joinToString(",")
        prefs.edit { putString(KEY_BOX_ORDER, orderString) }
    }
}

data class HomeBox(
    val id: String,
    val name: String,
    val iconRes: Int
)

