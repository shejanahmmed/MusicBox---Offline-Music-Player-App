package com.shejan.musicbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shejan.musicbox.TrackArtworkManager

class NowPlayingActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_ARTIST = "extra_artist"

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
    private val receiver = object : android.content.BroadcastReceiver() {
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
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { e.printStackTrace() }
            
            // Save custom artwork
            TrackArtworkManager.saveArtwork(this, track.id, uri.toString())
            
            // Refresh UI
            updateUI()
            android.widget.Toast.makeText(this, "Artwork updated", android.widget.Toast.LENGTH_SHORT).show()
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
    }
    
    private fun showQueueDialog() {
        if (MusicService.playlist.isEmpty()) return

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_queue, null)
        dialog.setContentView(view)
        
        // Make the background transparent to show rounded corners if root view has them, 
        // but dialog_queue.xml doesn't have a CardView or ShapeDrawable root yet.
        // Let's just rely on default BottomSheet styling or user preference.
        // Actually, let's fix the background to be dark.
        (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val rvQueue = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_queue)
        rvQueue.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        val adapter = QueueAdapter(MusicService.playlist, MusicService.playlist[MusicService.currentIndex].id) { index ->
             musicService?.playTrack(index)
             updateUI()
             dialog.dismiss()
        }
        rvQueue.adapter = adapter
        
        // Scroll to current
        rvQueue.scrollToPosition(MusicService.currentIndex)

        dialog.show()
    }

    private fun setupControls() {
        val btnPlayPause = findViewById<ImageButton>(R.id.btn_play_large) 
        val btnNext = findViewById<ImageButton>(R.id.btn_next_large)
        val btnPrev = findViewById<ImageButton>(R.id.btn_prev_large)
        val seekBar = findViewById<android.widget.SeekBar>(R.id.sb_progress)
        val volumeSeekBar = findViewById<android.widget.SeekBar>(R.id.sb_volume)
        
        // Volume Control
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = currentVolume
        
        volumeSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, progress, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

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
                MusicService.REPEAT_ALL -> "Repeat All"
                MusicService.REPEAT_ONE -> "Repeat One"
                else -> "Repeat Off"
            }
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // Favorite Button Logic
        val btnFav = findViewById<ImageButton>(R.id.btn_fav_large)
        btnFav.setOnClickListener {
            val track = musicService?.getCurrentTrack() ?: return@setOnClickListener
            if (FavoritesManager.isFavorite(this, track.uri)) {
                FavoritesManager.removeFavorite(this, track.uri)
                android.widget.Toast.makeText(this, "Removed from Favorites", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                FavoritesManager.addFavorite(this, track.uri)
                android.widget.Toast.makeText(this, "Added to Favorites", android.widget.Toast.LENGTH_SHORT).show()
            }
            updateUI()
            updateUI()
        }
        
        // Queue Button Logic
        findViewById<android.view.View>(R.id.btn_queue).setOnClickListener {
            showQueueDialog()
        }

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    findViewById<TextView>(R.id.tv_time_start).text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                handler.removeCallbacks(updateProgressAction)
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
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
        val ivAlbumArt = findViewById<android.widget.ImageView>(R.id.iv_album_art_large)
        MusicUtils.loadTrackArt(this, track.id, track.albumId, ivAlbumArt)
        
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
             btnShuffle.setColorFilter(android.graphics.Color.parseColor("#888888"))
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
            btnRepeat.setColorFilter(android.graphics.Color.parseColor("#888888"))
            btnRepeat.alpha = 1.0f
        }
        
        val duration = musicService!!.getDuration()
        findViewById<android.widget.SeekBar>(R.id.sb_progress).max = duration
        findViewById<TextView>(R.id.tv_time_end).text = formatTime(duration)
    }

    private fun updateProgress() {
        if (musicService == null) return
        val currentPosition = musicService!!.getCurrentPosition()
        findViewById<android.widget.SeekBar>(R.id.sb_progress).progress = currentPosition
        findViewById<TextView>(R.id.tv_time_start).text = formatTime(currentPosition)
    }

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // Volume Change Receiver
    private val volumeReceiver = object : android.content.BroadcastReceiver() {
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
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        // Register Music Update Receiver
        val filter = android.content.IntentFilter("MUSIC_BOX_UPDATE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        
        // Register Volume Receiver
        val volumeFilter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        registerReceiver(volumeReceiver, volumeFilter)
        
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
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        findViewById<android.widget.SeekBar>(R.id.sb_volume)?.progress = currentVolume
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
                 // Remove from Memory Playlist
                 if (MusicService.playlist.isNotEmpty()) {
                     // If deletion happened, we should check if it was the playing one.
                     // The passed 'track' is what we hid.
                     val currentId = musicService?.getCurrentTrack()?.id
                     
                     // If we are playing the one we just hid (likely yes in NowPlaying)
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
}
