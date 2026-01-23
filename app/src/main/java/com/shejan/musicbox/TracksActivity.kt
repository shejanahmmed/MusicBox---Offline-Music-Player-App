package com.shejan.musicbox

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.annotation.SuppressLint
import android.view.View
import androidx.core.content.edit
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

class TracksActivity : AppCompatActivity() {

    private val requestCodeReadStorage = 1001
    private var musicService: MusicService? = null
    private var isBound = false
    
    // Sort State
    private var sortColumn = MediaStore.Audio.Media.TITLE
    private var isAscending = true
    
    // Artwork Picker
    private val pickArtworkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
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
    private var isEditingPlaylist = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateMiniPlayer()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateMiniPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
        
        // Restore Sort State from SharedPreferences
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        sortColumn = prefs.getString("sort_column", MediaStore.Audio.Media.TITLE) ?: MediaStore.Audio.Media.TITLE
        isAscending = prefs.getBoolean("is_ascending", true)

        // Setup RecyclerView
        val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.layoutManager = LinearLayoutManager(this)

        // Mini Player Click
        findViewById<View>(R.id.cl_mini_player).setOnClickListener {
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
        findViewById<View>(R.id.btn_sort).setOnClickListener {
            showSortDialog()
        }
        
        // Header Controls
        findViewById<View>(R.id.btn_header_shuffle).setOnClickListener {
             if (isBound && musicService != null) {
                 musicService?.toggleShuffle()
                 updateUI()
             }
        }
        
        findViewById<View>(R.id.btn_header_repeat).setOnClickListener {
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
        


        findViewById<View>(R.id.btn_mini_next)?.setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            intent.action = MusicService.ACTION_NEXT
            startService(intent)
        }

        findViewById<View>(R.id.btn_mini_prev)?.setOnClickListener {
            val intent = Intent(this, MusicService::class.java)
            intent.action = MusicService.ACTION_PREV
            startService(intent)
        }
        


        // Navigation Logic
        NavUtils.setupNavigation(this, getNavId())
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
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
    }

    private fun getNavId(): Int {
        return when {
            intent.hasExtra("PLAYLIST_ID") -> R.id.nav_playlist
            intent.hasExtra("ALBUM_NAME") -> R.id.nav_albums
            intent.hasExtra("ARTIST_NAME") -> R.id.nav_artists
            else -> R.id.nav_tracks
        }
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, getNavId())
        
        // Reload if coming back from edit
        if (isEditingPlaylist) {
             loadTracks()
             isEditingPlaylist = false
        }
        
        // Register Receiver
        // Register Receiver
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    private fun updateMiniPlayer() {
        updateUI()
    }

    private fun updateUI() {
        // Update Header Controls
        val shuffleBtn = findViewById<ImageButton>(R.id.btn_header_shuffle)
        val repeatBtn = findViewById<ImageButton>(R.id.btn_header_repeat)
        
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
        
        // Update Mini Player
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this) { musicService }

