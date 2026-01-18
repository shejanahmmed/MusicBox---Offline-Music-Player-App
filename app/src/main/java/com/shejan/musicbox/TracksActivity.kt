package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.activity.result.contract.ActivityResultContracts

class TracksActivity : AppCompatActivity() {

    private val REQUEST_CODE_READ_STORAGE = 1001
    private var musicService: MusicService? = null
    private var isBound = false
    
    // Sort State
    private var sortColumn = android.provider.MediaStore.Audio.Media.TITLE
    private var isAscending = true
    
    // Artwork Picker
    private val pickArtworkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        if (uri != null) {
            // We need to know which track was being edited. 
            // Since launcher is async, we need a standard way to track the 'pending' track ID.
            // Or, we can use a simpler approach: The Dialog holds the track. 
            // But the dialog dismisses.
            // Actually, we can store 'trackIdForArtwork' in a var.
            if (currentEditingTrackId != -1L) {
                TrackArtworkManager.saveArtwork(this, currentEditingTrackId, uri.toString())
                // Refresh specific item or whole list? 
                // loadTracks() might be heavy. Adapter notifyItemChanged would be better if we had position.
                // For now, loadTracks() or finding ViewHolder.
                
                // Also update Mini Player if it matches
                updateMiniPlayer() 
                
                // Refresh list to show new art
                loadTracks()
            }
        }
    }
    
    private var currentEditingTrackId: Long = -1L

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
        NavUtils.setupNavigation(this, R.id.nav_tracks)

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

            // Load Mini Player Artwork
            val ivMiniArt = findViewById<android.widget.ImageView>(R.id.iv_mini_art)
            if (ivMiniArt != null) {
                MusicUtils.loadTrackArt(this, track.id, track.albumId, ivMiniArt)
            }
            
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
        val playlistId = intent.getLongExtra("PLAYLIST_ID", -1L)
        val playlistName = intent.getStringExtra("PLAYLIST_NAME")
        val artistName = intent.getStringExtra("ARTIST_NAME")
        val albumName = intent.getStringExtra("ALBUM_NAME")

        val trackList = mutableListOf<Track>()

        if (showFavoritesOnly) {
             findViewById<android.widget.TextView>(R.id.tv_header_title)?.text = "FAVORITES"
             val favs = FavoritesManager.getFavorites(this)
             trackList.addAll(getAllTracks().filter { favs.contains(it.uri) })
        } else if (playlistId != -1L) {
             findViewById<android.widget.TextView>(R.id.tv_header_title)?.text = playlistName?.uppercase() ?: "PLAYLIST"
             trackList.addAll(getPlaylistTracks(playlistId))
        } else if (artistName != null) {
             findViewById<android.widget.TextView>(R.id.tv_header_title)?.text = artistName.uppercase()
             trackList.addAll(getAllTracks().filter { it.artist.equals(artistName, ignoreCase = true) })
        } else if (albumName != null) {
             findViewById<android.widget.TextView>(R.id.tv_header_title)?.text = albumName.uppercase()
             // Use regex or contains for looser matching if needed, but exact is safet from MediaStore
             trackList.addAll(getAllTracks().filter { it.album?.equals(albumName, ignoreCase = true) == true }) // Need album logic in Track or query
             // Wait, Track model doesn't have album field yet? Let's check.
             // If not, we need to add it or load it.
             // Optimization: We load AllTracks then filter. Check getAllTracks() to see if it fetches Album.
             // It fetches: ID, TITLE, ARTIST, DATA. No Album.
             // We need to update getAllTracks to fetch Album too.
        } else {
             // Load All
             trackList.addAll(getAllTracks())
        }

        if (trackList.isEmpty()) {
            if (showFavoritesOnly) {
                 Toast.makeText(this, "No favorites added yet", Toast.LENGTH_LONG).show()
            } else if (playlistId != -1L) {
                 Toast.makeText(this, "Playlist is empty", Toast.LENGTH_LONG).show()
            } else if (artistName != null) {
                 Toast.makeText(this, "No tracks found for this artist", Toast.LENGTH_LONG).show()
            } else {
                 Toast.makeText(this, "No music found on device", Toast.LENGTH_LONG).show()
            }
        }
        
        val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.adapter = TrackAdapter(trackList) { track ->
            showTrackOptionsDialog(track)
        }
    }

    private fun getAllTracks(): List<Track> {
        val list = mutableListOf<Track>()
        try {
             val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.ALBUM,
                android.provider.MediaStore.Audio.Media.ALBUM_ID
             )
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
                 val albumColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                 val albumIdColumn = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)

                 while (it.moveToNext()) {
                     val id = it.getLong(idColumn)
                     val title = it.getString(titleColumn)
                     val artist = it.getString(artistColumn) ?: "Unknown Artist"
                     val path = it.getString(dataColumn)
                     val album = it.getString(albumColumn)
                     val albumId = it.getLong(albumIdColumn)
                     
                     val hidden = HiddenTracksManager.isHidden(this, path)
                     if (!hidden && !path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        list.add(Track(id, title, artist, path, album, albumId))
                     }
                 }
             }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun getPlaylistTracks(playlistId: Long): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val uri = android.provider.MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Playlists.Members.AUDIO_ID,
                android.provider.MediaStore.Audio.Playlists.Members.TITLE,
                android.provider.MediaStore.Audio.Playlists.Members.ARTIST,
                android.provider.MediaStore.Audio.Playlists.Members.DATA
            )
            val cursor = contentResolver.query(uri, projection, null, null, android.provider.MediaStore.Audio.Playlists.Members.PLAY_ORDER)
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists.Members.AUDIO_ID)
                val titleCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists.Members.TITLE)
                val artistCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists.Members.ARTIST)
                val pathCol = it.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists.Members.DATA)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol)
                    val artist = it.getString(artistCol)
                    val path = it.getString(pathCol)
                    // Playlist members query is limited. For full details we often query Media table by ID. 
                    // For now, pass -1L which triggers fallback to loadTrackArt (by id) which works fine.
                    list.add(Track(id, title, artist, path, null, -1L))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    // private fun loadDummyData() { removed }

    // private fun setupNavClick(id: Int, name: String, isHome: Boolean = false) { }
    
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

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun showTrackOptionsDialog(track: Track) {
        currentEditingTrackId = track.id // Helper for result launcher
        
        TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
            override fun onArtworkChanged() {
                loadTracks()
                updateMiniPlayer()
            }
            override fun onTrackUpdated() {
               loadTracks() // Refresh for favorites
            }
            override fun onTrackDeleted() {
                loadTracks()
            }
        })
    }
}
