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
             TrackArtworkManager.saveArtwork(this, track.id, uri.toString())

            // Refresh UI
            updateUI()
            Toast.makeText(this, getString(R.string.artwork_updated), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        
        // Initial UI Static Setup
        val title = intent.getStringExtra(EXTRA_TITLE)
        val artist = intent.getStringExtra(EXTRA_ARTIST)
        if (title != null) findViewById<TextView>(R.id.tv_now_playing_title).text = title
        if (artist != null) findViewById<TextView>(R.id.tv_now_playing_artist).text = artist

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }
        
        findViewById<ImageButton>(R.id.btn_more).setOnClickListener {
            showTrackOptionsDialog()
        }
        
        // Enable marquee scrolling for title
        findViewById<TextView>(R.id.tv_now_playing_title).isSelected = true
        
        setupControls()

        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
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
                        @Suppress("DEPRECATION")
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
        if (MusicService.playlist.isEmpty()) return

        val dialog = BottomSheetDialog(this)
        @SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.dialog_queue, null)
        dialog.setContentView(view)
        
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val rvQueue = view.findViewById<RecyclerView>(R.id.rv_queue)
        rvQueue.layoutManager = LinearLayoutManager(this)
        
        val adapter = QueueAdapter(MusicService.playlist, MusicService.playlist[MusicService.currentIndex].id) { index ->
             musicService?.playTrack(index)
             updateUI()
             dialog.dismiss()
        }
        rvQueue.adapter = adapter
        
        // Scroll to current
        rvQueue.scrollToPosition(MusicService.currentIndex)

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
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        findViewById<ImageButton>(R.id.btn_volume_down).setOnClickListener {
             val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
             val newVol = (currentVol - 1).coerceAtLeast(0)
             audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
             volumeSeekBar.progress = newVol
        }

        findViewById<ImageButton>(R.id.btn_volume_up).setOnClickListener {
             val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
             val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
             val newVol = (currentVol + 1).coerceAtMost(maxVol)
             audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
             volumeSeekBar.progress = newVol
        }

        findViewById<ImageView>(R.id.iv_album_art_large).setOnClickListener {
             if (musicService?.isPlaying() == true) {
                 musicService?.pause()
             } else {
                 musicService?.play()
             }
             updateUI()
        }

        btnPlayPause.setOnClickListener {
             if (musicService?.isPlaying() == true) {
                 musicService?.pause()
             } else {
                 musicService?.play()
             }
             updateUI()
        }
        
        btnNext.setOnClickListener {
            musicService?.playNext()
            updateUI()
        }
        
        btnPrev.setOnClickListener {
            musicService?.playPrev()
            updateUI()
        }

        val btnShuffle = findViewById<ImageButton>(R.id.btn_shuffle_large)
        btnShuffle.setOnClickListener {
            musicService?.toggleShuffle()
            updateUI()
        }

        val btnRepeat = findViewById<ImageButton>(R.id.btn_repeat_large)
        btnRepeat.setOnClickListener {
            musicService?.toggleRepeat()
            updateUI()
            
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
            val track = musicService?.getCurrentTrack() ?: return@setOnClickListener
            if (FavoritesManager.isFavorite(this, track.uri)) {
                FavoritesManager.removeFavorite(this, track.uri)
                Toast.makeText(this, getString(R.string.fav_removed), Toast.LENGTH_SHORT).show()
            } else {
                FavoritesManager.addFavorite(this, track.uri)
                Toast.makeText(this, getString(R.string.fav_added), Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }
        
        // Queue Button Logic
        findViewById<View>(R.id.btn_queue).setOnClickListener {
            showQueueDialog()
        }

        // Share Button Logic
        findViewById<View>(R.id.btn_share).setOnClickListener {
             val track = musicService?.getCurrentTrack()
             if (track != null) {
                 shareTrack(track)
             }
        }

        // Equalizer Button Logic
        findViewById<View>(R.id.btn_equalizer).setOnClickListener {
             try {
                 val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                 intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, musicService?.getAudioSessionId() ?: 0)
                 intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                 intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                 // Result launcher not needed unless we want to know when they return.
                 // startActivityForResult is deprecated but simple here. 
                 // Or just startActivity if we don't care about result.
                 startActivityForResult(intent, 1002) 
             } catch (e: Exception) {
                 Toast.makeText(this, "No Equalizer found", Toast.LENGTH_SHORT).show()
             }
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
    
    private fun updateUI() {
        if (!isBound || musicService == null) return
        
        val track = musicService?.getCurrentTrack() ?: return
        
        // Check for custom metadata
        val customMetadata = TrackMetadataManager.getMetadata(this, track.id)
        
        findViewById<TextView>(R.id.tv_now_playing_title).text = customMetadata?.title ?: track.title
        findViewById<TextView>(R.id.tv_now_playing_artist).text = customMetadata?.artist ?: track.artist
        
        // Load Album Art
        val ivAlbumArt = findViewById<ImageView>(R.id.iv_album_art_large)
        MusicUtils.loadTrackArt(this, track.id, track.albumId, ivAlbumArt)
        
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
             btnFav.setColorFilter(getColor(R.color.white))
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
                updateVolumeBar()
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
        updateVolumeBar()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(receiver)
        unregisterReceiver(volumeReceiver)
        handler.removeCallbacks(updateProgressAction)
    }
    
    private fun updateVolumeBar() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        findViewById<SeekBar>(R.id.sb_volume)?.progress = currentVolume
    }
    
    private fun showTrackOptionsDialog() {
        val track = musicService?.getCurrentTrack() ?: return
        
        TrackMenuManager.showTrackOptionsDialog(this, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
            override fun onArtworkChanged() {
                updateUI()
            }
            override fun onTrackUpdated() {
                updateUI()
            }
            override fun onTrackDeleted() {
                 
                 if (MusicService.playlist.isNotEmpty()) {
                     
                     val currentId = musicService?.getCurrentTrack()?.id
                     
                     
                     if (currentId == track.id) {
                         musicService?.playNext()
                     }
                     
                     MusicService.playlist = MusicService.playlist.filter { !HiddenTracksManager.isHidden(this@NowPlayingActivity, it.uri) }
                     
                     if (MusicService.playlist.isEmpty()) {
                         finish()
                     } else {
                         updateUI()
                     }
                 } else {
                     finish()
                 }
            }
        })
    }
    private fun shareTrack(track: Track) {
        try {
            val file = java.io.File(track.uri)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "audio/*"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(intent, getString(R.string.share_track)))
            } else {
                 Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
             Toast.makeText(this, getString(R.string.share_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
