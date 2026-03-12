package com.shejan.musicbox

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners(view)
        
        // Version
        val tvVersionValue = view.findViewById<TextView>(R.id.tv_version_value)
        tvVersionValue.text = getString(R.string.version_fmt, BuildConfig.VERSION_NAME)
    }

    private fun setupClickListeners(view: View) {
        val prefs = requireContext().getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)

        // Edit Name
        view.findViewById<View>(R.id.card_edit_name).setOnClickListener {
            val dialog = BottomSheetDialog(requireContext())
            @SuppressLint("InflateParams")
            val dialogView = layoutInflater.inflate(R.layout.dialog_edit_name, null)
            dialog.setContentView(dialogView)
             
            dialogView.post {
                (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            }
             
            val etName = dialogView.findViewById<EditText>(R.id.et_name)
            val btnSave = dialogView.findViewById<Button>(R.id.btn_save)
             
            val currentName = prefs.getString("USER_NAME", "Listener")
            etName.setText(currentName)
             
            btnSave.setOnClickListener {
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    prefs.edit { putString("USER_NAME", newName) }
                    Toast.makeText(requireContext(), "Name updated to $newName", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid name", Toast.LENGTH_SHORT).show()
                }
            }
             
            dialog.show()
        }

        // Theme Selection
        view.findViewById<View>(R.id.card_theme).setOnClickListener {
            showThemeSelectionDialog()
        }

        // Tab Order
        view.findViewById<View>(R.id.card_tab_order).setOnClickListener {
             val dialog = BottomSheetDialog(requireContext())
             @SuppressLint("InflateParams")
             val dialogView = layoutInflater.inflate(R.layout.dialog_tab_order, null)
             dialog.setContentView(dialogView)
             
             dialogView.post {
                (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
             }
             
             val rvOrder = dialogView.findViewById<RecyclerView>(R.id.rv_tab_order)
             val btnSave = dialogView.findViewById<Button>(R.id.btn_save)
             
             rvOrder.layoutManager = LinearLayoutManager(requireContext())
             
             val currentTabs = TabManager.getTabOrder(requireContext()).toMutableList()
             val currentHome = TabManager.getHomeTabId(requireContext())
             
             val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
                 override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                     return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                 }
                 override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                     (rvOrder.adapter as? TabOrderAdapter)?.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                     return true
                 }
                 override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                 override fun isLongPressDragEnabled(): Boolean = false
             })
             itemTouchHelper.attachToRecyclerView(rvOrder)
             
             val adapter = TabOrderAdapter(currentTabs, currentHome, 
                 onHomeSelected = { _ -> },
                 onStartDrag = { holder ->
                     itemTouchHelper.startDrag(holder)
                 }
             )
             rvOrder.adapter = adapter
             
             btnSave.setOnClickListener {
                 Log.d("SettingsFragment", "Saving tab order: ${currentTabs.map { it.id }}")
                 TabManager.saveTabOrder(requireContext(), currentTabs)
                 TabManager.setHomeTabId(requireContext(), adapter.getCurrentHomeId())
                 
                 Toast.makeText(requireContext(), getString(R.string.nav_updated), Toast.LENGTH_SHORT).show()
                 dialog.dismiss()
                 
                 // Apply changes immediately (Needs activity cast/recreate or NavUtils update)
                 // Activity should probably be recreated to apply new tab order to BottomNav if it was dynamic
                 // For now, let's assume MainActivity handles BottomNav dynamically and a broadcast or refresh is needed.
                 // NavUtils.setupNavigation(requireActivity() as AppCompatActivity, R.id.nav_settings) -> this won't work perfectly in single activity if BottomNav is recreated.
                 requireActivity().recreate() 
             }
             
             dialog.show()
        }

        // Album Length
        view.findViewById<View>(R.id.card_album_length).setOnClickListener {
            val dialog = BottomSheetDialog(requireContext())
            @SuppressLint("InflateParams")
            val dialogView = layoutInflater.inflate(R.layout.dialog_album_length, null)
            dialog.setContentView(dialogView)
            
            dialogView.post {
                (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            }

            val etDuration = dialogView.findViewById<EditText>(R.id.et_duration)
            val btnSave = dialogView.findViewById<Button>(R.id.btn_save)
            
            val current = prefs.getInt("min_track_duration_sec", 10)
            etDuration.setText(current.toString())
            
            btnSave.setOnClickListener {
                val input = etDuration.text.toString().toIntOrNull()
                if (input != null && input >= 0) {
                    prefs.edit { putInt("min_track_duration_sec", input) }
                    Toast.makeText(requireContext(), getString(R.string.filter_updated, input), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), R.string.enter_valid_number, Toast.LENGTH_SHORT).show()
                }
            }
            
            dialog.show()
        }

        // Video Duration Filter
        view.findViewById<View>(R.id.card_video_filter).setOnClickListener {
            val dialog = BottomSheetDialog(requireContext())
            @SuppressLint("InflateParams")
            val dialogView = layoutInflater.inflate(R.layout.dialog_video_filter, null)
            dialog.setContentView(dialogView)

            dialogView.post {
                (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            }

            val etMin = dialogView.findViewById<EditText>(R.id.et_min_duration)
            val etMax = dialogView.findViewById<EditText>(R.id.et_max_duration)
            val btnSave = dialogView.findViewById<Button>(R.id.btn_save)

            val videoPrefs = requireContext().getSharedPreferences("MusicBoxVideoPrefs", Context.MODE_PRIVATE)
            val currentMin = videoPrefs.getInt("video_min_duration_sec", 0)
            val currentMax = videoPrefs.getInt("video_max_duration_sec", 0)
            etMin.setText(currentMin.toString())
            etMax.setText(currentMax.toString())

            val tvMinHint = dialogView.findViewById<TextView>(R.id.tv_min_hint)
            val tvMaxHint = dialogView.findViewById<TextView>(R.id.tv_max_hint)

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

            tvMinHint.text = secToLabel(currentMin, false)
            tvMaxHint.text = secToLabel(currentMax, true)

            etMin.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val v = s.toString().toIntOrNull() ?: 0
                    tvMinHint.text = secToLabel(v, false)
                }
            })

            etMax.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val v = s.toString().toIntOrNull() ?: 0
                    tvMaxHint.text = secToLabel(v, true)
                }
            })

            btnSave.setOnClickListener {
                val minInput = etMin.text.toString().toIntOrNull()
                val maxInput = etMax.text.toString().toIntOrNull()

                when {
                    minInput == null || minInput < 0 -> {
                        Toast.makeText(requireContext(), "Enter a valid minimum duration (0 or above)", Toast.LENGTH_SHORT).show()
                    }
                    maxInput == null || maxInput < 0 -> {
                        Toast.makeText(requireContext(), "Enter a valid maximum duration (0 = unlimited)", Toast.LENGTH_SHORT).show()
                    }
                    maxInput > 0 && minInput > maxInput -> {
                        Toast.makeText(requireContext(), "Minimum must be less than maximum", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            }

            dialog.show()
        }


        val switchHaptic = view.findViewById<SwitchMaterial>(R.id.switch_haptic_feedback)
        val cardHaptic = view.findViewById<View>(R.id.card_haptic_feedback)
        
        switchHaptic.isChecked = prefs.getBoolean("haptic_feedback_enabled", false)
        
        cardHaptic.setOnClickListener {
            switchHaptic.isChecked = !switchHaptic.isChecked
        }
        
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
             prefs.edit { putBoolean("haptic_feedback_enabled", isChecked) }
             val msg = if (isChecked) "Haptic feedback enabled" else "Haptic feedback disabled"
             Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
             
             if (isChecked) {
                 MusicUtils.performHapticFeedback(requireContext())
             }
        }


        // Scanning
        view.findViewById<View>(R.id.card_scanning).setOnClickListener {
            scanMediaFiles()
        }

        // Customize Home Page
        view.findViewById<View>(R.id.card_customize_home).setOnClickListener {
            startActivity(Intent(requireContext(), HomeCustomizationActivity::class.java))
        }
        
        // Deleted Tracks
        view.findViewById<View>(R.id.card_deleted_tracks).setOnClickListener {
            startActivity(Intent(requireContext(), DeletedTracksActivity::class.java))
        }

        // Github
        view.findViewById<View>(R.id.card_github).setOnClickListener {
             try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App"))
                startActivity(browserIntent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.open_browser_error, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Privacy Policy
        view.findViewById<View>(R.id.card_privacy_policy).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.farjan.me/privacy-policy/"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.open_browser_error, Toast.LENGTH_SHORT).show()
            }
        }

        // License
        view.findViewById<View>(R.id.card_license).setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App?tab=GPL-3.0-1-ov-file"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.open_browser_error, Toast.LENGTH_SHORT).show()
            }
        }

        // About
        view.findViewById<View>(R.id.card_about).setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
    }

    private fun scanMediaFiles() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_scanning, null)
        dialog.setContentView(dialogView)
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTrackCount = dialogView.findViewById<TextView>(R.id.tv_track_count)
        
        dialog.setCancelable(false)
        dialog.show()
        
        Toast.makeText(requireContext(), R.string.scanning_started, Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val startTime = System.currentTimeMillis()
                var count = 0
                
                requireContext().contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.DATA),
                    "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null
                )?.use { cursor ->
                    count = cursor.count
                    activity?.runOnUiThread {
                        if (isAdded) tvTrackCount.text = getString(R.string.tracks_found, count)
                    }
                }
                
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 1000) {
                    Thread.sleep(1000 - elapsedTime)
                }
                
                activity?.runOnUiThread {
                    if (isAdded) {
                        dialog.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.scanning_finished, count), Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Trigger Refresh
                MusicUtils.contentVersion++
                requireContext().sendBroadcast(Intent("com.shejan.musicbox.REFRESH_DATA").setPackage(requireContext().packageName))

            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        dialog.dismiss()
                        Toast.makeText(requireContext(), getString(R.string.scan_failed, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showThemeSelectionDialog() {
        val dialog = BottomSheetDialog(requireContext())
        @SuppressLint("InflateParams")
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme, null)
        dialog.setContentView(dialogView)

        dialogView.post {
            (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        }

        val cardSystem = dialogView.findViewById<View>(R.id.card_theme_system)
        val cardLight = dialogView.findViewById<View>(R.id.card_theme_light)
        val cardDark = dialogView.findViewById<View>(R.id.card_theme_dark)
        
        val checkSystem = dialogView.findViewById<ImageView>(R.id.iv_check_system)
        val checkLight = dialogView.findViewById<ImageView>(R.id.iv_check_light)
        val checkDark = dialogView.findViewById<ImageView>(R.id.iv_check_dark)

        val prefs = requireContext().getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES)

        fun updateUI(selectedMode: Int) {
            checkSystem.visibility = if (selectedMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) View.VISIBLE else View.GONE
            checkLight.visibility = if (selectedMode == AppCompatDelegate.MODE_NIGHT_NO) View.VISIBLE else View.GONE
            checkDark.visibility = if (selectedMode == AppCompatDelegate.MODE_NIGHT_YES) View.VISIBLE else View.GONE
        }
        
        updateUI(currentMode)

        fun selectTheme(mode: Int) {
            prefs.edit { putInt("theme_mode", mode) }
            AppCompatDelegate.setDefaultNightMode(mode)
            updateUI(mode)
            dialog.dismiss()
            Toast.makeText(requireContext(), R.string.theme_updated, Toast.LENGTH_SHORT).show()
        }

        cardSystem.setOnClickListener { selectTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        cardLight.setOnClickListener { selectTheme(AppCompatDelegate.MODE_NIGHT_NO) }
        cardDark.setOnClickListener { selectTheme(AppCompatDelegate.MODE_NIGHT_YES) }

        dialog.show()
    }
}
