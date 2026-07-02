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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.content.BroadcastReceiver
import android.graphics.Color
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

import java.util.Locale
import android.content.IntentFilter
import android.media.AudioManager
import android.widget.SeekBar
import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.media.audiofx.AudioEffect
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge

class NowPlayingActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"
        private val COLOR_INACTIVE_INT by lazy { "#888888".toColorInt() }

        fun start(context: Context, title: String, artist: String) {
            val intent = Intent(context, NowPlayingActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
            context.startActivity(intent)
            // Apply slide-up animation for drawer-style effect
            if (context is AppCompatActivity) {
                context.overridePendingTransition(R.anim.slide_up_enter, R.anim.no_animation)
            }
        }
    }

    private var musicService: MusicService? = null
    private var isBound = false
    

    // BroadcastReceiver for updates
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateUI()
            }
        }
    }

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateUI()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            musicService = null
            isBound = false
        }
    }


    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 1000)
    }
    }
    
    // State Caching for Artwork
    private var loadedTrackId: Long = -1L
    private var loadedContentVersion: Long = -1L

    // Artwork Picker Launcher
    private val pickArtworkLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && musicService != null) {
            val track = musicService?.getCurrentTrack() ?: return@registerForActivityResult
            
            // Persist permission (needed for future access)
            try {
                contentResolver.takePersistableUriPermission(
                    uri, 
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { e.printStackTrace() }
            
             // Save custom artwork
             TrackArtworkManager.saveArtwork(this, track.uri, uri.toString())
             ImageLoader.clearCacheForTrack(track.uri)
             MusicUtils.contentVersion++

            // Force Reload
            loadedTrackId = -1L
            
            // Refresh UI
            updateUI()
            Toast.makeText(this, getString(R.string.artwork_updated), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_now_playing)
        
        // Initial UI Static Setup
        val title = intent.getStringExtra(EXTRA_TITLE)
        val artist = intent.getStringExtra(EXTRA_ARTIST)
        if (title != null) findViewById<TextView>(R.id.tv_now_playing_title).text = title
        if (artist != null) findViewById<TextView>(R.id.tv_now_playing_artist).text = artist

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.no_animation, R.anim.slide_down_exit)
        }
        
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener {
            showTrackOptionsDialog()
        }
        
        // Enable marquee scrolling
        findViewById<TextView>(R.id.tv_now_playing_title).isSelected = true
        findViewById<TextView>(R.id.tv_now_playing_artist).isSelected = true
        
        setupControls()

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }
    }
    
    // Global Swipe Detection
    private var swipeStartY = 0f
    private val swipeThreshold = 150f // Pixels

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)
        
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val headerLimit = screenHeight * 0.25
        
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartY = if (ev.y <= headerLimit) {
                    ev.y
                } else {
                    -1f // Invalid
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeStartY != -1f) {
                    val deltaY = ev.y - swipeStartY
                    if (deltaY > swipeThreshold) {
                        finish()
                        overridePendingTransition(R.anim.no_animation, R.anim.slide_down_exit)
                        return true // Consume event
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeStartY = -1f
            }
        }
        
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("DiscouragedApi")
    private fun showQueueDialog() {
        if (MusicService.playlist.isEmpty() || MusicService.currentIndex < 0 || 
            MusicService.currentIndex >= MusicService.playlist.size) return

        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        @SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.dialog_queue, null)
        dialog.setContentView(view)

        val rvQueue = view.findViewById<RecyclerView>(R.id.rv_queue)
        rvQueue.layoutManager = LinearLayoutManager(this)
        
        val currentTrackId = if (MusicService.currentIndex in MusicService.playlist.indices)
            MusicService.playlist[MusicService.currentIndex].id else -1L
        
        // Use a copy to avoid ConcurrentModificationException
        // Use a copy to avoid ConcurrentModificationException
        val playlistCopy = synchronized(MusicService.playlist) {
            ArrayList(MusicService.playlist)
        }
        
        val adapter = QueueAdapter(playlistCopy, currentTrackId) { index ->
             musicService?.playTrack(index)
             updateUI()
             dialog.dismiss()
        }
        rvQueue.adapter = adapter
        
        // Scroll to current
        if (MusicService.currentIndex in MusicService.playlist.indices) {
            rvQueue.scrollToPosition(MusicService.currentIndex)
        }

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheetId = resources.getIdentifier("design_bottom_sheet", "id", "com.google.android.material")
            val bottomSheet = bottomSheetDialog.findViewById<View>(bottomSheetId)
            bottomSheet?.let { sheet ->
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
                val displayMetrics = resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.75).toInt()
                sheet.layoutParams.height = height
                behavior.peekHeight = height
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }

        dialog.show()
    }

    private fun showEqChooserDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        @SuppressLint("InflateParams")
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

    private fun setupControls() {
        val btnPlayPause = findViewById<ImageButton>(R.id.btn_play_large) 
        val btnNext = findViewById<ImageButton>(R.id.btn_next_large)
        val btnPrev = findViewById<ImageButton>(R.id.btn_prev_large)
        val seekBar = findViewById<SeekBar>(R.id.sb_progress)
        val volumeSeekBar = findViewById<SeekBar>(R.id.sb_volume)
        
        // Volume Control
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = currentVolume
        
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                        MusicUtils.performHapticFeedback(this@NowPlayingActivity)
                    } catch (_: SecurityException) {
                        Toast.makeText(this@NowPlayingActivity, getString(R.string.error_volume_restricted), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingVolume = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingVolume = false
            }
        })

        findViewById<ImageButton>(R.id.btn_volume_down).setOnClickListener {
             MusicUtils.viewBubbleAnimation(it)
             MusicUtils.performHapticFeedback(this)
             val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
             val newVol = (currentVol - 1).coerceAtLeast(0)
             try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                volumeSeekBar.progress = newVol
             } catch (_: SecurityException) {
                Toast.makeText(this, getString(R.string.error_volume_restricted), Toast.LENGTH_SHORT).show()
             }
        }

        findViewById<ImageButton>(R.id.btn_volume_up).setOnClickListener {
             MusicUtils.viewBubbleAnimation(it)
             MusicUtils.performHapticFeedback(this)
             val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
             val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
             val newVol = (currentVol + 1).coerceAtMost(maxVol)
             try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                volumeSeekBar.progress = newVol
             } catch (_: SecurityException) {
                Toast.makeText(this, getString(R.string.error_volume_restricted), Toast.LENGTH_SHORT).show()
             }
        }

        findViewById<ImageView>(R.id.iv_album_art_large).setOnClickListener {
             if (musicService?.isPlaying() == true) {
                 musicService?.pause()
             } else {
                 musicService?.play()
             }
        }

        findViewById<ImageView>(R.id.iv_album_art_square).setOnClickListener {
             if (musicService?.isPlaying() == true) {
                 musicService?.pause()
             } else {
                 musicService?.play()
             }
        }

        btnPlayPause.setOnClickListener {
             MusicUtils.viewBubbleAnimation(it)
             MusicUtils.performHapticFeedback(this)
             if (musicService?.isPlaying() == true) {
                 musicService?.pause()
             } else {
                 musicService?.play()
             }
        }
        
        btnNext.setOnClickListener {
            MusicUtils.viewBubbleAnimation(it)
            MusicUtils.performHapticFeedback(this)
            musicService?.playNext()
        }
        
        btnPrev.setOnClickListener {
            MusicUtils.viewBubbleAnimation(it)
            MusicUtils.performHapticFeedback(this)
            musicService?.playPrev()
        }

        val btnShuffle = findViewById<ImageButton>(R.id.btn_shuffle_large)
        btnShuffle.setOnClickListener {
            musicService?.toggleShuffle()
            // We can keep specific updateUI here if toggleShuffle doesn't broadcast immediately, 
            // but MusicService.toggleShuffle DOES broadcast.
            // However, the broadcast sends "SHUFFLE_STATE", not "MUSIC_BOX_UPDATE" in some versions?
            // Let's check MusicService.toggleShuffle broadcast.
            // It sends "MUSIC_BOX_UPDATE" with extra "SHUFFLE_STATE".
            // The receiver calls updateUI(). So we can remove this too.
        }

        val btnRepeat = findViewById<ImageButton>(R.id.btn_repeat_large)
        btnRepeat.setOnClickListener {
            musicService?.toggleRepeat()
            
            val mode = MusicService.repeatMode
            val msg = when (mode) {
                MusicService.REPEAT_ALL -> getString(R.string.repeat_all)
                MusicService.REPEAT_ONE -> getString(R.string.repeat_one)
                else -> getString(R.string.repeat_off)
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        
        // Favorite Button Logic
        val btnFav = findViewById<ImageButton>(R.id.btn_fav_large)
        btnFav.setOnClickListener {
            MusicUtils.performHapticFeedback(this)
            val track = musicService?.getCurrentTrack() ?: return@setOnClickListener
            val isCurrentlyFavorite = FavoritesManager.isFavorite(this, track.uri)
            
            if (isCurrentlyFavorite) {
                FavoritesManager.removeFavorite(this, track.uri)
                MusicUtils.animateFavoriteButton(btnFav, false) {
                    btnFav.setImageResource(R.drawable.ic_favorite_border)
                    btnFav.setColorFilter(getColor(R.color.colorIcon))
                }
                Toast.makeText(this, getString(R.string.fav_removed), Toast.LENGTH_SHORT).show()
            } else {
                FavoritesManager.addFavorite(this, track.uri)
                MusicUtils.animateFavoriteButton(btnFav, true) {
                    btnFav.setImageResource(R.drawable.ic_favorite)
                    btnFav.setColorFilter(getColor(R.color.primary_red))
                }
                Toast.makeText(this, getString(R.string.fav_added), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Queue Button Logic
        findViewById<View>(R.id.btn_queue).setOnClickListener {
            showQueueDialog()
        }

        // Sleep Timer Logic
        findViewById<View>(R.id.btn_sleep_timer).setOnClickListener {
             showSleepTimerDialog()
        }

        // Equalizer Button Logic
        findViewById<View>(R.id.btn_equalizer).setOnClickListener {
             MusicUtils.viewBubbleAnimation(it)
             MusicUtils.performHapticFeedback(this)
             showEqChooserDialog()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    findViewById<TextView>(R.id.tv_time_start).text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                handler.removeCallbacks(updateProgressAction)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    musicService?.seekTo(it.progress)
                    handler.post(updateProgressAction)
                }
            }
        })
    }
    
    // Flag to prevent double haptics during drag and receiver update
    private var isDraggingVolume = false

    private fun updateUI() {
        if (!isBound || musicService == null) return
        
        val track = musicService?.getCurrentTrack() ?: return
        
        // Check for custom metadata
        val customMetadata = TrackMetadataManager.getMetadata(this, track.uri)
        
        findViewById<TextView>(R.id.tv_now_playing_title).text = customMetadata?.title ?: track.title
        findViewById<TextView>(R.id.tv_now_playing_artist).text = customMetadata?.artist ?: track.artist
        
        // Apply artwork style visibility
        applyArtworkStyle()

        // Load Album Art ONLY if track changed or content updated
        if (track.id != loadedTrackId || MusicUtils.contentVersion != loadedContentVersion) {
            val ivAlbumArt = findViewById<ImageView>(R.id.iv_album_art_large)
            MusicUtils.loadTrackArt(this, track.id, track.albumId, track.uri, ivAlbumArt)

            // Also load into square view
            val ivAlbumArtSquare = findViewById<ImageView>(R.id.iv_album_art_square)
            MusicUtils.loadTrackArt(this, track.id, track.albumId, track.uri, ivAlbumArtSquare)

            loadedTrackId = track.id
            loadedContentVersion = MusicUtils.contentVersion
        }
        
        // Update Duration FIRST to avoid progress clamping
        val duration = musicService!!.getDuration()
        findViewById<SeekBar>(R.id.sb_progress).max = duration
        findViewById<TextView>(R.id.tv_time_end).text = formatTime(duration)
        
        // Update Favorite Icon
        val btnFav = findViewById<ImageButton>(R.id.btn_fav_large)
        if (FavoritesManager.isFavorite(this, track.uri)) {
             btnFav.setImageResource(R.drawable.ic_favorite)
             btnFav.setColorFilter(getColor(R.color.primary_red))
        } else {
             btnFav.setImageResource(R.drawable.ic_favorite_border)
             btnFav.setColorFilter(getColor(R.color.colorIcon))
        }
        
        val btnPlayPause = findViewById<ImageButton>(R.id.btn_play_large)
        if (musicService?.isPlaying() == true) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
            handler.removeCallbacks(updateProgressAction)
            handler.post(updateProgressAction)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play_arrow)
            handler.removeCallbacks(updateProgressAction)
            updateProgress() // Update once to catch up
        }
        
        // Update Shuffle Icon
        val btnShuffle = findViewById<ImageButton>(R.id.btn_shuffle_large)
        if (MusicService.isShuffleEnabled) {
            btnShuffle.setColorFilter(getColor(R.color.primary_red))
            btnShuffle.alpha = 1.0f
        } else {
             btnShuffle.setColorFilter(COLOR_INACTIVE_INT)
             btnShuffle.alpha = 1.0f
        }
        
        // Update Repeat Icon
        val btnRepeat = findViewById<ImageButton>(R.id.btn_repeat_large)
        if (MusicService.repeatMode != MusicService.REPEAT_OFF) {
            btnRepeat.setColorFilter(getColor(R.color.primary_red))
            btnRepeat.alpha = 1.0f
            // If we had a specific icon for Repeat One, we'd set it here.
            // For now, Red indicates active.
        } else {
            btnRepeat.setColorFilter(COLOR_INACTIVE_INT)
            btnRepeat.alpha = 1.0f
        }

        // Update Sleep Timer Icon
        val btnSleepTimer = findViewById<ImageView>(R.id.btn_sleep_timer)
        val sleepEndTime = musicService?.sleepTimerEndTime ?: 0L
        if (sleepEndTime > System.currentTimeMillis()) {
            btnSleepTimer.setColorFilter(getColor(R.color.primary_red))
            btnSleepTimer.alpha = 1.0f
        } else {
            btnSleepTimer.setColorFilter(getColor(R.color.colorIcon))
            btnSleepTimer.alpha = 1.0f
        }
    }

    private fun applyArtworkStyle() {
        val style = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
            .getString("artwork_style", "default") ?: "default"
        val clVinyl = findViewById<View>(R.id.cl_vinyl)
        val clSquare = findViewById<View>(R.id.cl_artwork_square)
        if (style == "square") {
            clVinyl?.visibility = View.GONE
            clSquare?.visibility = View.VISIBLE
        } else {
            clVinyl?.visibility = View.VISIBLE
            clSquare?.visibility = View.GONE
        }
    }

    private fun updateProgress() {
        if (musicService == null) return
        val currentPosition = musicService!!.getCurrentPosition()
        findViewById<SeekBar>(R.id.sb_progress).progress = currentPosition
        findViewById<TextView>(R.id.tv_time_start).text = formatTime(currentPosition)
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // Volume Change Receiver
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                updateVolumeBar(feedback = true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to Service
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        // Register Music Update Receiver
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Register Volume Receiver
        val volumeFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        // Volume change is a system broadcast, so we can use default registration or exported? 
        // Actually, Volume change is broadcast by AudioManager. 
        // For system broadcasts, ContextCompat handles flags correctly if we just pass RECEIVER_EXPORTED.
        // Or if we don't care about external apps sending it (spoofing), NOT_EXPORTED works? 
        // No, if it's from system, it IS external. So we need RECEIVER_EXPORTED.
        ContextCompat.registerReceiver(this, volumeReceiver, volumeFilter, ContextCompat.RECEIVER_EXPORTED)
        
        // Sync Volume on Start
        updateVolumeBar(feedback = false)
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
        
        try {
            unregisterReceiver(volumeReceiver)
        } catch (_: Exception) {}
        
        handler.removeCallbacks(updateProgressAction)
    }
    
    private fun updateVolumeBar(feedback: Boolean = false) {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val seekBar = findViewById<SeekBar>(R.id.sb_volume)
            
            // Allow update if not dragging, OR if value mismatches significantly (external force)
            // But main goal is to avoid clearing drag state.
            // If dragging, we mostly trust user, but if physical key pressed, we must update.
            // But physical key implies !isDraggingVolume usually (unless multitasking?)
            
            if (!isDraggingVolume) {
                if (seekBar != null && seekBar.progress != currentVolume) {
                    seekBar.progress = currentVolume
                    if (feedback) {
                         MusicUtils.performHapticFeedback(this)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    
    @SuppressLint("InflateParams")
    private fun showSleepTimerDialog() {
        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.dialog_sleep_timer, null)
        dialog.setContentView(view)
        
        val llSetup = view.findViewById<View>(R.id.ll_timer_setup)
        val llActive = view.findViewById<View>(R.id.ll_timer_active)
        val tvCountdown = view.findViewById<TextView>(R.id.tv_timer_countdown)
        
        val updateTimerView = object : Runnable {
            override fun run() {
                val endTime = musicService?.sleepTimerEndTime ?: 0L
                val remaining = endTime - System.currentTimeMillis()
                
                if (remaining > 0) {
                     // Leak Check: Stop if activity is gone
                    if (isFinishing || isDestroyed) {
                        view.removeCallbacks(this)
                        return
                    }

                    llSetup.visibility = View.GONE
                    llActive.visibility = View.VISIBLE
                    
                    val seconds = (remaining / 1000) % 60
                    val minutes = (remaining / (1000 * 60)) % 60
                    val hours = (remaining / (1000 * 60 * 60))
                    
                    tvCountdown.text = if (hours > 0) {
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    }
                    
                    view.postDelayed(this, 1000)
                } else {
                    llSetup.visibility = View.VISIBLE
                    llActive.visibility = View.GONE
                    view.removeCallbacks(this)
                }
            }
        }
        
        // Initial Check
        updateTimerView.run()
        
        // Handle Dialog Dismiss to stop updating UI
        dialog.setOnDismissListener {
            view.removeCallbacks(updateTimerView)
        }
        
        val etCustom = view.findViewById<android.widget.EditText>(R.id.et_custom_time)
        val btnStart = view.findViewById<View>(R.id.btn_start_timer)
        val btnCancel = view.findViewById<View>(R.id.btn_cancel_timer)
        
        btnCancel.setOnClickListener {
            musicService?.cancelSleepTimer()
            Toast.makeText(this, "Sleep timer cancelled", Toast.LENGTH_SHORT).show()
            updateTimerView.run() // Refresh view immediately
            updateUI()
            dialog.dismiss()
        }
        
        val setTime = { min: Int ->
            musicService?.startSleepTimer(min)
            Toast.makeText(this, "Sleep timer set for $min minutes", Toast.LENGTH_SHORT).show()
            updateUI()
            dialog.dismiss()
        }
        
        // Presets now just fill the text box
        view.findViewById<View>(R.id.btn_15_min).setOnClickListener { etCustom.setText(getString(R.string.timer_15)); etCustom.setSelection(2) }
        view.findViewById<View>(R.id.btn_30_min).setOnClickListener { etCustom.setText(getString(R.string.timer_30)); etCustom.setSelection(2) }
        view.findViewById<View>(R.id.btn_45_min).setOnClickListener { etCustom.setText(getString(R.string.timer_45)); etCustom.setSelection(2) }
        view.findViewById<View>(R.id.btn_60_min).setOnClickListener { etCustom.setText(getString(R.string.timer_60)); etCustom.setSelection(2) }
        
        btnStart.setOnClickListener {
            val input = etCustom.text.toString()
            if (input.isNotEmpty()) {
                val min = input.toIntOrNull()
                if (min != null && min > 0) {
                    setTime(min)
                    updateUI()
                } else {
                    Toast.makeText(this, getString(R.string.enter_valid_number), Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        dialog.show()
    }

    private fun showTrackOptionsDialog() {
        val track = musicService?.getCurrentTrack() ?: return
        
        TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
            override fun onArtworkChanged() {
                // Force Reload
                loadedTrackId = -1L
                updateUI()
            }
            override fun onTrackUpdated() {
                updateUI()
            }
            override fun onTrackDeleted() {
                 if (MusicService.playlist.isEmpty()) {
                     finish()
                 } else {
                     updateUI()
                 }
            }
        })
    }

}



