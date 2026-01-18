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
        NavUtils.setupNavigation(this, R.id.nav_albums)
    }
}
