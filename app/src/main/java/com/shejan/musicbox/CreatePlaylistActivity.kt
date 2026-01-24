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


import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreatePlaylistActivity : AppCompatActivity() {

    private lateinit var adapter: TrackSelectionAdapter
    private val allTracks = mutableListOf<Track>()
    private var editPlaylistId: Long = -1L
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_playlist)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        loadTracks()

        val rv = findViewById<RecyclerView>(R.id.rv_track_selection)
        adapter = TrackSelectionAdapter(allTracks) { _, _ -> }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Check for edit mode
        editPlaylistId = intent.getLongExtra("EDIT_PLAYLIST_ID", -1L)
        if (editPlaylistId != -1L) {
            isEditMode = true
            setupEditMode()
        }

        findViewById<FloatingActionButton>(R.id.fab_save_playlist).setOnClickListener {
            savePlaylist()
        }
    }

    private fun setupEditMode() {
        val playlist = AppPlaylistManager.getPlaylist(this, editPlaylistId) ?: return
        
        findViewById<EditText>(R.id.et_playlist_name).setText(playlist.name)
        
        // Pre-select tracks
        val savedPaths = playlist.trackPaths.toSet()
        val savedIds = allTracks.filter { it.uri in savedPaths }.map { it.id }
        adapter.setSelectedTrackIds(savedIds)
    }

    private fun loadTracks() {
        // Reuse logic from TracksActivity or simplified query
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        try {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)


                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol)
                    val artist = it.getString(artistCol)
                    val path = it.getString(pathCol)
                    // Track(id, title, artist, uri, isActive)
                    allTracks.add(TrackMetadataManager.applyMetadata(this, Track(id, title, artist, path)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading tracks", Toast.LENGTH_SHORT).show()
        }
    }

    private fun savePlaylist() {
        val nameInput = findViewById<EditText>(R.id.et_playlist_name)
        val name = nameInput.text.toString().trim()
        
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a playlist name", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIds = adapter.getSelectedTrackIds()
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one song", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get paths
        val selectedPaths = allTracks.filter { selectedIds.contains(it.id) }.map { it.uri }
        
        if (isEditMode) {
            AppPlaylistManager.updatePlaylist(this, editPlaylistId, name, selectedPaths)
            Toast.makeText(this, "Playlist updated!", Toast.LENGTH_SHORT).show()
        } else {
            AppPlaylistManager.createPlaylist(this, name, selectedPaths)
            Toast.makeText(this, "Playlist created!", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    // MediaStore methods removed
}

