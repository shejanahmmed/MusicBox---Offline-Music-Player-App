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
import org.json.JSONObject
import java.io.File

data class AppPlaylist(
    val id: Long,
    var name: String,
    val trackPaths: MutableList<String> = mutableListOf(), // Storing Paths is safer than IDs if media store IDs change, but IDs are standard. Let's store Paths for robustness across resets? 
    // Actually standard is IDs. But user said "do not store in device storage". 
    // MediaStore IDs are persistent usually.
    // Let's store Track objects or just Paths?
    // Paths survive re-scanning better if IDs rotate.
    // Let's store PATHS (String).
)

object AppPlaylistManager {
    private const val FILE_NAME = "app_playlists.json"

    private fun getFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun getAllPlaylists(context: Context): List<AppPlaylist> {
        val file = getFile(context)
        if (!file.exists()) return emptyList()

        val jsonString = file.readText()
        if (jsonString.isBlank()) return emptyList()

        val list = mutableListOf<AppPlaylist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val name = obj.getString("name")
                val tracksArray = obj.getJSONArray("tracks")
                val paths = mutableListOf<String>()
                for (j in 0 until tracksArray.length()) {
                    paths.add(tracksArray.getString(j))
                }
                list.add(AppPlaylist(id, name, paths))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun savePlaylists(context: Context, playlists: List<AppPlaylist>) {
        val jsonArray = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val tracksArray = JSONArray()
            playlist.trackPaths.forEach { tracksArray.put(it) }
            obj.put("tracks", tracksArray)
            jsonArray.put(obj)
        }
        getFile(context).writeText(jsonArray.toString())
        
        // Notify observers that content has changed
        MusicUtils.contentVersion++
    }

    fun createPlaylist(context: Context, name: String, trackPaths: List<String>): Long {
        val playlists = getAllPlaylists(context).toMutableList()
        val newId = System.currentTimeMillis() // Simple unique ID
        val newPlaylist = AppPlaylist(newId, name, trackPaths.toMutableList())
        playlists.add(newPlaylist)
        savePlaylists(context, playlists)
        return newId
    }

    fun deletePlaylist(context: Context, playlistId: Long) {
        val playlists = getAllPlaylists(context).toMutableList()
        playlists.removeAll { it.id == playlistId }
        savePlaylists(context, playlists)
    }

    fun updatePlaylist(context: Context, playlistId: Long, name: String, trackPaths: List<String>) {
        val playlists = getAllPlaylists(context).toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            playlists[index] = AppPlaylist(playlistId, name, trackPaths.toMutableList())
            savePlaylists(context, playlists)
        }
    }
    
    fun getPlaylist(context: Context, playlistId: Long): AppPlaylist? {
        return getAllPlaylists(context).find { it.id == playlistId }
    }

    fun addTrackToPlaylist(context: Context, playlistId: Long, trackUri: String) {
        val playlists = getAllPlaylists(context).toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlists[index]
            if (!playlist.trackPaths.contains(trackUri)) {
                playlist.trackPaths.add(trackUri)
                savePlaylists(context, playlists)
            }
        }
    }

    fun removeTrackFromAllPlaylists(context: Context, trackUri: String) {
        val playlists = getAllPlaylists(context).toMutableList()
        var changed = false
        
        for (playlist in playlists) {
            if (playlist.trackPaths.contains(trackUri)) {
                playlist.trackPaths.remove(trackUri)
                changed = true
            }
        }
        
        if (changed) {
            savePlaylists(context, playlists)
        }
    }
}
