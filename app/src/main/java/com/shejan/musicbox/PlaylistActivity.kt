package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageView
import android.widget.TextView

class PlaylistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        setupNav()

        // Setup Header Add Button
        findViewById<android.view.View>(R.id.btn_add_playlist).setOnClickListener {
            // Launch Create Playlist Activity
            startActivity(Intent(this, CreatePlaylistActivity::class.java))
        }

        // Initial Load
        loadPlaylists()
        updateTopCards()
        
    }

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@PlaylistActivity, musicService)
            MiniPlayerManager.setup(this@PlaylistActivity, musicService)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                MiniPlayerManager.update(this@PlaylistActivity, musicService)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
        
        val filter = android.content.IntentFilter("MUSIC_BOX_UPDATE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
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
        loadPlaylists()
        updateTopCards()
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this, musicService)
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }

    private fun updateTopCards() {
         val systemPlaylists = getSystemPlaylists()
         findViewById<TextView>(R.id.tv_playlists_count).text = "${systemPlaylists.size} Lists"
    }

    // setupTopCards removed as it only set listener on removed view

    private fun loadPlaylists() {
        val list = mutableListOf<PlaylistItem>()

        // 2. User Playlists from System
        list.addAll(getSystemPlaylists())

        val rv = findViewById<RecyclerView>(R.id.rv_playlists)
        val emptyView = findViewById<TextView>(R.id.tv_empty_state)
        
        if (list.isEmpty()) {
            rv.visibility = android.view.View.GONE
            emptyView.visibility = android.view.View.VISIBLE
        } else {
            rv.visibility = android.view.View.VISIBLE
            emptyView.visibility = android.view.View.GONE
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(list) { item ->
             val intent = Intent(this, TracksActivity::class.java)
             intent.putExtra("PLAYLIST_ID", item.id)
             intent.putExtra("PLAYLIST_NAME", item.name)
             startActivity(intent)
        }
    }

    private fun getRecentlyAddedCount(): Int {
        var count = 0
        try {
            // Count tracks added in last 30 days
            val oneMonthAgo = (System.currentTimeMillis() / 1000) - (30 * 24 * 60 * 60)
            val cursor = contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATE_ADDED} > ?",
                arrayOf(oneMonthAgo.toString()),
                null
            )
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) { e.printStackTrace() }
        return count
    }

    private fun getSystemPlaylists(): List<PlaylistItem> {
        val playlists = mutableListOf<PlaylistItem>()
        try {
            val cursor = contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME),
                null, null, null
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Playlists._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol)
                    // Get count for this playlist (requires another query usually, simplified here for speed)
                    // Let's approximate count or query members
                    val count = getPlaylistCount(id)
                    playlists.add(PlaylistItem(id, name, count))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return playlists
    }
    
    private fun getPlaylistCount(playlistId: Long): Int {
        var count = 0
        try {
            val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Playlists.Members._ID), null, null, null)
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (e: Exception) { }
        return count
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }
}
