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
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.MediaStore


class SearchActivity : AppCompatActivity() {

    private lateinit var adapter: TrackAdapter
    private var currentSearchQuery: String = ""

    // Result Launcher for Artwork
    private var currentEditingTrackUri: String? = null
    private val pickArtworkLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null && currentEditingTrackUri != null) {
             try {
                 contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
             } catch (e: Exception) { e.printStackTrace() }
             
             TrackArtworkManager.saveArtwork(this, currentEditingTrackUri!!, uri.toString())
             performSearch(currentSearchQuery)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        rvResults.layoutManager = LinearLayoutManager(this)
        adapter = TrackAdapter(emptyList()) { track ->
            currentEditingTrackUri = track.uri
            TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {
                    performSearch(currentSearchQuery)
                }
                override fun onTrackUpdated() {
                    performSearch(currentSearchQuery)
                }
                override fun onTrackDeleted() {
                    performSearch(currentSearchQuery)
                }
            })
        }
        rvResults.adapter = adapter

        val etSearch = findViewById<EditText>(R.id.et_search)
        etSearch.requestFocus()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().trim()
                performSearch(currentSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        setupNav()
        
        val searchNav = findViewById<android.widget.LinearLayout>(R.id.nav_search)
        if (searchNav != null) {
            val icon = searchNav.getChildAt(0) as android.widget.ImageView
            val text = searchNav.getChildAt(1) as android.widget.TextView
            icon.setColorFilter(getColor(R.color.white))
            text.setTextColor(getColor(R.color.white))
        }
    }
    
    private val allTracks = mutableListOf<Track>()

    override fun onResume() {
        super.onResume()
        setupNav()
        loadAllTracks()
    }

    private fun loadAllTracks() {
        // Run DB Query in Background
        Thread {
            val tempList = mutableListOf<Track>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID
                )
    
                val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
                val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
                val minDurationMillis = minDurationSec * 1000
    
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
    
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
    
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol)
                        if (!HiddenTracksManager.isHidden(this, path) &&
                            !path.lowercase().contains("ringtone") &&
                            !path.lowercase().contains("notification")) {
    
                            val track = Track(
                                cursor.getLong(idCol),
                                cursor.getString(titleCol),
                                cursor.getString(artistCol) ?: "Unknown Artist",
                                path,
                                cursor.getString(albumCol),
                                cursor.getLong(albumIdCol)
                            )
                            // Apply metadata immediately so search works on custom names
                            tempList.add(TrackMetadataManager.applyMetadata(this, track))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            // Update Data on Main Thread
            runOnUiThread {
                allTracks.clear()
                allTracks.addAll(tempList)
                // If user already typed something, re-filter
                if (currentSearchQuery.isNotEmpty()) {
                    performSearch(currentSearchQuery)
                }
            }
        }.start()
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            adapter.updateData(emptyList())
            return
        }

        val filteredList = allTracks.filter { track ->
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true)
        }

        adapter.updateData(filteredList)
    }



    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_search)
    }
}
