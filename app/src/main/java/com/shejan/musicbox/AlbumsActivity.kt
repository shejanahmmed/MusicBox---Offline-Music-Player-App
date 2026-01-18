package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AlbumsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_albums)

        setupNav()
        loadAlbums()
    }

    private fun loadAlbums() {
        val list = mutableListOf<Album>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST,
                MediaStore.Audio.Albums.ALBUM_ART
            )
            
            val cursor = contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Albums.ALBUM + " ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
                // ALBUM_ART is deprecated in Q+, but useful for older/simple checks. Uri construction preferred.
                
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(albumCol)
                    val artist = it.getString(artistCol)
                    list.add(Album(id, title, artist, null))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading albums", Toast.LENGTH_SHORT).show()
        }

        val rv = findViewById<RecyclerView>(R.id.rv_albums)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = AlbumAdapter(list) { album ->
             // Open TracksActivity with Album Filter
             // We need to support ALBUM_ID or ALBUM_NAME filter in TracksActivity
             // Currently passing NAME as simpler if unique enough, but ID is safer.
             // Let's pass ID and Name.
             val intent = Intent(this, TracksActivity::class.java)
             intent.putExtra("ALBUM_NAME", album.title) 
             // TracksActivity needs update to handle ALBUM_NAME
             startActivity(intent)
        }
    }

    private fun setupNav() {
        val currentNav = findViewById<android.widget.LinearLayout>(R.id.nav_albums)
        if (currentNav != null) {
            val icon = currentNav.getChildAt(0) as ImageView
            val text = currentNav.getChildAt(1) as TextView
            icon.setColorFilter(getColor(R.color.primary_red))
            text.setTextColor(getColor(R.color.primary_red))
        }

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
        findViewById<android.view.View>(R.id.nav_playlist).setOnClickListener {
             startActivity(Intent(this, PlaylistActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_search).setOnClickListener {
             startActivity(Intent(this, SearchActivity::class.java))
             overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_artists).setOnClickListener {
             startActivity(Intent(this, ArtistsActivity::class.java))
             overridePendingTransition(0, 0)
        }
    }
}
