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
import org.json.JSONArray

object CustomSortHelper {
    private const val PREF_KEY_CUSTOM_ORDER = "custom_track_order"

    /**
     * Retrieves the custom ordered list of track paths (URIs) from SharedPreferences.
     */
    fun getCustomOrder(context: Context): List<String> {
        val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PREF_KEY_CUSTOM_ORDER, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Saves the custom ordered list of track paths (URIs) to SharedPreferences.
     */
    fun saveCustomOrder(context: Context, orderList: List<String>) {
        val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (path in orderList) {
            jsonArray.put(path)
        }
        prefs.edit().putString(PREF_KEY_CUSTOM_ORDER, jsonArray.toString()).apply()
    }

    /**
     * Sorts the given list of tracks according to the saved custom order.
     * New tracks that are not present in the saved order are appended to the end.
     */
    fun sortTracksCustom(tracks: List<Track>, customOrder: List<String>): List<Track> {
        if (customOrder.isEmpty()) return tracks

        // Map URIs to tracks for efficient O(1) lookup
        val trackMap = tracks.associateBy { it.uri }
        val sortedList = mutableListOf<Track>()

        // Add tracks in the saved custom order
        for (uri in customOrder) {
            val track = trackMap[uri]
            if (track != null) {
                sortedList.add(track)
            }
        }

        // Identify any tracks that are not yet in the saved custom order (e.g. newly scanned tracks)
        val addedUris = sortedList.map { it.uri }.toSet()
        for (track in tracks) {
            if (track.uri !in addedUris) {
                sortedList.add(track)
            }
        }

        return sortedList
    }
}
