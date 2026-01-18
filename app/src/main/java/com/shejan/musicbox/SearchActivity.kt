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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Setup RecyclerView
        val rvResults = findViewById<RecyclerView>(R.id.rv_search_results)
        rvResults.layoutManager = LinearLayoutManager(this)
        adapter = TrackAdapter(emptyList())
        rvResults.adapter = adapter

        // Load all tracks initially (from service if possible or query again)
        // Since MusicService.playlist might be empty if app just started or cleared, 
        // we might need to query again or rely on what's accessible.
        // For consistency, let's query the device again (fast for meta data) or better, 
        // if MusicService has them, use them. 
        // If TracksActivity ran, MusicService.playlist is populated (static).
        if (MusicService.playlist.isNotEmpty()) {
            allTracks = MusicService.playlist
        } else {
             // Fallback: Query device (simplified version of TracksActivity query)
             // For now, let's assume user visits Tracks first usually, or we can simply query here.
             // Let's rely on MusicService for now to check if it persists.
             // If empty, we might want to trigger a load.
             loadTracks() 
        }

        // Search Input Logic
        val etSearch = findViewById<EditText>(R.id.et_search)
        
        // Focus automatically to keyboard? HTML had autofocus.
        etSearch.requestFocus()
        // window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE) // Optional

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
            
            icon.setColorFilter(getColor(R.color.primary_red))
            text.setTextColor(getColor(R.color.primary_red))
        }
    }
    
    private fun filter(query: String) {
        if (query.isEmpty()) {
            adapter.updateData(emptyList()) // Or show all? HTML design suggests initially empty or recent.
            // Let's show nothing initially as per "opacity-10" area in HTML design implying empty state.
            return
        }
        
        val filtered = allTracks.filter { 
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        adapter.updateData(filtered)
    }

    private fun loadTracks() {
         // simplified query if service list is empty
         val trackList = mutableListOf<Track>()
         try {
             val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DATA
             )
             val selection = "${android.provider.MediaStore.Audio.Media.DURATION} >= 10000"
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
                     if (!path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        trackList.add(Track(it.getLong(idCol), it.getString(titleCol), it.getString(artistCol) ?: "Unknown", path, null, -1L))
                     }
                 }
             }
             allTracks = trackList
         } catch (e: Exception) { e.printStackTrace() }
    }

    private fun setupNav() {
        // Same as other activities
        findViewById<android.view.View>(R.id.nav_home).setOnClickListener {
             startActivity(Intent(this, MainActivity::class.java))
             overridePendingTransition(0, 0)
        }
        
        findViewById<android.view.View>(R.id.nav_folders).setOnClickListener {
             startActivity(Intent(this, FoldersActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_tracks).setOnClickListener {
             startActivity(Intent(this, TracksActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_albums).setOnClickListener {
             startActivity(Intent(this, AlbumsActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_tracks).setOnClickListener {
             startActivity(Intent(this, TracksActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_playlist).setOnClickListener {
             startActivity(Intent(this, PlaylistActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_artists).setOnClickListener {
             startActivity(Intent(this, ArtistsActivity::class.java))
             overridePendingTransition(0, 0)
        }
        // ... others just toast for now or duplicate logic if needed
    }
}
