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

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri

import android.content.ComponentName
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        setupClickListeners()

        // Navigation
    setupNav()
    }

    private fun setupNav() {
        NavUtils.setupNavigation(this, R.id.nav_settings)
        
        val nav = findViewById<android.widget.LinearLayout>(R.id.nav_settings)
        if (nav != null) {
            val icon = nav.getChildAt(0) as android.widget.ImageView
            val text = nav.getChildAt(1) as android.widget.TextView
            icon.setColorFilter(getColor(R.color.colorNavSelected))
            text.setTextColor(getColor(R.color.colorNavSelected))
        }
    }

    private fun setupClickListeners() {
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)

        // Tab Order
        findViewById<android.view.View>(R.id.card_edit_name).setOnClickListener {
             val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
             @SuppressLint("InflateParams")
             val view = layoutInflater.inflate(R.layout.dialog_edit_name, null)
             dialog.setContentView(view)
             
             // Fix corners
             view.post {
                (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
             }
             
             val etName = view.findViewById<android.widget.EditText>(R.id.et_name)
             val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save)
             
             // Load current name
             val currentName = prefs.getString("USER_NAME", "Listener")
             etName.setText(currentName)
             
             btnSave.setOnClickListener {
                 val newName = etName.text.toString().trim()
                 if (newName.isNotEmpty()) {
                     prefs.edit { putString("USER_NAME", newName) }
                     Toast.makeText(this, "Name updated to $newName", Toast.LENGTH_SHORT).show()
                     dialog.dismiss()
                 } else {
                     Toast.makeText(this, "Please enter a valid name", Toast.LENGTH_SHORT).show()
                 }
             }
             
             dialog.show()
        }

        // Theme Selection
        findViewById<android.view.View>(R.id.card_theme).setOnClickListener {
            showThemeSelectionDialog()
        }

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

        // Video Duration Filter
        findViewById<android.view.View>(R.id.card_video_filter).setOnClickListener {
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
            @SuppressLint("InflateParams")
            val view = layoutInflater.inflate(R.layout.dialog_video_filter, null)
            dialog.setContentView(view)

            // Fix corners
            view.post {
                (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }

            val etMin = view.findViewById<android.widget.EditText>(R.id.et_min_duration)
            val etMax = view.findViewById<android.widget.EditText>(R.id.et_max_duration)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btn_save)

            // Load current values (stored separately in MusicBoxVideoPrefs)
            val videoPrefs = getSharedPreferences("MusicBoxVideoPrefs", MODE_PRIVATE)
            val currentMin = videoPrefs.getInt("video_min_duration_sec", 0)
            val currentMax = videoPrefs.getInt("video_max_duration_sec", 0)
            etMin.setText(currentMin.toString())
            etMax.setText(currentMax.toString())

            val tvMinHint = view.findViewById<android.widget.TextView>(R.id.tv_min_hint)
            val tvMaxHint = view.findViewById<android.widget.TextView>(R.id.tv_max_hint)

            fun secToLabel(sec: Int, isMax: Boolean): String {
                if (sec == 0) return if (isMax) "0 = unlimited" else "seconds"
                val mins = sec / 60
                val secs = sec % 60
                return when {
                    mins == 0 -> "${sec}s"
                    secs == 0 -> "${sec}s = ${mins} min"
                    else -> "${sec}s = ${mins} min ${secs} sec"
                }
            }

            // Set initial hints based on loaded values
            tvMinHint.text = secToLabel(currentMin, false)
            tvMaxHint.text = secToLabel(currentMax, true)

            etMin.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val v = s.toString().toIntOrNull() ?: 0
                    tvMinHint.text = secToLabel(v, false)
                }
            })

            etMax.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val v = s.toString().toIntOrNull() ?: 0
                    tvMaxHint.text = secToLabel(v, true)
                }
            })


            btnSave.setOnClickListener {
                val minInput = etMin.text.toString().toIntOrNull()
                val maxInput = etMax.text.toString().toIntOrNull()

                when {
                    minInput == null || minInput < 0 -> {
                        Toast.makeText(this, "Enter a valid minimum duration (0 or above)", Toast.LENGTH_SHORT).show()
                    }
                    maxInput == null || maxInput < 0 -> {
                        Toast.makeText(this, "Enter a valid maximum duration (0 = unlimited)", Toast.LENGTH_SHORT).show()
                    }
                    maxInput > 0 && minInput > maxInput -> {
                        Toast.makeText(this, "Minimum must be less than maximum", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        videoPrefs.edit {
                            putInt("video_min_duration_sec", minInput)
                            putInt("video_max_duration_sec", maxInput)
                        }
                        val msg = if (maxInput == 0)
                            "Videos: min ${minInput}s, no max limit"
                        else
                            "Videos: ${minInput}s – ${maxInput}s"
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }


        val switchHaptic = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_haptic_feedback)
        val cardHaptic = findViewById<android.view.View>(R.id.card_haptic_feedback)
        
        // Load preference
        switchHaptic.isChecked = prefs.getBoolean("haptic_feedback_enabled", false)
        
        // Toggle switch when card is clicked
        cardHaptic.setOnClickListener {
            switchHaptic.isChecked = !switchHaptic.isChecked
        }
        
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
             prefs.edit { putBoolean("haptic_feedback_enabled", isChecked) }
             val msg = if (isChecked) "Haptic feedback enabled" else "Haptic feedback disabled"
             Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
             
             // Feedback for the toggle itself
              if (isChecked) {
                  MusicUtils.performHapticFeedback(this)
              }
         }

        // Experimental Home Screen Widgets Toggle
        val switchWidgets = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_experimental_widgets)
        val cardWidgets = findViewById<android.view.View>(R.id.card_experimental_widgets)

        switchWidgets.isChecked = prefs.getBoolean("experimental_widgets_enabled", true)

        cardWidgets.setOnClickListener {
            switchWidgets.isChecked = !switchWidgets.isChecked
        }

        switchWidgets.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("experimental_widgets_enabled", isChecked) }
            val msg = if (isChecked) "Home screen widgets enabled" else "Home screen widgets disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

            try {
                val pm = packageManager
                val lightComponent = ComponentName(this, MusicWidgetProviderLight::class.java)
                val darkComponent = ComponentName(this, MusicWidgetProviderDark::class.java)
                val smallLightComponent = ComponentName(this, MusicWidgetProviderSmallLight::class.java)
                val smallDarkComponent = ComponentName(this, MusicWidgetProviderSmallDark::class.java)

                val state = if (isChecked) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

                pm.setComponentEnabledSetting(lightComponent, state, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(darkComponent, state, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(smallLightComponent, state, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(smallDarkComponent, state, PackageManager.DONT_KILL_APP)

                if (isChecked) {
                    BaseMusicWidgetProvider.updateAllWidgets(this)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Error toggling widgets state", e)
            }
        }


    // Scanning
        findViewById<android.view.View>(R.id.card_scanning).setOnClickListener {
            // Toast removed, dialog is shown inside scanMediaFiles
            scanMediaFiles()
        }

        // Customize Home Page
        findViewById<android.view.View>(R.id.card_customize_home).setOnClickListener {
            startActivity(Intent(this, HomeCustomizationActivity::class.java))
        }
        
        // Deleted Tracks
        findViewById<android.view.View>(R.id.card_deleted_tracks).setOnClickListener {
            startActivity(Intent(this, DeletedTracksActivity::class.java))
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
        
        // Privacy Policy
        findViewById<android.view.View>(R.id.card_privacy_policy).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://www.farjan.me/privacy-policy/".toUri())
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_browser_error, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // License
        findViewById<android.view.View>(R.id.card_license).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App?tab=GPL-3.0-1-ov-file".toUri())
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.open_browser_error, Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // About
        findViewById<android.view.View>(R.id.card_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // Version
        val tvVersionValue = findViewById<android.widget.TextView>(R.id.tv_version_value)
        tvVersionValue.text = getString(R.string.version_fmt, BuildConfig.VERSION_NAME)
        
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
        
        dialog.setCancelable(false)
        dialog.show()
        
        Toast.makeText(this, R.string.scanning_started, Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val startTime = System.currentTimeMillis()
                var count = 0
                
                // Query MediaStore for ALL audio files (including hidden tracks)
                contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.DATA),
                    "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null
                )?.use { cursor ->
                    count = cursor.count
                    runOnUiThread {
                        tvTrackCount.text = getString(R.string.tracks_found, count)
                    }
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
                
                // Trigger Refresh
                MusicUtils.contentVersion++
                sendBroadcast(Intent("com.shejan.musicbox.REFRESH_DATA").setPackage(packageName))

            } catch (e: Exception) {
                runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.scan_failed, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showThemeSelectionDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        @SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.dialog_theme, null)
        dialog.setContentView(view)

        // Fix corners
        view.post {
            (view.parent as? android.view.View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        val cardSystem = view.findViewById<android.view.View>(R.id.card_theme_system)
        val cardLight = view.findViewById<android.view.View>(R.id.card_theme_light)
        val cardDark = view.findViewById<android.view.View>(R.id.card_theme_dark)
        
        val checkSystem = view.findViewById<android.widget.ImageView>(R.id.iv_check_system)
        val checkLight = view.findViewById<android.widget.ImageView>(R.id.iv_check_light)
        val checkDark = view.findViewById<android.widget.ImageView>(R.id.iv_check_dark)

        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)

        fun updateUI(selectedMode: Int) {
            checkSystem.visibility = if (selectedMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) android.view.View.VISIBLE else android.view.View.GONE
            checkLight.visibility = if (selectedMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) android.view.View.VISIBLE else android.view.View.GONE
            checkDark.visibility = if (selectedMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        // Initialize UI
        updateUI(currentMode)

        fun selectTheme(mode: Int) {
            prefs.edit { putInt("theme_mode", mode) }
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
            updateUI(mode)
            dialog.dismiss()
            Toast.makeText(this, R.string.theme_updated, Toast.LENGTH_SHORT).show()
        }

        cardSystem.setOnClickListener { selectTheme(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        cardLight.setOnClickListener { selectTheme(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) }
        cardDark.setOnClickListener { selectTheme(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) }


        dialog.show()
    }
}