        // Update List Active State
        var track: Track? = null
        if (isBound && musicService != null) {
            track = musicService?.getCurrentTrack()
        } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
            track = MusicService.playlist[MusicService.currentIndex]
        }
        
        val adapter = findViewById<RecyclerView>(R.id.rv_tracks).adapter as? TrackAdapter
        adapter?.updateActiveTrack(track?.id ?: -1L)
    }

    private fun checkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             Manifest.permission.READ_MEDIA_AUDIO
        } else {
             Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             Manifest.permission.READ_MEDIA_AUDIO
        } else {
             Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCodeReadStorage)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeReadStorage) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadTracks()
            } else {
                Toast.makeText(this, getString(R.string.msg_storage_permission), Toast.LENGTH_LONG).show()
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
             findViewById<TextView>(R.id.tv_header_title)?.text = getString(R.string.title_favorites)
             val favorites = FavoritesManager.getFavorites(this)
             trackList.addAll(getAllTracks().filter { favorites.contains(it.uri) })
        } else if (playlistId != -1L) {
             // Fetch latest name from manager, fallback to intent
             val playlist = AppPlaylistManager.getPlaylist(this, playlistId)
             val displayName = playlist?.name ?: playlistName ?: "PLAYLIST"
             
             findViewById<TextView>(R.id.tv_header_title)?.text = displayName.uppercase()
             trackList.addAll(getPlaylistTracks(playlistId))
             
             // Show Edit Button
             val btnEdit = findViewById<View>(R.id.btn_edit)
             btnEdit.visibility = View.VISIBLE
             btnEdit.setOnClickListener {
                 val intent = Intent(this, CreatePlaylistActivity::class.java)
                 intent.putExtra("EDIT_PLAYLIST_ID", playlistId)
                 intent.putExtra("PLAYLIST_NAME", playlistName)
                 isEditingPlaylist = true
                 startActivity(intent)
             }
        } else if (artistName != null) {
             findViewById<TextView>(R.id.tv_header_title)?.text = artistName.uppercase()
             trackList.addAll(getAllTracks().filter { it.artist.equals(artistName, ignoreCase = true) })
        } else if (albumName != null) {
             findViewById<TextView>(R.id.tv_header_title)?.text = albumName.uppercase()
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
                 Toast.makeText(this, getString(R.string.msg_no_favorites), Toast.LENGTH_LONG).show()
            } else if (playlistId != -1L) {
                 Toast.makeText(this, getString(R.string.msg_playlist_empty), Toast.LENGTH_LONG).show()
            } else if (artistName != null) {
                 Toast.makeText(this, getString(R.string.msg_no_artist_tracks), Toast.LENGTH_LONG).show()
            } else {
                 Toast.makeText(this, getString(R.string.msg_no_music), Toast.LENGTH_LONG).show()
            }
        }
        
        // Update Count
        findViewById<TextView>(R.id.tv_tracks_count)?.text = if (trackList.size == 1) "1 Song" else "${trackList.size} Songs"

        val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.adapter = TrackAdapter(trackList) { track ->
            showTrackOptionsDialog(track)
        }
    }

    private fun getAllTracks(): List<Track> {
        val list = mutableListOf<Track>()
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
             val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
             val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
             val minDurationMillis = minDurationSec * 1000
             
             val selection = "${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
             val order = if (isAscending) "ASC" else "DESC"
             val sortOrder = "$sortColumn $order"

             val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null, // selectionArgs not needed for simple integer comparison in string
                sortOrder
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
                     val title = it.getString(titleColumn)
                     val artist = it.getString(artistColumn) ?: "Unknown Artist"
                     val path = it.getString(dataColumn)
                     val album = it.getString(albumColumn)
                     val albumId = it.getLong(albumIdColumn)
                     
                     val hidden = HiddenTracksManager.isHidden(this, path)
                     if (!hidden && !path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        list.add(TrackMetadataManager.applyMetadata(this, Track(id, title, artist, path, album, albumId)))
                     }
                 }
             }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    @Suppress("DEPRECATION")
    private fun getPlaylistTracks(playlistId: Long): List<Track> {
        val playlist = AppPlaylistManager.getPlaylist(this, playlistId) ?: return emptyList()
        val allTracks = getAllTracks()
        
        // Map paths to tracks to preserve order and get metadata
        val trackMap = allTracks.associateBy { it.uri }
        return playlist.trackPaths.mapNotNull { trackMap[it] }
    }

    // private fun loadDummyData() { removed }

    // private fun setupNavClick(id: Int, name: String, isHome: Boolean = false) { }
    
    @SuppressLint("InflateParams")
    private fun showSortDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sort, null)
        dialog.setContentView(view)
        
        view.post {
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        }
        
        val switchAsc = view.findViewById<SwitchMaterial>(R.id.switch_ascending)
        
        val containerTitle = view.findViewById<View>(R.id.container_title)
        val containerDateAdded = view.findViewById<View>(R.id.container_date_added)
        val containerDateModified = view.findViewById<View>(R.id.container_date_modified)
        
        val rbTitle = view.findViewById<RadioButton>(R.id.rb_title)
        val rbDateAdded = view.findViewById<RadioButton>(R.id.rb_date_added)
        val rbDateModified = view.findViewById<RadioButton>(R.id.rb_date_modified)
        
        // Helper to update UI
        fun updateSelection(selectedRb: RadioButton) {
            rbTitle.isChecked = false
            rbDateAdded.isChecked = false
            rbDateModified.isChecked = false
            selectedRb.isChecked = true
        }

        // Set current state
        switchAsc.isChecked = isAscending
        when (sortColumn) {
            MediaStore.Audio.Media.TITLE -> updateSelection(rbTitle)
            MediaStore.Audio.Media.DATE_ADDED -> updateSelection(rbDateAdded)
            MediaStore.Audio.Media.DATE_MODIFIED -> updateSelection(rbDateModified)
        }
        
        // Helper to save prefs
    fun saveSortPrefs() {
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("sort_column", sortColumn)
            putBoolean("is_ascending", isAscending)
        }
    }
    
    // Listeners
    switchAsc.setOnCheckedChangeListener { _, isChecked ->
        isAscending = isChecked
        saveSortPrefs()
        loadTracks()
    }
    
    containerTitle.setOnClickListener {
        updateSelection(rbTitle)
        sortColumn = MediaStore.Audio.Media.TITLE
        saveSortPrefs()
        loadTracks()
        dialog.dismiss()
    }
    
    containerDateAdded.setOnClickListener {
        updateSelection(rbDateAdded)
        sortColumn = MediaStore.Audio.Media.DATE_ADDED
        saveSortPrefs()
        loadTracks()
        dialog.dismiss()
    }
    
    containerDateModified.setOnClickListener {
        updateSelection(rbDateModified)
        sortColumn = MediaStore.Audio.Media.DATE_MODIFIED
        saveSortPrefs()
        loadTracks()
        dialog.dismiss()
    }
        
    dialog.show()
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
