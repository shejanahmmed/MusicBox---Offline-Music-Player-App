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

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.ServiceConnection

import android.os.IBinder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge

class AlbumsActivity : AppCompatActivity() {

    private var localContentVersion: Long = 0
    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@AlbumsActivity, musicService)
            MiniPlayerManager.setup(this@AlbumsActivity) { musicService }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                MiniPlayerManager.update(this@AlbumsActivity, musicService)
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        androidx.core.content.ContextCompat.registerReceiver(this, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
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
        if (localContentVersion != MusicUtils.contentVersion) {
            loadAlbums()
        }
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this) { musicService }
        NavUtils.setupNavigation(this, R.id.nav_albums)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_albums)

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        setupNav()
        
        // Initialize RecyclerView to prevent "No adapter attached" error
        val rv = findViewById<RecyclerView>(R.id.rv_albums)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = AlbumAdapter(emptyList()) { album ->
             val intent = Intent(this@AlbumsActivity, TracksActivity::class.java)
             intent.putExtra("ALBUM_NAME", album.title)
             startActivity(intent)
        }

        loadAlbums()
        
    }

    private fun loadAlbums() {
        localContentVersion = MusicUtils.contentVersion
        
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext
            val list = MusicRepository.getAlbums(appContext)
            
            withContext(Dispatchers.Main) {
                // Update Count
                val countView = findViewById<android.widget.TextView>(R.id.tv_albums_count)
                val countText = if (list.size == 1) "1 Album" else "${list.size} Albums"
                countView.text = countText
        
                val rv = findViewById<RecyclerView>(R.id.rv_albums)
                rv.layoutManager = GridLayoutManager(this@AlbumsActivity, 2)
                rv.adapter = AlbumAdapter(list) { album ->
                     // Open TracksActivity with Album Filter
                     val intent = Intent(this@AlbumsActivity, TracksActivity::class.java)
                     intent.putExtra("ALBUM_NAME", album.title) 
                     startActivity(intent)
                }
            }
        }
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_albums)
    }
}




