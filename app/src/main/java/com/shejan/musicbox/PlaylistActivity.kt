package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder

class PlaylistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        setupNav()

        // Setup Header Add Button
        findViewById<View>(R.id.btn_add_playlist).setOnClickListener {
            // Launch Create Playlist Activity
            startActivity(Intent(this, CreatePlaylistActivity::class.java))
        }

        // Initial Load
        loadPlaylists()
        updateTopCards()
        
    }

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@PlaylistActivity, musicService)
            MiniPlayerManager.setup(this@PlaylistActivity) { musicService }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                MiniPlayerManager.update(this@PlaylistActivity, musicService)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        MiniPlayerManager.setup(this) { musicService }
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }

    private fun updateTopCards() {
         val systemPlaylists = getSystemPlaylists()
         findViewById<TextView>(R.id.tv_playlists_count).text = getString(R.string.lists_count, systemPlaylists.size)
    }

    // setupTopCards removed as it only set listener on removed view

    private fun loadPlaylists() {
        val list = mutableListOf<PlaylistItem>()

        // 2. User Playlists from System
        list.addAll(getSystemPlaylists())

        val rv = findViewById<RecyclerView>(R.id.rv_playlists)
        val emptyView = findViewById<TextView>(R.id.tv_empty_state)
        
        if (list.isEmpty()) {
            rv.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(list) { item ->
             val intent = Intent(this, TracksActivity::class.java)
             intent.putExtra("PLAYLIST_ID", item.id)
             intent.putExtra("PLAYLIST_NAME", item.name)
             startActivity(intent)
        }
    }



    @Suppress("DEPRECATION")
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
        } catch (_: Exception) { }
        return playlists
    }
    
    @Suppress("DEPRECATION")
    private fun getPlaylistCount(playlistId: Long): Int {
        var count = 0
        try {
            val uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val cursor = contentResolver.query(uri, arrayOf(MediaStore.Audio.Playlists.Members._ID), null, null, null)
            count = cursor?.count ?: 0
            cursor?.close()
        } catch (_: Exception) { }
        return count
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }
}
