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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.ServiceConnection

import android.os.IBinder

class ArtistsActivity : AppCompatActivity() {

    private var localContentVersion: Long = 0
    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@ArtistsActivity, musicService)
            MiniPlayerManager.setup(this@ArtistsActivity) { musicService }
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
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (localContentVersion != MusicUtils.contentVersion) {
            loadArtists()
        }
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this) { musicService }
        NavUtils.setupNavigation(this, R.id.nav_artists)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artists)

        setupNav()

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }
        loadArtists()
        
    }

    private fun loadArtists() {
        localContentVersion = MusicUtils.contentVersion
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                     Toast.makeText(this@ArtistsActivity, "Error loading artists", Toast.LENGTH_SHORT).show()
                }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                // Update Count
                val countView = findViewById<android.widget.TextView>(R.id.tv_artists_count)
                val countText = if (list.size == 1) "1 Artist" else "${list.size} Artists"
                countView.text = countText
        
                val rv = findViewById<RecyclerView>(R.id.rv_artists)
                rv.layoutManager = LinearLayoutManager(this@ArtistsActivity)
                rv.adapter = ArtistAdapter(list) { artist ->
                     // On Click: Open TracksActivity with Artist Filter (implement later if needed, mostly requested UI for now)
                     val intent = Intent(this@ArtistsActivity, TracksActivity::class.java)
                     intent.putExtra("ARTIST_NAME", artist.name)
                     startActivity(intent)
                }
            }
        }
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_artists)
    }
}

