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
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge

class DeletedTracksActivity : AppCompatActivity() {

    private lateinit var rvDeletedTracks: RecyclerView
    private lateinit var llEmptyState: View
    private val deletedTracks = mutableListOf<Track>()
    private var adapter: TrackAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_deleted_tracks)

        // Apply WindowInsets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }


        rvDeletedTracks = findViewById(R.id.rv_deleted_tracks)
        llEmptyState = findViewById(R.id.ll_empty_state)

        rvDeletedTracks.layoutManager = LinearLayoutManager(this)

        loadDeletedTracks()
    }

    private fun loadDeletedTracks() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val hiddenUris = HiddenTracksManager.getHiddenTracks(this@DeletedTracksActivity)
            
            if (hiddenUris.isEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                     showEmptyState()
                }
                return@launch
            }
    
            val list = MusicRepository.getHiddenTracks(this@DeletedTracksActivity)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                deletedTracks.clear()
                deletedTracks.addAll(list)

                if (deletedTracks.isEmpty()) {
                    showEmptyState()
                } else {
                    showTrackList()
                }
            }
        }
    }


    private fun showTrackList() {
        rvDeletedTracks.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE

        if (adapter == null) {
            adapter = TrackAdapter(deletedTracks) { track ->
                showRestoreDialog(track)
            }
            rvDeletedTracks.adapter = adapter
        } else {
            adapter?.updateData(deletedTracks)
        }
    }

    private fun showEmptyState() {
        rvDeletedTracks.visibility = View.GONE
        llEmptyState.visibility = View.VISIBLE
    }

    private fun showRestoreDialog(track: Track) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        @android.annotation.SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.dialog_restore_track, null)
        dialog.setContentView(view)
        
        // Transparent background for CardView to show
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Make dialog wider - 95% of screen width
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        // Set track name in message
        val message = getString(R.string.delete_tracks_dialog_restore_message, track.title)
        view.findViewById<android.widget.TextView>(R.id.tv_dialog_message).text = message
        
        // Cancel button
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // Restore button
        view.findViewById<View>(R.id.btn_restore).setOnClickListener {
            dialog.dismiss()
            restoreTrack(track)
        }
        
        dialog.show()
    }

    private fun restoreTrack(track: Track) {
        HiddenTracksManager.restoreTrack(this, track.uri)
        Toast.makeText(this, "Track restored", Toast.LENGTH_SHORT).show()
        
        // Reload list
        loadDeletedTracks()
    }
}




