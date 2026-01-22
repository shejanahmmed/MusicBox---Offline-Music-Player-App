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

        // Favorite Box Click
        findViewById<View>(R.id.cl_favorite_box).setOnClickListener {
            val intent = Intent(this, TracksActivity::class.java)
            intent.putExtra("SHOW_FAVORITES", true)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        // Playlist Box Click
        findViewById<View>(R.id.cl_playlist_box).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        // Albums Box Click
        findViewById<View>(R.id.cl_albums_box).setOnClickListener {
            startActivity(Intent(this, AlbumsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        // Artists Box Click
        findViewById<View>(R.id.cl_artists_box).setOnClickListener {
            startActivity(Intent(this, ArtistsActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        
        // Tracks Box Click
        findViewById<View>(R.id.cl_tracks_box).setOnClickListener {
            startActivity(Intent(this, TracksActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
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
        // Bind to service
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, R.id.nav_home)
        
        // Also check if already bound (unlikely to change between start and resume, but good for sync)
        if (isBound && musicService != null) {
            updateDot(musicService?.isPlaying() == true)
        }
        updateGreeting()
        updateHomeStats()
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

    private fun updateHomeStats() {
        // Favorites
        val favCount = FavoritesManager.getFavorites(this).size
        findViewById<TextView>(R.id.tv_fav_count).text = getString(R.string.home_fav_count, favCount)

        // Playlists
        var playlistCount = 0
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Playlists._ID),
                null, null, null
            )?.use { playlistCount = it.count }
        } catch (_: Exception) { }
        findViewById<TextView>(R.id.tv_playlist_count).text = getString(R.string.home_playlist_count, playlistCount)

        // Albums
        var albumCount = 0
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID),
                null, null, null
            )?.use { albumCount = it.count }
        } catch (_: Exception) { }
        findViewById<TextView>(R.id.tv_album_count).text = getString(R.string.home_album_count, albumCount)

        // Artists
        var artistCount = 0
        try {
            @Suppress("DEPRECATION")
            contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Artists._ID),
                null, null, null
            )?.use { artistCount = it.count }
        } catch (_: Exception) { }
        findViewById<TextView>(R.id.tv_artist_count).text = getString(R.string.home_artist_count, artistCount)
        
        // Tracks (excluding hidden tracks and filtered by duration)
        var trackCount = 0
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
                    
                    // Only count if not hidden AND meets duration filter
                    if (!HiddenTracksManager.isHidden(this, path) && duration >= minDurationMs) {
                        trackCount++
                    }
                }
            }
        } catch (_: Exception) { }
        findViewById<TextView>(R.id.tv_track_count).text = getString(R.string.home_track_count, trackCount)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
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
                    textView.text = getString(R.string.text_with_cursor, currentText)
                    
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
}
