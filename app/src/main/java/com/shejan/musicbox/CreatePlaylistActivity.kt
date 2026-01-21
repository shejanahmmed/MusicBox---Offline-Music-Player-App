package com.shejan.musicbox

import android.content.ContentValues
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

class CreatePlaylistActivity : AppCompatActivity() {

    private lateinit var adapter: TrackSelectionAdapter
    private val allTracks = mutableListOf<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_playlist)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        loadTracks()

        val rv = findViewById<RecyclerView>(R.id.rv_track_selection)
        adapter = TrackSelectionAdapter(allTracks) { _, _ -> }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_save_playlist).setOnClickListener {
            savePlaylist()
        }
    }

    private fun loadTracks() {
        // Reuse logic from TracksActivity or simplified query
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        try {
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)


                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol)
                    val artist = it.getString(artistCol)
                    val path = it.getString(pathCol)
                    // Track(id, title, artist, uri, isActive)
                    allTracks.add(TrackMetadataManager.applyMetadata(this, Track(id, title, artist, path)))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading tracks", Toast.LENGTH_SHORT).show()
        }
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

        val playlistId = createPlaylist(name)
        if (playlistId != -1L) {
            addToPlaylist(playlistId, selectedIds)
            Toast.makeText(this, "Playlist created!", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Failed to create playlist", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun createPlaylist(name: String): Long {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Playlists.NAME, name)
            put(MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Audio.Playlists.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        return try {
            val uri = contentResolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
            uri?.lastPathSegment?.toLong() ?: -1L
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    @Suppress("DEPRECATION")
    private fun addToPlaylist(playlistId: Long, trackIds: List<Long>) {
        val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
        // Bulk insert or loop
        // Loop is safer for order usually, but bulk is faster. Let's do loop to ensure order if we added that feature later.
        // Actually, simple insert is fine.
        
        var baseOrder = 1
        trackIds.forEach { trackId ->
            val values = ContentValues().apply {
                put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, baseOrder++)
                put(MediaStore.Audio.Playlists.Members.AUDIO_ID, trackId)
            }
            try {
                contentResolver.insert(uri, values)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
