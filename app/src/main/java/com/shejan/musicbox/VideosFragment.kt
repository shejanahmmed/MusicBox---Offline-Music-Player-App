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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideosFragment : Fragment() {

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
        loadSortPrefs()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_videos, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvVideos = view.findViewById<RecyclerView>(R.id.rv_videos)
        rvVideos.layoutManager = LinearLayoutManager(requireContext())
        adapter = VideoAdapter(emptyList()) { video -> showVideoOptions(video) }
        rvVideos.adapter = adapter

        if (!checkPermission()) {
            requestPermission()
        } else {
            loadVideos()
        }

        view.findViewById<View>(R.id.btn_sort).setOnClickListener {
            showSortDialog()
        }

        view.findViewById<View>(R.id.btn_header_shuffle).setOnClickListener {
            if (isBound && musicService != null) {
                musicService?.toggleShuffle()
                updateUI()
                
                val msg = if (MusicService.isShuffleEnabled) "Shuffle On" else "Shuffle Off"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(requireContext(), receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        try { requireContext().unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
        if (checkPermission()) {
            loadVideos()
        }
    }

    // ── Permission ──────────────────────────────────────────────────────────────

    private fun checkPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(requireContext(), perm) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun requestPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissions(arrayOf(perm), requestCodePermission)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == requestCodePermission) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Storage permission required to show videos.", Toast.LENGTH_LONG).show()
            } else {
                loadVideos()
            }
        }
    }

    // ── Data Loading ────────────────────────────────────────────────────────────

    private fun loadVideos() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val videoList = queryVideos(requireContext().applicationContext)
            withContext(Dispatchers.Main) {
                val view = view ?: return@withContext

                val countText = when (videoList.size) {
                    0 -> "0 Videos"
                    1 -> "1 Video"
                    else -> "${videoList.size} Videos"
                }
                view.findViewById<TextView>(R.id.tv_videos_count)?.text = countText

                adapter?.updateData(videoList)

                if (videoList.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
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

            val videoPrefs = context.getSharedPreferences("MusicBoxVideoPrefs", Context.MODE_PRIVATE)
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
        val view = view ?: return
        val shuffleBtn = view.findViewById<ImageButton>(R.id.btn_header_shuffle)
        val context = requireContext()

        if (MusicService.isShuffleEnabled) {
            shuffleBtn?.setColorFilter(Color.WHITE)
        } else {
            shuffleBtn?.setColorFilter(Color.parseColor("#80FFFFFF"))
        }
        shuffleBtn?.alpha = 1.0f

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
        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort, null)
        dialog.setContentView(dialogView)

        dialogView.post {
            (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        }

        val switchAsc = dialogView.findViewById<SwitchMaterial>(R.id.switch_ascending)
        val containerTitle = dialogView.findViewById<View>(R.id.container_title)
        val containerDateAdded = dialogView.findViewById<View>(R.id.container_date_added)
        val containerDateModified = dialogView.findViewById<View>(R.id.container_date_modified)

        val rbTitle = dialogView.findViewById<RadioButton>(R.id.rb_title)
        val rbDateAdded = dialogView.findViewById<RadioButton>(R.id.rb_date_added)
        val rbDateModified = dialogView.findViewById<RadioButton>(R.id.rb_date_modified)

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
            requireContext().getSharedPreferences("MusicBoxVideoPrefs", Context.MODE_PRIVATE).edit().apply {
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
        val prefs = requireContext().getSharedPreferences("MusicBoxVideoPrefs", Context.MODE_PRIVATE)
        sortColumn = prefs.getString("video_sort_column", MediaStore.Video.Media.TITLE)
            ?: MediaStore.Video.Media.TITLE
        isAscending = prefs.getBoolean("video_is_ascending", true)
    }

    // ── Video Options ───────────────────────────────────────────────────────────

    private fun showVideoOptions(video: VideoItem) {
        val track = Track(
            id = video.id,
            title = video.title,
            artist = "Video",
            uri = video.uri,
            album = null,
            albumId = -1L
        )
        TrackMenuManager.showTrackOptionsDialog(
            activity = requireActivity() as AppCompatActivity,
            track = track,
            pickArtworkLauncher = null,
            callback = object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {}
                override fun onTrackDeleted() { loadVideos() }
                override fun onTrackUpdated() { loadVideos() }
            }
        )
    }
}
