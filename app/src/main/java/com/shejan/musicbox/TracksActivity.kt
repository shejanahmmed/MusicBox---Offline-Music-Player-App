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
    private var localContentVersion: Long = 0
    
    // Artwork Picker
    private val pickArtworkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            if (currentEditingTrackUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) { e.printStackTrace() }
                
                TrackArtworkManager.saveArtwork(this, currentEditingTrackUri!!, uri.toString())
                updateMiniPlayer() 
                loadTracks()
            }
        }
    }
    
    private var currentEditingTrackUri: String? = null
    private var isEditingPlaylist = false
    private var adapter: TrackAdapter? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateMiniPlayer()
            } else if (intent?.action == "com.shejan.musicbox.TRACK_DELETED") {
                loadTracks()
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
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
        
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        sortColumn = prefs.getString("sort_column", MediaStore.Audio.Media.TITLE) ?: MediaStore.Audio.Media.TITLE
        isAscending = prefs.getBoolean("is_ascending", true)

        val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.layoutManager = LinearLayoutManager(this)



        if (checkPermission()) {
            loadTracks()
        } else {
            requestPermission()
        }
        
        findViewById<View>(R.id.btn_sort).setOnClickListener {
            showSortDialog()
        }
        
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

        MiniPlayerManager.setup(this) { musicService }
        
        NavUtils.setupNavigation(this, getNavId())
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        filter.addAction("com.shejan.musicbox.TRACK_DELETED")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadTracks()
        NavUtils.setupNavigation(this, getNavId())
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {}
    }

    private fun getNavId(): Int {
        return when {
            intent.getBooleanExtra("SHOW_FAVORITES", false) -> R.id.nav_home
            intent.hasExtra("PLAYLIST_ID") -> R.id.nav_playlist
            intent.hasExtra("ALBUM_NAME") -> R.id.nav_albums
            intent.hasExtra("ARTIST_NAME") -> R.id.nav_artists
            else -> R.id.nav_tracks
        }
    }

    override fun onResume() {
        super.onResume()
        if (localContentVersion != MusicUtils.contentVersion) {
            loadTracks()
        }
        updateMiniPlayer()
        NavUtils.setupNavigation(this, getNavId())
        
        if (isEditingPlaylist) {
             loadTracks()
             isEditingPlaylist = false
        }
    }
    
    private fun updateMiniPlayer() {
        updateUI()
    }

    private fun updateUI() {
        val shuffleBtn = findViewById<ImageButton>(R.id.btn_header_shuffle)
        val repeatBtn = findViewById<ImageButton>(R.id.btn_header_repeat)
        
        if (MusicService.isShuffleEnabled) {
            shuffleBtn.setColorFilter(getColor(R.color.primary_red))
        } else {
            shuffleBtn.setColorFilter(getColor(R.color.text_white_opacity_40))
        }
        
        if (MusicService.repeatMode != MusicService.REPEAT_OFF) {
            repeatBtn.setColorFilter(getColor(R.color.primary_red))
        } else {
            repeatBtn.setColorFilter(getColor(R.color.text_white_opacity_40))
        }
        
        MiniPlayerManager.update(this, musicService)

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
            }
        }
    }

    private fun loadTracks() {
        val showFavoritesOnly = intent.getBooleanExtra("SHOW_FAVORITES", false)
        val playlistId = intent.getLongExtra("PLAYLIST_ID", -1L)
        val playlistName = intent.getStringExtra("PLAYLIST_NAME")
        val artistName = intent.getStringExtra("ARTIST_NAME")
        val albumName = intent.getStringExtra("ALBUM_NAME")

        localContentVersion = MusicUtils.contentVersion
        
        // Show loading state if needed (optional)
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val trackList: List<Track> = if (showFavoritesOnly) {
                 val favorites = FavoritesManager.getFavorites(this@TracksActivity)
                 getTracks(null, null).filter { favorites.contains(it.uri) }
            } else if (playlistId != -1L) {
                 getPlaylistTracks(playlistId)
            } else if (artistName != null) {
                 getTracks("${MediaStore.Audio.Media.ARTIST} = ?", arrayOf(artistName))
            } else if (albumName != null) {
                 getTracks("${MediaStore.Audio.Media.ALBUM} = ?", arrayOf(albumName))
            } else {
                 getTracks(null, null)
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                if (showFavoritesOnly) findViewById<TextView>(R.id.tv_header_title)?.text = getString(R.string.title_favorites)
                else if (playlistId != -1L) {
                     val playlist = AppPlaylistManager.getPlaylist(this@TracksActivity, playlistId)
                     val displayName = playlist?.name ?: playlistName ?: "PLAYLIST"
                     findViewById<TextView>(R.id.tv_header_title)?.text = displayName.uppercase()
                     
                     val btnEdit = findViewById<View>(R.id.btn_edit)
                     btnEdit.visibility = View.VISIBLE
                     btnEdit.setOnClickListener {
                         val intent = Intent(this@TracksActivity, CreatePlaylistActivity::class.java)
                         intent.putExtra("EDIT_PLAYLIST_ID", playlistId)
                         intent.putExtra("PLAYLIST_NAME", playlistName)
                         isEditingPlaylist = true
                         startActivity(intent)
                     }
                } else if (artistName != null) findViewById<TextView>(R.id.tv_header_title)?.text = artistName.uppercase()
                else if (albumName != null) findViewById<TextView>(R.id.tv_header_title)?.text = albumName.uppercase()
                else findViewById<TextView>(R.id.tv_header_title)?.text = getString(R.string.tab_tracks).uppercase()

                if (trackList.isEmpty()) {
                    val msg = when {
                        showFavoritesOnly -> getString(R.string.msg_no_favorites)
                        playlistId != -1L -> getString(R.string.msg_playlist_empty)
                        artistName != null -> getString(R.string.msg_no_artist_tracks)
                        else -> getString(R.string.msg_no_music)
                    }
                    Toast.makeText(this@TracksActivity, msg, Toast.LENGTH_LONG).show()
                }
                
                findViewById<TextView>(R.id.tv_tracks_count)?.text = if (trackList.size == 1) "1 Song" else "${trackList.size} Songs"

                val rvTracks = findViewById<RecyclerView>(R.id.rv_tracks)
                if (adapter == null) {
                    adapter = TrackAdapter(trackList) { track ->
                        showTrackOptionsDialog(track)
                    }
                    rvTracks.adapter = adapter
                } else {
                    adapter?.updateData(trackList)
                }
            }
        }
    }

    private fun getTracks(selection: String?, selectionArgs: Array<String>?): List<Track> {
        // This function is now just a helper for reading DB, called by loadTracks background thread.
        // Or better, we keep getTracks compliant with synchronous if needed, but loadTracks does the threading.
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
             
             // Base criteria: is_music and minimum duration
             val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
             val finalSelection = if (selection != null) "($baseSelection) AND ($selection)" else baseSelection
             
             val order = if (isAscending) "ASC" else "DESC"
             val sortOrder = "$sortColumn $order"

             val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                finalSelection,
                selectionArgs,
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
                     val title = it.getString(titleColumn) ?: "Unknown"
                     val artist = it.getString(artistColumn) ?: "Unknown Artist"
                     val path = it.getString(dataColumn) ?: continue
                     val album = it.getString(albumColumn)
                     val albumId = it.getLong(albumIdColumn)
                     
                     if (!HiddenTracksManager.isHidden(this, path) && 
                         !path.lowercase().contains("ringtone") && 
                         !path.lowercase().contains("notification")) {
                        list.add(TrackMetadataManager.applyMetadata(this, Track(id, title, artist, path, album, albumId)))
                     }
                 }
             }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun getPlaylistTracks(playlistId: Long): List<Track> {
        val playlist = AppPlaylistManager.getPlaylist(this, playlistId) ?: return emptyList()
        val allTracks = getTracks(null, null)
        val trackMap = allTracks.associateBy { it.uri }
        return playlist.trackPaths.mapNotNull { trackMap[it] }
    }

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
        
        fun updateSelection(selectedRb: RadioButton) {
            rbTitle.isChecked = false
            rbDateAdded.isChecked = false
            rbDateModified.isChecked = false
            selectedRb.isChecked = true
        }

        switchAsc.isChecked = isAscending
        when (sortColumn) {
            MediaStore.Audio.Media.TITLE -> updateSelection(rbTitle)
            MediaStore.Audio.Media.DATE_ADDED -> updateSelection(rbDateAdded)
            MediaStore.Audio.Media.DATE_MODIFIED -> updateSelection(rbDateModified)
        }
        
        fun saveSortPrefs() {
            val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
            prefs.edit {
                putString("sort_column", sortColumn)
                putBoolean("is_ascending", isAscending)
            }
        }
    
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
        currentEditingTrackUri = track.uri
        TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
            override fun onArtworkChanged() {
                loadTracks()
                updateMiniPlayer()
            }
            override fun onTrackUpdated() {
               loadTracks()
            }
            override fun onTrackDeleted() {
                loadTracks()
            }
        })
    }
}
