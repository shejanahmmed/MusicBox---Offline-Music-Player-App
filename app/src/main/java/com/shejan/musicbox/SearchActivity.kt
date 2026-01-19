package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.AnimationUtils

class SearchActivity : AppCompatActivity() {

    private lateinit var adapter: TrackAdapter
    private var allTracks: List<Track> = emptyList()

    // Result Launcher for Artwork
    private var currentEditingTrackId: Long = -1L
    private val pickArtworkLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null && currentEditingTrackId != -1L) {
             TrackArtworkManager.saveArtwork(this, currentEditingTrackId, uri.toString())
             loadTracks() // Refresh list (and allTracks)
             filter(findViewById<EditText>(R.id.et_search).text.toString()) // Re-filter
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Setup RecyclerView
        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        rvResults.layoutManager = LinearLayoutManager(this)
        adapter = TrackAdapter(emptyList()) { track ->
            currentEditingTrackId = track.id
            TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {
                    loadTracks()
                    filter(findViewById<EditText>(R.id.et_search).text.toString())
                }
                override fun onTrackUpdated() {
                    // Update if needed
                }
                override fun onTrackDeleted() {
                    loadTracks()
                    filter(findViewById<EditText>(R.id.et_search).text.toString())
                }
            })
        }
        rvResults.adapter = adapter

        // Load all tracks initially
        if (MusicService.playlist.isNotEmpty()) {
            allTracks = MusicService.playlist
        } else {
             loadTracks() 
        }

        // Search Input Logic
        val etSearch = findViewById<EditText>(R.id.et_search)
        etSearch.requestFocus()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Navigation
        setupNav()
        
        // Set Active State for Search Nav Item (Visually)
        val searchNav = findViewById<android.widget.LinearLayout>(R.id.nav_search)
        if (searchNav != null) {
            val icon = searchNav.getChildAt(0) as android.widget.ImageView
            val text = searchNav.getChildAt(1) as android.widget.TextView
            
            icon.setColorFilter(getColor(R.color.white))
            text.setTextColor(getColor(R.color.white))
        }
    }
    
    private fun filter(query: String) {
        if (query.isEmpty()) {
            adapter.updateData(emptyList()) 
            return
        }
        
        val filtered = allTracks.filter { 
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        adapter.updateData(filtered)
    }

    private fun loadTracks() {
         val trackList = mutableListOf<Track>()
         try {
             val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DATA
             )
             val prefs = getSharedPreferences("MusicBoxPrefs", android.content.Context.MODE_PRIVATE)
             val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
             val minDurationMillis = minDurationSec * 1000
             
             val selection = "${android.provider.MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
             val cursor = contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
             )
             cursor?.use {
                 val idCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                 val titleCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                 val artistCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                 val dataCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                 while (it.moveToNext()) {
                     val path = it.getString(dataCol)
                     // Filter hidden tracks
                     if (!HiddenTracksManager.isHidden(this, path) && !path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        trackList.add(Track(it.getLong(idCol), it.getString(titleCol), it.getString(artistCol) ?: "Unknown", path, null, -1L))
                     }
                 }
             }
             allTracks = trackList
         } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_search)
    }
}
