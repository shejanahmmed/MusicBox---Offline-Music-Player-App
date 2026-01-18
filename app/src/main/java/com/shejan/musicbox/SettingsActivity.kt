package com.shejan.musicbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Back Button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        setupClickListeners()

        // Navigation
        NavUtils.setupNavigation(this, R.id.nav_settings)
    }

    private fun setupClickListeners() {
        // Tab Order
        findViewById<android.view.View>(R.id.card_tab_order).setOnClickListener {
            Toast.makeText(this, "Tab Order customization coming soon", Toast.LENGTH_SHORT).show()
        }

        // Album Length
        findViewById<android.view.View>(R.id.card_album_length).setOnClickListener {
            Toast.makeText(this, "Album Length Filter coming soon", Toast.LENGTH_SHORT).show()
        }


    // Scanning
        findViewById<android.view.View>(R.id.card_scanning).setOnClickListener {
            // Toast removed, dialog is shown inside scanMediaFiles
            scanMediaFiles()
        }

        // Pre-Release Notification
        val switchPreRelease = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_pre_release)
        val cardPreRelease = findViewById<android.view.View>(R.id.card_pre_release)
        
        // Toggle switch when card is clicked
        cardPreRelease.setOnClickListener {
            switchPreRelease.isChecked = !switchPreRelease.isChecked
        }
        
        switchPreRelease.setOnCheckedChangeListener { _, isChecked ->
             val msg = if (isChecked) "Pre-release notifications enabled" else "Pre-release notifications disabled"
             Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Github
        findViewById<android.view.View>(R.id.card_github).setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App"))
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Version
         findViewById<android.view.View>(R.id.card_version).setOnClickListener {
            Toast.makeText(this, "Version 1.0.24-BETA", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanMediaFiles() {
        // Show BottomSheetDialog
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_scanning, null)
        dialog.setContentView(view)
        
        // Fix corner background artifact
        view.post {
            (view.parent as android.view.View).background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        }

        val tvTrackCount = view.findViewById<android.widget.TextView>(R.id.tv_track_count)
        dialog.setCancelable(false)
        dialog.show()

        Thread {
            try {
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                val filesToScan = mutableListOf<String>()
                var count = 0
                
                musicDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.extension.equals("mp3", true) || file.extension.equals("m4a", true) || file.extension.equals("wav", true))) {
                        filesToScan.add(file.absolutePath)
                        count++
                        // Update UI periodically (every 5 files to avoid spamming UI thread, or just always for smooth effect if fast enough)
                        runOnUiThread {
                            tvTrackCount.text = "$count tracks found"
                        }
                    }
                }

                if (filesToScan.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        this,
                        filesToScan.toTypedArray(),
                        null
                    ) { _, _ -> 
                        // Optional: Callback
                    }
                }
                
                // artificial delay to show "Complete" state or just close
                Thread.sleep(1000)
                
                runOnUiThread {
                    if (filesToScan.isEmpty()) {
                         Toast.makeText(this, "No new music files found", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
