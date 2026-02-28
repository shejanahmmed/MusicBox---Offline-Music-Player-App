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

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideosActivity : AppCompatActivity() {

    private val requestCodePermission = 2001

    // Sort state
    private var sortColumn = MediaStore.Video.Media.TITLE
    private var isAscending = true

    private var musicService: MusicService? = null
    private var isBound = false

    private var adapter: VideoAdapter? = null

    // ── Service Connection ──────────────────────────────────────────────────────

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateMiniPlayer()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    // ── Broadcast Receiver ──────────────────────────────────────────────────────

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateMiniPlayer()
            }
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        setContentView(R.layout.activity_videos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        loadSortPrefs()

        val rvVideos = findViewById<RecyclerView>(R.id.rv_videos)
        rvVideos.layoutManager = LinearLayoutManager(this)
        adapter = VideoAdapter(emptyList()) { video -> showVideoOptions(video) }
        rvVideos.adapter = adapter

        if (!checkPermission()) {
            requestPermission()
        }

        // Sort button
        findViewById<View>(R.id.btn_sort).setOnClickListener {
            showSortDialog()
        }

        // Shuffle button
        findViewById<View>(R.id.btn_header_shuffle).setOnClickListener {
            if (isBound && musicService != null) {
                musicService?.toggleShuffle()
                updateUI()
            }
        }

        MiniPlayerManager.setup(this) { musicService }

        NavUtils.setupNavigation(this, R.id.nav_videos)

        // Register broadcast
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), connection, BIND_AUTO_CREATE)
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
        updateMiniPlayer()
        NavUtils.setupNavigation(this, R.id.nav_videos)
        if (checkPermission()) {
            loadVideos()   // re-apply duration filter and refresh list on every resume
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    // ── Permission ──────────────────────────────────────────────────────────────

    private fun checkPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(this, arrayOf(perm), requestCodePermission)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermission) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission required to show videos.", Toast.LENGTH_LONG).show()
            }
            // If permission granted, onResume will call loadVideos()
        }
    }

    // ── Data Loading ────────────────────────────────────────────────────────────

    private fun loadVideos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val videoList = queryVideos(applicationContext)
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext

                val countText = when (videoList.size) {
                    0 -> "0 Videos"
                    1 -> "1 Video"
                    else -> "${videoList.size} Videos"
                }
                findViewById<TextView>(R.id.tv_videos_count)?.text = countText

                adapter?.updateData(videoList)

                if (videoList.isEmpty()) {
                    Toast.makeText(
                        this@VideosActivity,
                        "No videos found on device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun queryVideos(context: Context): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE
            )

            val order = if (isAscending) "ASC" else "DESC"
            val sortOrder = "$sortColumn $order"

            // Read duration filter from dedicated video prefs (separate from audio track filter)
            val videoPrefs = context.getSharedPreferences("MusicBoxVideoPrefs", MODE_PRIVATE)
            val minSec = videoPrefs.getInt("video_min_duration_sec", 0)
            val maxSec = videoPrefs.getInt("video_max_duration_sec", 0)
            val minMs = minSec * 1000L
            val maxMs = if (maxSec > 0) maxSec * 1000L else Long.MAX_VALUE

            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Video"
                    val duration = it.getLong(durationCol)
                    val path = it.getString(dataCol) ?: continue
                    val size = it.getLong(sizeCol)

                    // Apply duration filter
                    if (duration < minMs || duration > maxMs) continue

                    list.add(VideoItem(id, title, duration, path, size))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }


    // ── UI Updates ──────────────────────────────────────────────────────────────

    private fun updateMiniPlayer() {
        updateUI()
    }

    private fun updateUI() {
        val shuffleBtn = findViewById<ImageButton>(R.id.btn_header_shuffle)
        if (MusicService.isShuffleEnabled) {
            shuffleBtn?.setColorFilter(getColor(R.color.primary_red))
        } else {
            shuffleBtn?.setColorFilter(ContextCompat.getColor(this, R.color.colorIconSecondary))
        }

        MiniPlayerManager.update(this, musicService)

        // Highlight currently playing video if it comes from this page
        var currentTrack: Track? = null
        if (isBound && musicService != null) {
            currentTrack = musicService?.getCurrentTrack()
        } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
            currentTrack = MusicService.playlist[MusicService.currentIndex]
        }
        adapter?.updateActiveVideo(currentTrack?.id ?: -1L)
    }

    // ── Sort Dialog ─────────────────────────────────────────────────────────────

    @SuppressLint("InflateParams")
    private fun showSortDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_sort, null)
        dialog.setContentView(view)

        view.post {
            (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        }

        val switchAsc = view.findViewById<SwitchMaterial>(R.id.switch_ascending)
        val containerTitle = view.findViewById<View>(R.id.container_title)
        val containerDateAdded = view.findViewById<View>(R.id.container_date_added)
        val containerDateModified = view.findViewById<View>(R.id.container_date_modified)

        val rbTitle = view.findViewById<RadioButton>(R.id.rb_title)
        val rbDateAdded = view.findViewById<RadioButton>(R.id.rb_date_added)
        val rbDateModified = view.findViewById<RadioButton>(R.id.rb_date_modified)

        fun updateSelection(selectedRb: RadioButton) {
            rbTitle.isChecked = false
            rbDateAdded.isChecked = false
            rbDateModified.isChecked = false
            selectedRb.isChecked = true
        }

        switchAsc.isChecked = isAscending
        when (sortColumn) {
            MediaStore.Video.Media.TITLE -> updateSelection(rbTitle)
            MediaStore.Video.Media.DATE_ADDED -> updateSelection(rbDateAdded)
            MediaStore.Video.Media.DATE_MODIFIED -> updateSelection(rbDateModified)
        }

        fun saveSortPrefs() {
            getSharedPreferences("MusicBoxVideoPrefs", MODE_PRIVATE).edit().apply {
                putString("video_sort_column", sortColumn)
                putBoolean("video_is_ascending", isAscending)
                apply()
            }
        }

        switchAsc.setOnCheckedChangeListener { _, isChecked ->
            isAscending = isChecked
            saveSortPrefs()
            loadVideos()
        }

        containerTitle.setOnClickListener {
            updateSelection(rbTitle)
            sortColumn = MediaStore.Video.Media.TITLE
            saveSortPrefs()
            loadVideos()
            dialog.dismiss()
        }

        containerDateAdded.setOnClickListener {
            updateSelection(rbDateAdded)
            sortColumn = MediaStore.Video.Media.DATE_ADDED
            saveSortPrefs()
            loadVideos()
            dialog.dismiss()
        }

        containerDateModified.setOnClickListener {
            updateSelection(rbDateModified)
            sortColumn = MediaStore.Video.Media.DATE_MODIFIED
            saveSortPrefs()
            loadVideos()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadSortPrefs() {
        val prefs = getSharedPreferences("MusicBoxVideoPrefs", MODE_PRIVATE)
        sortColumn = prefs.getString("video_sort_column", MediaStore.Video.Media.TITLE)
            ?: MediaStore.Video.Media.TITLE
        isAscending = prefs.getBoolean("video_is_ascending", true)
    }

    // ── Video Options ───────────────────────────────────────────────────────────

    private fun showVideoOptions(video: VideoItem) {
        // Convert VideoItem → Track so we can reuse the full TrackMenuManager options dialog
        val track = Track(
            id = video.id,
            title = video.title,
            artist = "Video",
            uri = video.uri,
            album = null,
            albumId = -1L
        )
        TrackMenuManager.showTrackOptionsDialog(
            activity = this,
            track = track,
            pickArtworkLauncher = null,     // artwork editing not applicable for videos
            callback = object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {}
                override fun onTrackDeleted() { loadVideos() }
                override fun onTrackUpdated() { loadVideos() }
            }
        )
    }

}



