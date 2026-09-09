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
import android.annotation.SuppressLint
import android.media.audiofx.AudioEffect
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                     overridePendingTransition(0, 0)
                     // Keep Main in backstack? Yes, usually.
                }
            }
        }

        setContentView(R.layout.activity_main)

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        // Greeting loaded in onResume

        // Helper to setup Nav clicks
        NavUtils.setupNavigation(this, R.id.nav_home)

        // Setup Home Boxes RecyclerView synchronously with empty adapter to prevent "No adapter attached; skipping layout"
        val rvHomeBoxes = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_home_boxes)
        if (rvHomeBoxes != null) {
            rvHomeBoxes.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
            val spacing = (12 * resources.displayMetrics.density).toInt()
            rvHomeBoxes.addItemDecoration(GridSpacingItemDecoration(2, spacing, spacing, false))
            rvHomeBoxes.adapter = MainHomeBoxAdapter(emptyList())
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.homeBoxes.collect { boxItems ->
                    if (boxItems.isNotEmpty()) {
                        val homeBoxes = boxItems.map { item ->
                            MainHomeBox(
                                id = item.id,
                                name = item.name,
                                iconRes = item.iconRes,
                                iconTint = getBoxIconTint(item.id),
                                count = item.count,
                                countLabel = item.countLabel,
                                onClick = getBoxClickListener(item.id)
                            )
                        }
                        rvHomeBoxes?.adapter = MainHomeBoxAdapter(homeBoxes)
                    }
                }
            }
        }

        setupHomeBoxes()
    }

    private fun getBoxClickListener(boxId: String): () -> Unit {
        return when (boxId) {
            HomeBoxPreferences.BOX_FAVORITES -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    val intent = Intent(this, TracksActivity::class.java)
                    intent.putExtra("SHOW_FAVORITES", true)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                }
            }
            HomeBoxPreferences.BOX_PLAYLISTS -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    startActivity(Intent(this, PlaylistActivity::class.java))
                    overridePendingTransition(0, 0)
                }
            }
            HomeBoxPreferences.BOX_ALBUMS -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    startActivity(Intent(this, AlbumsActivity::class.java))
                    overridePendingTransition(0, 0)
                }
            }
            HomeBoxPreferences.BOX_ARTISTS -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    startActivity(Intent(this, ArtistsActivity::class.java))
                    overridePendingTransition(0, 0)
                }
            }
            HomeBoxPreferences.BOX_TRACKS -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    startActivity(Intent(this, TracksActivity::class.java))
                    overridePendingTransition(0, 0)
                }
            }
            HomeBoxPreferences.BOX_EQUALIZER -> {
                {
                    MusicUtils.performHapticFeedback(this)
                    showEqChooserDialog()
                }
            }
            else -> { {} }
        }
    }
    
    private fun setupHomeBoxes() {
        viewModel.loadHomeBoxes(this)
    }
    
    private fun getBoxIconTint(boxId: String): Int {
        return when (boxId) {
            HomeBoxPreferences.BOX_FAVORITES -> ContextCompat.getColor(this, R.color.primary_red)
            else -> ContextCompat.getColor(this, R.color.colorIcon)
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                updateDot(isPlaying)
            } else if (intent?.action == "com.shejan.musicbox.REFRESH_DATA") {
                setupHomeBoxes()
            }
        }
    }

    private var musicService: MusicService? = null
    private var isBound = false
    private val typingHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null
    private var isReceiverRegistered = false

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
        val statusLabel = findViewById<TextView>(R.id.tv_status_label)
        if (isPlaying) {
             dot?.setBackgroundResource(R.drawable.shape_circle_green)
             statusLabel?.text = "PLAYING"
             statusLabel?.setTextColor(ContextCompat.getColor(this, R.color.primary_green))
        } else {
             dot?.setBackgroundResource(R.drawable.shape_circle_red)
             statusLabel?.text = "OFFLINE"
             statusLabel?.setTextColor(ContextCompat.getColor(this, R.color.colorTextSecondary))
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        // Register receiver only if not already registered
        if (!isReceiverRegistered) {
            try {
                ContextCompat.registerReceiver(this, updateReceiver, IntentFilter("UPDATE_MAIN_ACTIVITY"), ContextCompat.RECEIVER_NOT_EXPORTED)
                val filter = IntentFilter("MUSIC_BOX_UPDATE")
                filter.addAction("com.shejan.musicbox.REFRESH_DATA")
                ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                isReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
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
        
        // Remove typing callbacks to prevent leaks
        typingRunnable?.let { typingHandler.removeCallbacks(it) }
        
        // Only unregister if it was registered
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(updateReceiver)
                isReceiverRegistered = false
            } catch (_: IllegalArgumentException) {}
        }
    }

    override fun onPause() {
        super.onPause()
    }

    private fun updateGreeting() {
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        val rawName = prefs.getString("USER_NAME", "LISTENER")?.trim() ?: "LISTENER"
        val userName = if (rawName.isEmpty()) "LISTENER" else rawName.uppercase()
        val tvSub = findViewById<TextView>(R.id.tv_greeting_sub)
        val greetingText = findViewById<TextView>(R.id.tv_greeting)

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greetingResId = when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..16 -> R.string.good_afternoon
            in 17..21 -> R.string.good_evening
            else -> R.string.home_greeting
        }

        tvSub?.setText(greetingResId)
        typeWriterEffect(greetingText, userName)
    }

    private fun getFavoriteCount(): Int = MusicRepository.getFavoriteCount(this)
    
    private fun getPlaylistCount(): Int = AppPlaylistManager.getAllPlaylists(this).size
    
    private fun getAlbumCount(): Int = MusicRepository.getAlbumCount(this)
    
    private fun getArtistCount(): Int = MusicRepository.getArtistCount(this)
    
    private fun getTrackCount(): Int = MusicRepository.getTrackCount(this)

    private fun typeWriterEffect(textView: TextView?, text: String, delay: Long = 40) {
        if (textView == null) return
        typingRunnable?.let { typingHandler.removeCallbacks(it) }

        typingRunnable = object : Runnable {
            var index = 0
            override fun run() {
                if (index > text.length) {
                    index = text.length
                }
                
                if (index <= text.length) {
                    try {
                        val currentText = text.subSequence(0, index).toString()
                        textView.text = "$currentText|"
                        
                        if (index < text.length) {
                            index++
                            typingHandler.postDelayed(this, delay)
                        } else {
                            typingHandler.postDelayed({
                                textView.text = text
                            }, 700)
                        }
                    } catch (_: Exception) {
                        textView.text = text
                    }
                }
            }
        }
        typingRunnable?.run()
    }
    
    @SuppressLint("InflateParams")
    private fun showEqChooserDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.dialog_eq_chooser, null)
        dialog.setContentView(view)

        val tvStatus = view.findViewById<TextView>(R.id.tv_builtin_status)
        if (EqManager.isEnabled) {
            val presetName = EqManager.getMatchingPresetName()
            tvStatus.text = if (presetName != null) "On ($presetName)" else "On (Custom)"
        } else {
            tvStatus.text = "Off"
        }

        view.findViewById<View>(R.id.option_builtin_eq).setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, EqActivity::class.java))
        }

        view.findViewById<View>(R.id.option_system_eq).setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, musicService?.getAudioSessionId() ?: 0)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.eq_error_system), Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.option_other_eq).setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                val chooser = Intent.createChooser(intent, "Choose Equalizer")
                startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.eq_error_other), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}



