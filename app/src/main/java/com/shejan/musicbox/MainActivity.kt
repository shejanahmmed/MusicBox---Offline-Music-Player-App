package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

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
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
             if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
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

        // Load User Name
        val userName = prefs.getString("USER_NAME", "LISTENER")?.uppercase() ?: "LISTENER"
        val greetingText = findViewById<android.widget.TextView>(R.id.tv_greeting)
        val fullInfo = getString(R.string.good_morning) + "\n" + userName
        typeWriterEffect(greetingText, fullInfo)

        // Helper to setup Nav clicks
        NavUtils.setupNavigation(this, R.id.nav_home)

        // Favorite Box Click
        findViewById<android.view.View>(R.id.cl_favorite_box).setOnClickListener {
            val intent = Intent(this, TracksActivity::class.java)
            intent.putExtra("SHOW_FAVORITES", true)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        
        // Playlist Box Click
        findViewById<android.view.View>(R.id.cl_playlist_box).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
            overridePendingTransition(0, 0)
        }
        
        // Albums Box Click
        findViewById<android.view.View>(R.id.cl_albums_box).setOnClickListener {
            startActivity(Intent(this, AlbumsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        
        // Artists Box Click
        findViewById<android.view.View>(R.id.cl_artists_box).setOnClickListener {
            startActivity(Intent(this, ArtistsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        

    }

    private val updateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                val isPlaying = intent.getBooleanExtra("IS_PLAYING", false)
                updateDot(isPlaying)
            }
        }
    }

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            // Sync state immediately
            updateDot(musicService?.isPlaying() == true)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private fun updateDot(isPlaying: Boolean) {
        val dot = findViewById<android.view.View>(R.id.v_red_dot)
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
        val filter = android.content.IntentFilter("MUSIC_BOX_UPDATE")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        
        // Also check if already bound (unlikely to change between start and resume, but good for sync)
        if (isBound && musicService != null) {
            updateDot(musicService?.isPlaying() == true)
        }
        updateHomeStats()
    }

    private fun updateHomeStats() {
        // Favorites
        val favCount = FavoritesManager.getFavorites(this).size
        findViewById<android.widget.TextView>(R.id.tv_fav_count).text = "$favCount Tracks"

        // Playlists
        var playlistCount = 0
        try {
            contentResolver.query(
                android.provider.MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Audio.Playlists._ID),
                null, null, null
            )?.use { playlistCount = it.count }
        } catch (e: Exception) { }
        findViewById<android.widget.TextView>(R.id.tv_playlist_count).text = "$playlistCount Playlists"

        // Albums
        var albumCount = 0
        try {
            contentResolver.query(
                android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Audio.Albums._ID),
                null, null, null
            )?.use { albumCount = it.count }
        } catch (e: Exception) { }
        findViewById<android.widget.TextView>(R.id.tv_album_count).text = "$albumCount Albums"

        // Artists
        var artistCount = 0
        try {
            contentResolver.query(
                android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Audio.Artists._ID),
                null, null, null
            )?.use { artistCount = it.count }
        } catch (e: Exception) { }
        findViewById<android.widget.TextView>(R.id.tv_artist_count).text = "$artistCount Artists"
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(updateReceiver)
    }

    private fun typeWriterEffect(textView: android.widget.TextView, text: String, delay: Long = 50) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var index = 0
        
        val runnable = object : Runnable {
            override fun run() {
                if (index <= text.length) {
                    // Show cursor while typing
                    val currentText = text.subSequence(0, index).toString()
                    textView.text = "$currentText|"
                    
                    if (index < text.length) {
                        index++
                        handler.postDelayed(this, delay)
                    } else {
                        // Finished typing, remove cursor after a moment or make it blink?
                        // For now, let's remove it after a short pause to finalize the effect
                         handler.postDelayed({
                             textView.text = text
                         }, 800)
                    }
                }
            }
        }
        handler.post(runnable)
    }
}
