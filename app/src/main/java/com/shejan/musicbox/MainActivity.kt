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

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for first run
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("IS_FIRST_RUN", true)

        if (isFirstRun) {
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Request Permissions
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
        }

        // Check for Default Home Redirect (Only if fresh start and NOT from nav click)
        if (savedInstanceState == null && !intent.getBooleanExtra("IS_NAV_CLICK", false)) {
            val homeId = TabManager.getHomeTabId(this)
            if (homeId != "home") {
                val target = TabManager.getTargetActivity(homeId)
                if (target != MainActivity::class.java) {
                     startActivity(Intent(this, target))
                     @Suppress("DEPRECATION")
                     overridePendingTransition(0, 0)
                     // Keep Main in backstack? Yes, usually.
                }
            }
        }

        setContentView(R.layout.activity_main)

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Greeting loaded in onResume


        // Helper to setup Nav clicks
        NavUtils.setupNavigation(this, R.id.nav_home)

        // Setup Home Boxes RecyclerView
        setupHomeBoxes()
        
        // Check for Updates
        if (prefs.getBoolean("show_pre_releases", false)) {
            GitHubReleaseManager.checkForUpdates(this, isManualCheck = false)
        }
    }
    
    private fun setupHomeBoxes() {
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_home_boxes)
        
        // Get saved box order
        val savedOrder = HomeBoxPreferences.getBoxOrder(this)
        val allBoxes = HomeBoxPreferences.getAllBoxes()
        
        // Create ordered list of visible boxes
        val visibleBoxes = savedOrder.mapNotNull { boxId ->
            if (HomeBoxPreferences.isBoxVisible(this, boxId)) {
                allBoxes.find { it.id == boxId }
            } else {
                null
            }
        }
        
        // Create MainHomeBox list with counts and click handlers
        val homeBoxes = visibleBoxes.map { box ->
            val (count, label, onClick) = when (box.id) {
                HomeBoxPreferences.BOX_FAVORITES -> {
                    Triple(getFavoriteCount(), "Favorites") {
                        val intent = Intent(this, TracksActivity::class.java)
                        intent.putExtra("SHOW_FAVORITES", true)
                        startActivity(intent)
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                HomeBoxPreferences.BOX_PLAYLISTS -> {
                    Triple(getPlaylistCount(), "Playlists") {
                        startActivity(Intent(this, PlaylistActivity::class.java))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                HomeBoxPreferences.BOX_ALBUMS -> {
                    Triple(getAlbumCount(), "Albums") {
                        startActivity(Intent(this, AlbumsActivity::class.java))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                HomeBoxPreferences.BOX_ARTISTS -> {
                    Triple(getArtistCount(), "Artists") {
                        startActivity(Intent(this, ArtistsActivity::class.java))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                HomeBoxPreferences.BOX_TRACKS -> {
                    Triple(getTrackCount(), "Tracks") {
                        startActivity(Intent(this, TracksActivity::class.java))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(0, 0)
                    }
                }
                HomeBoxPreferences.BOX_EQUALIZER -> {
                    Triple(-1, "Tune Sound") {
                        openEqualizer()
                    }
                }
                else -> Triple(0, "") {}
            }
            
            MainHomeBox(
                id = box.id,
                name = box.name.uppercase(),
                iconRes = box.iconRes,
                iconTint = getBoxIconTint(box.id),
                count = count,
                countLabel = label,
                onClick = onClick
            )
        }
        
        // Setup RecyclerView if not already setup
        if (recyclerView.layoutManager == null) {
            val layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
            recyclerView.layoutManager = layoutManager
            
            // 8dp spacing both horizontally and vertically (padding reduced to 22dp to keep box size constant)
            val spacing = (8 * resources.displayMetrics.density).toInt()
            recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, spacing, false))
        }
        
        recyclerView.adapter = MainHomeBoxAdapter(homeBoxes)
    }
    
    private fun getBoxIconTint(boxId: String): Int {
        return when (boxId) {
            HomeBoxPreferences.BOX_FAVORITES -> ContextCompat.getColor(this, R.color.primary_red)
            else -> ContextCompat.getColor(this, R.color.white)
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                updateDot(isPlaying)
            }
        }
    }

    private var musicService: MusicService? = null
    private var isBound = false
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            // Sync state immediately
            updateDot(musicService?.isPlaying() == true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private fun updateDot(isPlaying: Boolean) {
        val dot = findViewById<View>(R.id.v_red_dot)
        if (isPlaying) {
             dot.setBackgroundResource(R.drawable.shape_circle_green)
        } else {
             dot.setBackgroundResource(R.drawable.shape_circle_red)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter("UPDATE_MAIN_ACTIVITY"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, updateReceiver, IntentFilter("MUSIC_BOX_UPDATE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        setupHomeBoxes() // Refresh boxes with latest data
        updateGreeting()
        NavUtils.setupNavigation(this, R.id.nav_home) // Refresh Navigation in case Settings changed
    }
    
    override fun onResume() {
        super.onResume()
        setupHomeBoxes() // Refresh box visibility/order when returning from settings
        
        // Also check if already bound (unlikely to change between start and resume, but good for sync)
        if (isBound && musicService != null) {
            updateDot(musicService?.isPlaying() == true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        try {
            unregisterReceiver(updateReceiver)
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPause() {
        super.onPause()
    }

    private fun updateGreeting() {
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        val userName = prefs.getString("USER_NAME", "LISTENER")?.uppercase() ?: "LISTENER"
        val greetingText = findViewById<TextView>(R.id.tv_greeting)

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greetingResId = when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..16 -> R.string.good_afternoon
            in 17..21 -> R.string.good_evening
            else -> R.string.home_greeting
        }

        val fullInfo = getString(greetingResId) + "\n" + userName
        typeWriterEffect(greetingText, fullInfo)
    }

    private fun getFavoriteCount(): Int {
        val favorites = FavoritesManager.getFavorites(this)
        if (favorites.isEmpty()) return 0

        var count = 0
        try {
            val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
            val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
            val minDurationMillis = minDurationSec * 1000
            
            // Only query tracks that match our duration criteria
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
            
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA), // valid column
                selection,
                null, 
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    // Check if: 
                    // 1. It is in our favorites list
                    // 2. It is NOT hidden
                    // 3. It is not a ringtone/notification (extra safety)
                    if (favorites.contains(path) && 
                        !HiddenTracksManager.isHidden(this, path) && 
                        !path.lowercase().contains("ringtone") && 
                        !path.lowercase().contains("notification")) {
                        count++
                    }
                }
            }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getPlaylistCount(): Int {
        return AppPlaylistManager.getAllPlaylists(this).size
    }
    
    private fun getAlbumCount(): Int {
        var count = 0
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getArtistCount(): Int {
        var count = 0
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Artists._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getTrackCount(): Int {
        var count = 0
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
        val minDurationMs = minDurationSec * 1000
        
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getInt(durationColumn)
                    
                    if (!HiddenTracksManager.isHidden(this, path) && duration >= minDurationMs) {
                        count++
                    }
                }
            }
        } catch (_: Exception) { }
        return count
    }

    private fun typeWriterEffect(textView: TextView, text: String, delay: Long = 50) {
        // Cancel previous
        typingRunnable?.let { typingHandler.removeCallbacks(it) }

        typingRunnable = object : Runnable {
            var index = 0
            override fun run() {
                if (index <= text.length) {
                    // Show cursor while typing
                    val currentText = text.subSequence(0, index).toString()
                    @Suppress("SetTextI18n")
                    textView.text = "$currentText|"
                    
                    if (index < text.length) {
                        index++
                        typingHandler.postDelayed(this, delay)
                    } else {
                        // Finished typing, remove cursor after a moment
                         typingHandler.postDelayed({
                             textView.text = text
                         }, 800)
                    }
                }
            }
        }
        typingHandler.post(typingRunnable!!)
    }
    
    private fun openEqualizer() {
        try {
            val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Equalizer not available", Toast.LENGTH_SHORT).show()
        }
    }
}
