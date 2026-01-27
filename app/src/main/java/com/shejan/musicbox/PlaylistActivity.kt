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

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistActivity : AppCompatActivity() {

    private var localContentVersion: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)
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
        if (localContentVersion != MusicUtils.contentVersion) {
            loadPlaylists()
        }

        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this) { musicService }
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }

    private fun updateTopCards(count: Int = -1) {
        if (count != -1) {
             findViewById<TextView>(R.id.tv_playlists_count).text = getString(R.string.lists_count, count)
        } else {
             // Fallback for immediate UI updates if needed (though usually we wait for load)
             findViewById<TextView>(R.id.tv_playlists_count).text = getString(R.string.lists_count, 0)
        }
    }

    // setupTopCards removed as it only set listener on removed view

    private fun loadPlaylists() {
        localContentVersion = MusicUtils.contentVersion
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<PlaylistItem>()

            // 2. User Playlists from App Storage
            val appPlaylists = AppPlaylistManager.getAllPlaylists(this@PlaylistActivity)
            
            // Map to PlaylistItem
            list.addAll(appPlaylists.map { PlaylistItem(it.id, it.name, it.trackPaths.size) })

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                val rv = findViewById<RecyclerView>(R.id.rv_playlists)
                val emptyView = findViewById<TextView>(R.id.tv_empty_state)
                
                if (list.isEmpty()) {
                    rv.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    rv.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }

                rv.layoutManager = LinearLayoutManager(this@PlaylistActivity)
                rv.adapter = PlaylistAdapter(list, onClick = { item ->
                     val intent = Intent(this@PlaylistActivity, TracksActivity::class.java)
                     intent.putExtra("PLAYLIST_ID", item.id)
                     intent.putExtra("PLAYLIST_NAME", item.name)
                     startActivity(intent)
                }, onLongClick = { item ->
                    showDeleteDialog(item)
                })
                
                updateTopCards(list.size)
            }
        }
    }



    // getSystemPlaylists removed
    
    // getPlaylistCount removed


    @android.annotation.SuppressLint("InflateParams", "SetTextI18n")
    private fun showDeleteDialog(playlist: PlaylistItem) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val view = layoutInflater.inflate(R.layout.dialog_delete_playlist, null)
        dialog.setContentView(view)
        
        // Transparent background
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        
        // Width
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        // Setup text
        view.findViewById<TextView>(R.id.tv_dialog_message).text = 
            "Are you sure you want to delete \"${playlist.name}\"?"
            
        // Cancel
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // Delete
        view.findViewById<View>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            deletePlaylist(playlist)
        }
        
        dialog.show()
    }
    
    private fun deletePlaylist(playlist: PlaylistItem) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            AppPlaylistManager.deletePlaylist(this@PlaylistActivity, playlist.id)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                Toast.makeText(this@PlaylistActivity, "Playlist deleted", Toast.LENGTH_SHORT).show()
                // Reload
                loadPlaylists()
            }
        }
    }
    
    // onActivityResult removed as it is no longer needed/reachable for delete

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_playlist)
    }
}

