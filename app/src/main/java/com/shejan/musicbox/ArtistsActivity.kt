package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ArtistsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artists)

        setupNav()
        loadArtists()
        

    }

    private fun loadArtists() {
        val list = mutableListOf<Artist>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
            )
            
            val cursor = contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Artists.ARTIST + " ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
                val countCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    val count = it.getInt(countCol)
                    list.add(Artist(id, name, count))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading artists", Toast.LENGTH_SHORT).show()
        }

        val rv = findViewById<RecyclerView>(R.id.rv_artists)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = ArtistAdapter(list) { artist ->
             // On Click: Open TracksActivity with Artist Filter (implement later if needed, mostly requested UI for now)
             // For now just toast or open Tracks with filter if supported
             // We can pass ARTIST_NAME to TracksActivity to filter
             val intent = Intent(this, TracksActivity::class.java)
             intent.putExtra("ARTIST_NAME", artist.name) // need to handle this in TracksActivity
             startActivity(intent)
        }
    }

    private fun setupNav() {
        // IDs: nav_home, nav_search, nav_profile(center), nav_library(tracks?), nav_settings
        // Wait, the user layout has: "home", "search", "person", "library_music", "settings".
        // My app uses: Home, Tracks, Albums, Folders, Artists, Playlist, Search.
        // I need to stick to the ID convention I used in layouts: layout_bottom_nav.xml
        
        // Let's highlight "Artists"
        val currentNav = findViewById<android.widget.LinearLayout>(R.id.nav_artists)
        if (currentNav != null) {
            val icon = currentNav.getChildAt(0) as ImageView
            val text = currentNav.getChildAt(1) as TextView
            icon.setColorFilter(getColor(R.color.primary_red))
            text.setTextColor(getColor(R.color.primary_red))
        }

        // Listeners for others
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
        findViewById<android.view.View>(R.id.nav_playlist).setOnClickListener {
             startActivity(Intent(this, PlaylistActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_search).setOnClickListener {
             startActivity(Intent(this, SearchActivity::class.java))
             overridePendingTransition(0, 0)
        }
    }
}
