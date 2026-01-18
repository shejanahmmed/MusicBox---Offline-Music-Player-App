package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TracksActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_STORAGE = 1001
    private var musicService: MusicService? = null
    private var isBound = false
    
    // Sort State
    private var sortColumn = android.provider.MediaStore.Audio.Media.TITLE
    private var isAscending = true

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateMiniPlayer()
            }
        }
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateMiniPlayer()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)

        // Setup RecyclerView
        val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.layoutManager = LinearLayoutManager(this)

        // Mini Player Click
        findViewById<android.view.View>(R.id.cl_mini_player).setOnClickListener {
             // Do nothing if no track is loaded, or show toast
             if (musicService?.getCurrentTrack() != null) {
                  val track = musicService!!.getCurrentTrack()!!
                  NowPlayingActivity.start(this, track.title, track.artist)
             }
        }

        // Check and Load Tracks
        if (checkPermission()) {
            loadTracks()
        } else {
            requestPermission()
        }
        
        // Sort Button
        findViewById<android.view.View>(R.id.btn_sort).setOnClickListener {
            showSortDialog()
        }
        
        // Header Controls
        findViewById<android.view.View>(R.id.btn_header_shuffle).setOnClickListener {
             if (isBound && musicService != null) {
                 musicService?.toggleShuffle()
                 updateUI()
             }
        }
        
        findViewById<android.view.View>(R.id.btn_header_repeat).setOnClickListener {
             if (isBound && musicService != null) {
                 musicService?.toggleRepeat()
                 updateUI()
                 
                 val mode = MusicService.repeatMode
                 val msg = when (mode) {
                    MusicService.REPEAT_ALL -> "Repeat All"
                    MusicService.REPEAT_ONE -> "Repeat One"
                    else -> "Repeat Off"
                 }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
             }
        }

        // Navigation Logic
        setupNavClick(R.id.nav_home, "Home", true)
        setupNavClick(R.id.nav_albums, "Albums")
        setupNavClick(R.id.nav_folders, "Folders")
        setupNavClick(R.id.nav_artists, "Artists")
        setupNavClick(R.id.nav_playlist, "Playlist")
        setupNavClick(R.id.nav_playlist, "Playlist")
       
        findViewById<android.view.View>(R.id.nav_search).setOnClickListener {
             startActivity(Intent(this, SearchActivity::class.java))
             overridePendingTransition(0, 0)
        }

        // Register Receiver
        val filter = android.content.IntentFilter("MUSIC_BOX_UPDATE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
    }

    private fun updateMiniPlayer() {
        updateUI()
    }

    private fun updateUI() {
        val titleView = findViewById<android.widget.TextView>(R.id.tv_mini_title)
        val artistView = findViewById<android.widget.TextView>(R.id.tv_mini_artist)
        val playButton = findViewById<android.widget.ImageButton>(R.id.btn_mini_play)
        
        // Update Header Controls
        val shuffleBtn = findViewById<android.widget.ImageButton>(R.id.btn_header_shuffle)
        val repeatBtn = findViewById<android.widget.ImageButton>(R.id.btn_header_repeat)
        
        if (MusicService.isShuffleEnabled) {
            shuffleBtn.setColorFilter(getColor(R.color.primary_red))
            shuffleBtn.alpha = 1.0f
        } else {
            shuffleBtn.setColorFilter(getColor(R.color.text_white_opacity_40))
            shuffleBtn.alpha = 1.0f
        }
        
        if (MusicService.repeatMode != MusicService.REPEAT_OFF) {
            repeatBtn.setColorFilter(getColor(R.color.primary_red))
            repeatBtn.alpha = 1.0f
        } else {
            repeatBtn.setColorFilter(getColor(R.color.text_white_opacity_40))
            repeatBtn.alpha = 1.0f
        }
        
        // Attempt to get from service if bound, else fallback to static
        var track: Track? = null
        var isPlaying = false

        if (isBound && musicService != null) {
            track = musicService?.getCurrentTrack()
            isPlaying = musicService?.isPlaying() == true
        } else {
            // Fallback to static if service not bound yet (though onResume should help)
            if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
                track = MusicService.playlist[MusicService.currentIndex]
            }
        }

        if (track != null) {
            titleView.text = track.title
            artistView.text = track.artist
            playButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            // Re-setup Click Listener with correct data
            findViewById<android.view.View>(R.id.cl_mini_player).setOnClickListener {
                NowPlayingActivity.start(this, track.title, track.artist)
            }
            
            playButton.setOnClickListener {
                if (isBound && musicService != null) {
                    if (isPlaying) musicService?.pause() else musicService?.play()
                }
            }
        } else {
             // Handle empty state or hide mini player logic if desired vs keeping dummy default
        }
    }

    private fun checkPermission(): Boolean {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
             android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return androidx.core.content.ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
             android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
             android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_READ_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                loadTracks()
            } else {
                Toast.makeText(this, "Storage permission is required to load songs", Toast.LENGTH_LONG).show()
                // loadDummyData() - Removed
            }
        }
    }

    private fun loadTracks() {
        val showFavoritesOnly = intent.getBooleanExtra("SHOW_FAVORITES", false)
        if (showFavoritesOnly) {
             findViewById<android.widget.TextView>(R.id.tv_header_title)?.text = "FAVORITES"
        }

        val trackList = mutableListOf<Track>()
        val favoriteUris = FavoritesManager.getFavorites(this)
        
        try {
             val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DURATION
             )
             
             // Relaxed filter: just min duration
             val selection = "${android.provider.MediaStore.Audio.Media.DURATION} >= 10000"
             
             val order = if (isAscending) "ASC" else "DESC"
             val sortOrder = "$sortColumn $order"

             val cursor = contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
             )

             cursor?.use {
                 val idColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                 val titleColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                 val artistColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                 val dataColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)

                 while (it.moveToNext()) {
                     val id = it.getLong(idColumn)
                     val title = it.getString(titleColumn)
                     val artist = it.getString(artistColumn) ?: "Unknown Artist"
                     val path = it.getString(dataColumn)
                     
                     // Filter Logic
                     val isFav = favoriteUris.contains(path) // Using path/uri as key
                     
                     if (showFavoritesOnly && !isFav) {
                         continue
                     }

                     if (!path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        trackList.add(Track(id, title, artist, path))
                     }
                 }
             }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading tracks: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        if (trackList.isEmpty()) {
            if (showFavoritesOnly) {
                 Toast.makeText(this, "No favorites added yet", Toast.LENGTH_LONG).show()
            } else {
                 Toast.makeText(this, "No music found on device", Toast.LENGTH_LONG).show()
            }
            // loadDummyData() // Disable dummy data for real usage or keep if preferred.
            // Let's keep empty state handling simple.
             val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
             rvTracks.adapter = TrackAdapter(trackList)
        } else {
            val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
            rvTracks.adapter = TrackAdapter(trackList)
        }
    }

    // private fun loadDummyData() { removed }

    private fun setupNavClick(id: Int, name: String, isHome: Boolean = false) {
        findViewById<android.view.View>(id).setOnClickListener {
            if (isHome) {
                // Navigate back to Main
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
                overridePendingTransition(0, 0) // No animation for "Tab" switch feel
            } else {
                Toast.makeText(this, "Navigate to $name", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showSortDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sort, null)
        dialog.setContentView(view)
        
        (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        val switchAsc = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_ascending)
        val rgOptions = view.findViewById<android.widget.RadioGroup>(R.id.rg_sort_options)
        
        // Set current state
        switchAsc.isChecked = isAscending
        when (sortColumn) {
            android.provider.MediaStore.Audio.Media.TITLE -> rgOptions.check(R.id.rb_title)
            android.provider.MediaStore.Audio.Media.DATE_ADDED -> rgOptions.check(R.id.rb_date_added)
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED -> rgOptions.check(R.id.rb_date_modified)
        }
        
        // Listeners - apply immediately or on dismiss? Usually immediate or "Apply" button.
        // Let's apply on change for immediate feedback.
        
        switchAsc.setOnCheckedChangeListener { _, isChecked ->
            isAscending = isChecked
            loadTracks()
        }
        
        rgOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_title -> sortColumn = android.provider.MediaStore.Audio.Media.TITLE
                R.id.rb_date_added -> sortColumn = android.provider.MediaStore.Audio.Media.DATE_ADDED
                R.id.rb_date_modified -> sortColumn = android.provider.MediaStore.Audio.Media.DATE_MODIFIED
            }
            loadTracks()
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
