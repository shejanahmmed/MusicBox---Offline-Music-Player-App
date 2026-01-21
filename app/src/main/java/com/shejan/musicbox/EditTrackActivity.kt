package com.shejan.musicbox

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EditTrackActivity : AppCompatActivity() {

    private lateinit var track: Track
    private var currentTitle: String = ""
    private var currentArtist: String = ""
    private var currentAlbum: String = ""
    private var currentYear: String = ""
    private var fileTitle: String = ""
    private var fileArtist: String = ""
    private var fileAlbum: String = ""

    companion object {
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_TRACK_TITLE = "track_title"
        const val EXTRA_TRACK_ARTIST = "track_artist"
        const val EXTRA_TRACK_ALBUM = "track_album"
        const val EXTRA_TRACK_YEAR = "track_year"
        const val EXTRA_ALBUM_ID = "album_id"
        const val EXTRA_FILE_TITLE = "file_title"
        const val EXTRA_FILE_ARTIST = "file_artist"
        const val EXTRA_FILE_ALBUM = "file_album"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_track)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Get track data from intent
        val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1)
        currentTitle = intent.getStringExtra(EXTRA_TRACK_TITLE) ?: ""
        currentArtist = intent.getStringExtra(EXTRA_TRACK_ARTIST) ?: ""
        currentAlbum = intent.getStringExtra(EXTRA_TRACK_ALBUM) ?: ""
        currentYear = intent.getStringExtra(EXTRA_TRACK_YEAR) ?: ""
        fileTitle = intent.getStringExtra(EXTRA_FILE_TITLE) ?: currentTitle
        fileArtist = intent.getStringExtra(EXTRA_FILE_ARTIST) ?: currentArtist
        fileAlbum = intent.getStringExtra(EXTRA_FILE_ALBUM) ?: currentAlbum
        val albumId = intent.getLongExtra(EXTRA_ALBUM_ID, -1)

        if (trackId == -1L) {
            Toast.makeText(this, "Error loading track", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Create track object (id, title, artist, uri, album, albumId, isActive)
        track = Track(trackId, currentTitle, currentArtist, "", currentAlbum, albumId, false)

        // Load album art
        val ivAlbumArt = findViewById<android.widget.ImageView>(R.id.iv_album_art)
        if (albumId != -1L) {
            MusicUtils.loadAlbumArt(this, albumId, ivAlbumArt)
        }

        // Get EditText fields
        val etTrackName = findViewById<EditText>(R.id.et_track_name)
        val etArtistName = findViewById<EditText>(R.id.et_artist_name)
        val etAlbumName = findViewById<EditText>(R.id.et_album_name)
        val etYear = findViewById<EditText>(R.id.et_year)

        // Display current values (already includes custom metadata if available)
        etTrackName.setText(currentTitle)
        etArtistName.setText(currentArtist)
        etAlbumName.setText(currentAlbum)
        etYear.setText(currentYear)

        // Reset button - restore original file metadata
        findViewById<ImageButton>(R.id.btn_reset).setOnClickListener {
            etTrackName.setText(fileTitle)
            etArtistName.setText(fileArtist)
            etAlbumName.setText(fileAlbum)
            etYear.setText("")
            Toast.makeText(this, "Reset to original file metadata", Toast.LENGTH_SHORT).show()
        }

        // Cancel button
        findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener {
            finish()
        }

        // Save button
        findViewById<ImageButton>(R.id.btn_save).setOnClickListener {
            val newTitle = etTrackName.text.toString().trim()
            val newArtist = etArtistName.text.toString().trim()
            val newAlbum = etAlbumName.text.toString().trim()
            val newYear = etYear.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "Track name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save metadata
            TrackMetadataManager.saveMetadata(
                this,
                trackId,
                newTitle,
                newArtist.takeIf { it.isNotEmpty() },
                newAlbum.takeIf { it.isNotEmpty() },
                newYear.takeIf { it.isNotEmpty() }
            )

            Toast.makeText(this, "Metadata saved", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }
}
