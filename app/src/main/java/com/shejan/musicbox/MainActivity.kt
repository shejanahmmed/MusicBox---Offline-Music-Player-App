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

        setContentView(R.layout.activity_main)

        // Load User Name
        val userName = prefs.getString("USER_NAME", "LISTENER")?.uppercase() ?: "LISTENER"
        val greetingText = findViewById<android.widget.TextView>(R.id.tv_greeting)
        greetingText.text = getString(R.string.good_morning) + "\n" + userName

        // Helper to setup Nav clicks
        setupNavClick(R.id.nav_home, "Home")
        setupNavClick(R.id.nav_tracks, "Tracks")
        setupNavClick(R.id.nav_albums, "Albums")
        setupNavClick(R.id.nav_folders, "Folders")
        setupNavClick(R.id.nav_artists, "Artists")
        setupNavClick(R.id.nav_playlist, "Playlist")
        setupNavClick(R.id.nav_playlist, "Playlist")
        
        findViewById<android.view.View>(R.id.nav_search).setOnClickListener {
             startActivity(Intent(this, SearchActivity::class.java))
             overridePendingTransition(0, 0)
        }

        // Favorite Box Click
        findViewById<android.view.View>(R.id.cl_favorite_box).setOnClickListener {
            val intent = Intent(this, TracksActivity::class.java)
            intent.putExtra("SHOW_FAVORITES", true)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        
        // Settings Click
        findViewById<android.view.View>(R.id.btn_settings).setOnClickListener {
             android.widget.Toast.makeText(this, "Settings", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavClick(id: Int, name: String) {
        findViewById<android.view.View>(id).setOnClickListener {
            if (name == "Tracks") {
                val intent = Intent(this, TracksActivity::class.java)
                startActivity(intent)
                overridePendingTransition(0, 0) // No animation
            } else {
                android.widget.Toast.makeText(this, "Navigate to $name", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
