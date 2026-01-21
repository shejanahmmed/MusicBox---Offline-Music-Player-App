package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import android.annotation.SuppressLint
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Back Button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        setupClickListeners()

        // Navigation
    }

    private fun setupClickListeners() {
        // Tab Order
        findViewById<android.view.View>(R.id.card_tab_order).setOnClickListener {
             val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
             @SuppressLint("InflateParams")
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
                 onHomeSelected = { _ ->
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
                 
                 Toast.makeText(this, getString(R.string.nav_updated), Toast.LENGTH_SHORT).show()
                 dialog.dismiss()
                 
                 // Apply changes immediately
                 NavUtils.setupNavigation(this, R.id.nav_settings)
             }
             
             dialog.show()
        }

        // Album Length
        findViewById<android.view.View>(R.id.card_album_length).setOnClickListener {
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            @SuppressLint("InflateParams")
            val view = layoutInflater.inflate(R.layout.dialog_album_length, null)
            dialog.setContentView(view)
            
            // Fix corners
            view.post {
                (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            val etDuration = view.findViewById<android.widget.EditText>(R.id.et_duration)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save)
            
            // Load current
            val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
            val current = prefs.getInt("min_track_duration_sec", 10)
            etDuration.setText(current.toString())
            
            btnSave.setOnClickListener {
                val input = etDuration.text.toString().toIntOrNull()
                if (input != null && input >= 0) {
                    prefs.edit { putInt("min_track_duration_sec", input) }
                    Toast.makeText(this, getString(R.string.filter_updated, input), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
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
             val msg = if (isChecked) getString(R.string.notifications_enabled) else getString(R.string.notifications_disabled)
             Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Github
        findViewById<android.view.View>(R.id.card_github).setOnClickListener {
             try {
                val browserIntent = Intent(Intent.ACTION_VIEW, "https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App".toUri())
                startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(this, R.string.open_browser_error, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Version
         findViewById<android.view.View>(R.id.card_version).setOnClickListener {
            Toast.makeText(this, R.string.version_text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanMediaFiles() {
        // Show Center Popup Dialog
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        @SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.dialog_scanning, null)
        dialog.setContentView(view)
        
        // Transparent background for rounded corners
        dialog.window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        val tvTrackCount = view.findViewById<android.widget.TextView>(R.id.tv_track_count)
        // val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progress_scanning)
        
        dialog.setCancelable(false)
        dialog.show()
        
        Toast.makeText(this, R.string.scanning_started, Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val startTime = System.currentTimeMillis()
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                val filesToScan = mutableListOf<String>()
                var count = 0
                
                musicDir.walkTopDown().forEach { file ->
                    if (file.isFile && (file.extension.equals("mp3", true) || file.extension.equals("m4a", true) || file.extension.equals("wav", true))) {
                        filesToScan.add(file.absolutePath)
                        count++
                        runOnUiThread {
                            tvTrackCount.text = getString(R.string.tracks_found, count)
                        }
                    }
                }

                if (filesToScan.isNotEmpty()) {
                    android.media.MediaScannerConnection.scanFile(
                        this,
                        filesToScan.toTypedArray(),
                        null
                    ) { _, _ -> }
                }
                
                // FORCE MINIMUM DURATION: Ensure animation runs for at least 1 second
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 1000) {
                    Thread.sleep(1000 - elapsedTime)
                }
                
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.scanning_finished, count), Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.scan_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
