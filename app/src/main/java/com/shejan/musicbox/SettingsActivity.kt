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
             val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
             val view = layoutInflater.inflate(R.layout.dialog_tab_order, null)
             dialog.setContentView(view)
             
             // Fix corners
             view.post {
                (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
             }
             
             val rvOrder = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_tab_order)
             val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save)
             
             rvOrder.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
             
             // Get current order
             val currentTabs = TabManager.getTabOrder(this).toMutableList()
             val currentHome = TabManager.getHomeTabId(this)
             
             val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.Callback() {
                 override fun getMovementFlags(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder): Int {
                     return makeMovementFlags(androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0)
                 }
                 override fun onMove(recyclerView: androidx.recyclerview.widget.RecyclerView, viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                     (rvOrder.adapter as? TabOrderAdapter)?.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                     return true
                 }
                 override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
                 override fun isLongPressDragEnabled(): Boolean = false // We use handle
             })
             itemTouchHelper.attachToRecyclerView(rvOrder)
             
             val adapter = TabOrderAdapter(currentTabs, currentHome, 
                 onHomeSelected = { newHomeId ->
                     // No-op, adapter updates internally, we read it back on save? 
                     // Actually adapter has currentHomeId var.
                 },
                 onStartDrag = { holder ->
                     itemTouchHelper.startDrag(holder)
                 }
             )
             rvOrder.adapter = adapter
             
             btnSave.setOnClickListener {
                 android.util.Log.d("SettingsActivity", "Saving tab order: ${currentTabs.map { it.id }}")
                 // Save Order
                 TabManager.saveTabOrder(this, currentTabs)
                 // Save Home
                 TabManager.setHomeTabId(this, adapter.getCurrentHomeId())
                 
                 Toast.makeText(this, "Navigation updated.", Toast.LENGTH_SHORT).show()
                 dialog.dismiss()
                 
                 // Apply changes immediately
                 NavUtils.setupNavigation(this, R.id.nav_settings)
             }
             
             dialog.show()
        }

        // Album Length
        findViewById<android.view.View>(R.id.card_album_length).setOnClickListener {
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_album_length, null)
            dialog.setContentView(view)
            
            // Fix corners
            view.post {
                (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            val etDuration = view.findViewById<android.widget.EditText>(R.id.et_duration)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save)
            
            // Load current
            val prefs = getSharedPreferences("MusicBoxPrefs", android.content.Context.MODE_PRIVATE)
            val current = prefs.getInt("min_track_duration_sec", 10)
            etDuration.setText(current.toString())
            
            btnSave.setOnClickListener {
                val input = etDuration.text.toString().toIntOrNull()
                if (input != null && input >= 0) {
                    prefs.edit().putInt("min_track_duration_sec", input).apply()
                    Toast.makeText(this, "Filter updated: tracks shorter than ${input}s will be hidden", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            
            dialog.show()
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
