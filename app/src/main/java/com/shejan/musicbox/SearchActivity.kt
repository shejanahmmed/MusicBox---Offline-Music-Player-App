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

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.provider.MediaStore


class SearchActivity : AppCompatActivity() {

    private lateinit var adapter: TrackAdapter
    private var currentSearchQuery: String = ""
    private lateinit var tvSearchCount: android.widget.TextView

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
        enableEdgeToEdge()
        setContentView(R.layout.activity_search)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        tvSearchCount = findViewById(R.id.tv_search_count)

        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        rvResults.layoutManager = LinearLayoutManager(this)
        adapter = TrackAdapter(emptyList()) { track ->
            currentEditingTrackUri = track.uri
            TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {
                    loadAllTracks()
                }
                override fun onTrackUpdated() {
                    loadAllTracks()
                }
                override fun onTrackDeleted() {
                    loadAllTracks()
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
            icon.setColorFilter(getColor(R.color.colorNavSelected))
            text.setTextColor(getColor(R.color.colorNavSelected))
        }
    }
    
    private val allTracks = mutableListOf<Track>()

    override fun onResume() {
        super.onResume()
        setupNav()
        loadAllTracks()
    }

    private fun loadAllTracks() {
        val appContext = applicationContext
        // Run DB Query in Background
        lifecycleScope.launch(Dispatchers.IO) {
            val tempList = mutableListOf<Track>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID
                )
                val prefs = appContext.getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
                val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
                val minDurationMillis = minDurationSec * 1000
             
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
             
                val cursor = appContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )

                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val title = it.getString(titleColumn) ?: "Unknown"
                        val artist = it.getString(artistColumn) ?: "Unknown Artist"
                        val path = it.getString(dataColumn) ?: continue
                        val album = it.getString(albumColumn)
                        val albumId = it.getLong(albumIdColumn)
                     
                        if (!HiddenTracksManager.isHidden(appContext, path) && 
                            !path.lowercase().contains("ringtone") && 
                            !path.lowercase().contains("notification")) {
                            tempList.add(TrackMetadataManager.applyMetadata(appContext, Track(id, title, artist, path, album, albumId)))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            withContext(Dispatchers.Main) {
                allTracks.clear()
                allTracks.addAll(tempList)
                // If user already typed something, re-filter
                if (currentSearchQuery.isNotEmpty()) {
                    performSearch(currentSearchQuery)
                } else {
                    val currentCount = allTracks.size
                    tvSearchCount.text = if (currentCount == 1) "Search from 1 track" else "Search from $currentCount tracks"
                }
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null
    
    private fun performSearch(query: String) {
        // Cancel previous search job if active
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            adapter.updateData(emptyList())
            val totalCount = allTracks.size
            tvSearchCount.text = if (totalCount == 1) "Search from 1 track" else "Search from $totalCount tracks"
            return
        }

        searchJob = lifecycleScope.launch(Dispatchers.Default) {
            val filteredList = allTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true)
            }
            
            withContext(Dispatchers.Main) {
                adapter.updateData(filteredList)
                val foundCount = filteredList.size
                tvSearchCount.text = if (foundCount == 1) "1 track found" else "$foundCount tracks found"
            }
        }
    }


    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_search)
    }
}



