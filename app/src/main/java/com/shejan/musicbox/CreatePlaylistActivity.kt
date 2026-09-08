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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge

class CreatePlaylistActivity : AppCompatActivity() {

    private lateinit var adapter: TrackSelectionAdapter
    private val allTracks = mutableListOf<Track>()
    private var editPlaylistId: Long = -1L
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_playlist)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
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
        allTracks.clear()
        allTracks.addAll(MusicRepository.getTracks(this))
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
        
        lifecycleScope.launch(Dispatchers.IO) {
            if (isEditMode) {
                AppPlaylistManager.updatePlaylist(this@CreatePlaylistActivity, editPlaylistId, name, selectedPaths)
            } else {
                AppPlaylistManager.createPlaylist(this@CreatePlaylistActivity, name, selectedPaths)
            }
            
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                Toast.makeText(this@CreatePlaylistActivity, if (isEditMode) "Playlist updated!" else "Playlist created!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // MediaStore methods removed
}




