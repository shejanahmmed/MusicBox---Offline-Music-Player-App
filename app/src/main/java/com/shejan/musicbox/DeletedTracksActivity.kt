package com.shejan.musicbox

import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DeletedTracksActivity : AppCompatActivity() {

    private lateinit var rvDeletedTracks: RecyclerView
    private lateinit var llEmptyState: View
    private val deletedTracks = mutableListOf<Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deleted_tracks)

        // Apply WindowInsets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Back Button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        rvDeletedTracks = findViewById(R.id.rv_deleted_tracks)
        llEmptyState = findViewById(R.id.ll_empty_state)

        rvDeletedTracks.layoutManager = LinearLayoutManager(this)

        loadDeletedTracks()
    }

    private fun loadDeletedTracks() {
        deletedTracks.clear()
        
        val hiddenUris = HiddenTracksManager.getHiddenTracks(this)
        
        if (hiddenUris.isEmpty()) {
            showEmptyState()
            return
        }

        // Query MediaStore for hidden tracks
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    
                    // Only include if it's in the hidden list
                    if (hiddenUris.contains(path)) {
                        val track = Track(
                            id = cursor.getLong(idColumn),
                            title = cursor.getString(titleColumn) ?: "Unknown",
                            artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                            album = cursor.getString(albumColumn) ?: "Unknown Album",
                            uri = path,
                            albumId = cursor.getLong(albumIdColumn)
                        )
                        deletedTracks.add(track)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (deletedTracks.isEmpty()) {
            showEmptyState()
        } else {
            showTrackList()
        }
    }

    private fun showTrackList() {
        rvDeletedTracks.visibility = View.VISIBLE
        llEmptyState.visibility = View.GONE

        val adapter = TrackAdapter(deletedTracks) { track ->
            showRestoreDialog(track)
        }
        rvDeletedTracks.adapter = adapter
    }

    private fun showEmptyState() {
        rvDeletedTracks.visibility = View.GONE
        llEmptyState.visibility = View.VISIBLE
    }

    private fun showRestoreDialog(track: Track) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_restore_track, null)
        dialog.setContentView(view)
        
        // Transparent background for CardView to show
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        // Make dialog wider - 95% of screen width
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        // Set track name in message
        view.findViewById<android.widget.TextView>(R.id.tv_dialog_message).text = 
            "Restore \"${track.title}\" to your library?"
        
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
