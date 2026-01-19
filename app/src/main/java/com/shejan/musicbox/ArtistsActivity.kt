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

import android.content.ServiceConnection
import android.content.BroadcastReceiver

import android.content.ComponentName
import android.os.IBinder
import android.content.Context
import android.content.IntentFilter
import android.os.Build

class ArtistsActivity : AppCompatActivity() {

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@ArtistsActivity, musicService)
            MiniPlayerManager.setup(this@ArtistsActivity, musicService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                MiniPlayerManager.update(this@ArtistsActivity, musicService)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(receiver)
    }

    override fun onResume() {
        super.onResume()
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this, musicService)
        NavUtils.setupNavigation(this, R.id.nav_artists)
    }

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
        NavUtils.setupNavigation(this, R.id.nav_artists)
    }
}
