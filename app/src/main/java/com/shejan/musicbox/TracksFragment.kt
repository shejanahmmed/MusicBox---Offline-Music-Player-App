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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TracksFragment : Fragment() {

    private val requestCodeReadStorage = 1001
    private var musicService: MusicService? = null
    private var isBound = false
    private var initialScrollDone = false

    // Sort State
    private var sortColumn = MediaStore.Audio.Media.TITLE
    private var isAscending = true
    private var localContentVersion: Long = 0

    // Artwork Picker
    private val pickArtworkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val trackUri = currentEditingTrackUri
            if (trackUri != null) {
                TrackArtworkManager.saveArtworkFromUri(requireContext(), trackUri, uri)
                ImageLoader.clearCacheForTrack(trackUri)
                MusicUtils.contentVersion++
                updateMiniPlayer()
                loadTracks()
            } else {
                Toast.makeText(requireContext(), "Error: Track info lost", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var currentEditingTrackUri: String? = null
    private var isEditingPlaylist = false
    private var adapter: TrackAdapter? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                updateMiniPlayer()
            } else if (intent?.action == "com.shejan.musicbox.TRACK_DELETED" || 
                       intent?.action == "com.shejan.musicbox.REFRESH_DATA" || 
                       intent?.action == FavoritesManager.ACTION_FAVORITES_UPDATED) {
                loadTracks()
                updateMiniPlayer()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            updateMiniPlayer()
            attemptScrollToActiveTrack()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = requireContext().getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        sortColumn = prefs.getString("sort_column", MediaStore.Audio.Media.TITLE) ?: MediaStore.Audio.Media.TITLE
        isAscending = prefs.getBoolean("is_ascending", true)

        if (savedInstanceState != null) {
            currentEditingTrackUri = savedInstanceState.getString("EDITING_TRACK_URI")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tracks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvTracks = view.findViewById<RecyclerView>(R.id.rv_tracks)
        rvTracks.layoutManager = LinearLayoutManager(requireContext())

        adapter = TrackAdapter(emptyList()) { track ->
            showTrackOptionsDialog(track)
        }
        rvTracks.adapter = adapter

        // Setup ItemTouchHelper for custom preference drag-and-drop
        val touchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean {
                return sortColumn == "custom_preference"
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                
                adapter?.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No-op
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.8f
                    viewHolder?.itemView?.scaleX = 1.03f
                    viewHolder?.itemView?.scaleY = 1.03f
                    viewHolder?.itemView?.context?.let { ctx ->
                        MusicUtils.performHapticFeedback(ctx)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                viewHolder.itemView.scaleX = 1.0f
                viewHolder.itemView.scaleY = 1.0f
                
                adapter?.let { adapterInstance ->
                    val context = recyclerView.context
                    val currentTracks = adapterInstance.getTracks()
                    val paths = currentTracks.map { it.uri }
                    CustomSortHelper.saveCustomOrder(context, paths)
                    
                    if (isBound && musicService != null) {
                        val currentTrackId = musicService?.getCurrentTrack()?.id ?: -1L
                        if (currentTrackId != -1L) {
                            val newIndex = currentTracks.indexOfFirst { it.id == currentTrackId }
                            if (newIndex != -1) {
                                MusicService.updatePlaylist(currentTracks, newIndex)
                            }
                        }
                    }
                }
            }
        }
        ItemTouchHelper(touchCallback).attachToRecyclerView(rvTracks)

        // ── Alphabet Index Scrollbar ──────────────────────────────────────
        val alphabetScrollbar = view.findViewById<AlphabetIndexScrollbar>(R.id.alphabet_scrollbar)
        val letterBubble = view.findViewById<TextView>(R.id.tv_letter_bubble)
        var hideBubbleRunnable: Runnable? = null

        alphabetScrollbar.onLetterSelected = { letter ->
            val currentAdapter = rvTracks.adapter as? TrackAdapter
            if (currentAdapter != null) {
                val index = currentAdapter.getFirstIndexForLetter(letter)
                if (index != -1) {
                    (rvTracks.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(index, 0)
                }

                // Show animated letter bubble
                letterBubble.text = letter
                hideBubbleRunnable?.let { letterBubble.removeCallbacks(it) }
                letterBubble.animate().setListener(null).cancel()

                if (letterBubble.visibility != View.VISIBLE) {
                    letterBubble.alpha = 0f
                    letterBubble.scaleX = 0.5f
                    letterBubble.scaleY = 0.5f
                    letterBubble.visibility = View.VISIBLE
                    letterBubble.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator())
                        .setListener(null)
                        .start()
                } else {
                    letterBubble.alpha = 1f
                    letterBubble.scaleX = 1f
                    letterBubble.scaleY = 1f
                }
            }
        }

        alphabetScrollbar.onDragEnded = {
            hideBubbleRunnable?.let { letterBubble.removeCallbacks(it) }
            hideBubbleRunnable = Runnable {
                letterBubble.animate()
                    .alpha(0f).scaleX(0.5f).scaleY(0.5f)
                    .setDuration(200)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            letterBubble.visibility = View.GONE
                            letterBubble.alpha = 1f
                            letterBubble.scaleX = 1f
                            letterBubble.scaleY = 1f
                        }
                    })
                    .start()
            }.also { letterBubble.postDelayed(it, 800) }
        }
        // ─────────────────────────────────────────────────────────────────

        if (checkPermission()) {
            loadTracks()
        } else {
            requestPermission()
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

        view.findViewById<View>(R.id.btn_header_repeat).setOnClickListener {
            if (isBound && musicService != null) {
                musicService?.toggleRepeat()
                updateUI()

                val mode = MusicService.repeatMode
                val msg = when (mode) {
                    MusicService.REPEAT_ALL -> "Repeat All"
                    MusicService.REPEAT_ONE -> "Repeat One"
                    else -> "Repeat Off"
                }
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(requireContext(), MusicService::class.java)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)

        val filter = IntentFilter("MUSIC_BOX_UPDATE").apply {
            addAction("com.shejan.musicbox.TRACK_DELETED")
            addAction("com.shejan.musicbox.REFRESH_DATA")
            addAction(FavoritesManager.ACTION_FAVORITES_UPDATED)
        }
        ContextCompat.registerReceiver(
            requireContext(),
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireContext().unbindService(connection)
            isBound = false
        }
        try {
            requireContext().unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {}
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("EDITING_TRACK_URI", currentEditingTrackUri)
    }

    override fun onResume() {
        super.onResume()
        if (localContentVersion != MusicUtils.contentVersion) {
            loadTracks()
        }
        updateMiniPlayer()

        if (isEditingPlaylist) {
            loadTracks()
            isEditingPlaylist = false
        }

        initialScrollDone = false
        attemptScrollToActiveTrack()
    }

    private fun updateMiniPlayer() {
        updateUI()
    }

    private fun updateUI() {
        val view = view ?: return
        val shuffleBtn = view.findViewById<ImageButton>(R.id.btn_header_shuffle)
        val repeatBtn = view.findViewById<ImageButton>(R.id.btn_header_repeat)

        val context = requireContext()

        if (MusicService.isShuffleEnabled) {
            shuffleBtn.setColorFilter(Color.WHITE)
        } else {
            shuffleBtn.setColorFilter(Color.parseColor("#80FFFFFF"))
        }
        shuffleBtn.alpha = 1.0f

        if (MusicService.repeatMode != MusicService.REPEAT_OFF) {
            repeatBtn.setColorFilter(Color.WHITE)
        } else {
            repeatBtn.setColorFilter(Color.parseColor("#80FFFFFF"))
        }
        repeatBtn.alpha = 1.0f

        var track: Track? = null
        if (isBound && musicService != null) {
            track = musicService?.getCurrentTrack()
        } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
            track = MusicService.playlist[MusicService.currentIndex]
        }

        val adapter = view.findViewById<RecyclerView>(R.id.rv_tracks).adapter as? TrackAdapter
        adapter?.updateActiveTrack(track?.id ?: -1L)
    }

    private fun checkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun requestPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissions(arrayOf(permission), requestCodeReadStorage)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == requestCodeReadStorage) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadTracks()
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_storage_permission), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadTracks() {
        val arguments = arguments
        val showFavoritesOnly = arguments?.getBoolean("SHOW_FAVORITES", false) ?: false
        val playlistId = arguments?.getLong("PLAYLIST_ID", -1L) ?: -1L
        val playlistName = arguments?.getString("PLAYLIST_NAME")
        val artistName = arguments?.getString("ARTIST_NAME")
        val albumName = arguments?.getString("ALBUM_NAME")

        localContentVersion = MusicUtils.contentVersion

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
            val trackList: List<Track> = if (showFavoritesOnly) {
                val favorites = FavoritesManager.getFavorites(appContext)
                getTracks(appContext, null, null).filter { favorites.contains(it.uri) }
            } else if (playlistId != -1L) {
                getPlaylistTracks(appContext, playlistId)
            } else if (artistName != null) {
                getTracks(appContext, "${MediaStore.Audio.Media.ARTIST} = ?", arrayOf(artistName))
            } else if (albumName != null) {
                getTracks(appContext, "${MediaStore.Audio.Media.ALBUM} = ?", arrayOf(albumName))
            } else {
                getTracks(appContext, null, null)
            }

            withContext(Dispatchers.Main) {
                val view = view ?: return@withContext

                if (showFavoritesOnly) view.findViewById<TextView>(R.id.tv_header_title)?.text = getString(R.string.title_favorites)
                else if (playlistId != -1L) {
                    val playlist = AppPlaylistManager.getPlaylist(requireContext(), playlistId)
                    val displayName = playlist?.name ?: playlistName ?: "PLAYLIST"
                    view.findViewById<TextView>(R.id.tv_header_title)?.text = displayName.uppercase()

                    val btnEdit = view.findViewById<View>(R.id.btn_edit)
                    btnEdit.visibility = View.VISIBLE
                    btnEdit.setOnClickListener {
                        val intent = Intent(requireContext(), CreatePlaylistActivity::class.java)
                        intent.putExtra("EDIT_PLAYLIST_ID", playlistId)
                        intent.putExtra("PLAYLIST_NAME", playlistName)
                        isEditingPlaylist = true
                        startActivity(intent)
                    }
                } else if (artistName != null) view.findViewById<TextView>(R.id.tv_header_title)?.text = artistName.uppercase()
                else if (albumName != null) view.findViewById<TextView>(R.id.tv_header_title)?.text = albumName.uppercase()
                else view.findViewById<TextView>(R.id.tv_header_title)?.text = getString(R.string.tab_tracks).uppercase()

                if (trackList.isEmpty()) {
                    val msg = when {
                        showFavoritesOnly -> getString(R.string.msg_no_favorites)
                        playlistId != -1L -> getString(R.string.msg_playlist_empty)
                        artistName != null -> getString(R.string.msg_no_artist_tracks)
                        else -> getString(R.string.msg_no_music)
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }

                view.findViewById<TextView>(R.id.tv_tracks_count)?.text = if (trackList.size == 1) "1 Song" else "${trackList.size} Songs"

                val rvTracks = view.findViewById<RecyclerView>(R.id.rv_tracks) ?: return@withContext
                if (adapter == null) {
                    adapter = TrackAdapter(trackList) { track ->
                        showTrackOptionsDialog(track)
                    }
                    rvTracks.adapter = adapter
                } else {
                    adapter?.updateData(trackList)
                }

                // Update alphabet scrollbar visibility and available letters
                val alphabetScrollbar = view.findViewById<AlphabetIndexScrollbar>(R.id.alphabet_scrollbar)
                if (sortColumn == MediaStore.Audio.Media.TITLE) {
                    alphabetScrollbar?.visibility = View.VISIBLE
                    alphabetScrollbar?.setAvailableLetters(adapter?.getAvailableLetters() ?: emptyList())
                } else {
                    alphabetScrollbar?.visibility = View.GONE
                }

                if (!initialScrollDone && trackList.isNotEmpty()) {
                    attemptScrollToActiveTrack()
                }
            }
        }
    }

    private fun attemptScrollToActiveTrack() {
        if (initialScrollDone) return

        val rvTracks = view?.findViewById<RecyclerView>(R.id.rv_tracks) ?: return
        val adapter = rvTracks.adapter as? TrackAdapter ?: return
        if (adapter.itemCount == 0) return

        var currentTrackId: Long = -1
        if (isBound && musicService != null) {
            currentTrackId = musicService?.getCurrentTrack()?.id ?: -1
        } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
            currentTrackId = MusicService.playlist[MusicService.currentIndex].id
        }

        if (currentTrackId != -1L) {
            val index = adapter.indexOfTrack(currentTrackId)

            if (index != -1) {
                if (rvTracks.width > 0 && rvTracks.height > 0) {
                    (rvTracks.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 300)
                    initialScrollDone = true
                } else {
                    rvTracks.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            rvTracks.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            (rvTracks.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 300)
                            initialScrollDone = true
                        }
                    })
                }
            }
        }
    }

    private fun getTracks(context: Context, selection: String?, selectionArgs: Array<String>?): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
            val minDurationMillis = minDurationSec * 1000

            val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
            val finalSelection = if (selection != null) "($baseSelection) AND ($selection)" else baseSelection

            val order = if (isAscending) "ASC" else "DESC"
            val sortOrder = if (sortColumn == "custom_preference") {
                "${MediaStore.Audio.Media.TITLE} ASC"
            } else {
                "$sortColumn $order"
            }

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                finalSelection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val path = it.getString(dataColumn) ?: continue
                    val album = it.getString(albumColumn)
                    val albumId = it.getLong(albumIdColumn)

                    if (!HiddenTracksManager.isHidden(context, path) &&
                        !path.lowercase().contains("ringtone") &&
                        !path.lowercase().contains("notification")
                    ) {
                        list.add(TrackMetadataManager.applyMetadata(context, Track(id, title, artist, path, album, albumId)))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        if (sortColumn == "custom_preference") {
            val customOrder = CustomSortHelper.getCustomOrder(context)
            var sortedList = CustomSortHelper.sortTracksCustom(list, customOrder)
            if (!isAscending) {
                sortedList = sortedList.reversed()
            }
            return sortedList
        }
        return list
    }

    private fun getPlaylistTracks(context: Context, playlistId: Long): List<Track> {
        val playlist = AppPlaylistManager.getPlaylist(context, playlistId) ?: return emptyList()
        val allTracks = getTracks(context, null, null)
        val trackMap = allTracks.associateBy { it.uri }
        return playlist.trackPaths.mapNotNull { trackMap[it] }
    }

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
        val containerCustom = dialogView.findViewById<View>(R.id.container_custom)

        val rbTitle = dialogView.findViewById<RadioButton>(R.id.rb_title)
        val rbDateAdded = dialogView.findViewById<RadioButton>(R.id.rb_date_added)
        val rbDateModified = dialogView.findViewById<RadioButton>(R.id.rb_date_modified)
        val rbCustom = dialogView.findViewById<RadioButton>(R.id.rb_custom)

        fun updateSelection(selectedRb: RadioButton) {
            rbTitle.isChecked = false
            rbDateAdded.isChecked = false
            rbDateModified.isChecked = false
            rbCustom.isChecked = false
            selectedRb.isChecked = true

            if (selectedRb == rbCustom) {
                switchAsc.isEnabled = false
                switchAsc.alpha = 0.5f
            } else {
                switchAsc.isEnabled = true
                switchAsc.alpha = 1.0f
            }
        }

        switchAsc.isChecked = isAscending
        when (sortColumn) {
            MediaStore.Audio.Media.TITLE -> updateSelection(rbTitle)
            MediaStore.Audio.Media.DATE_ADDED -> updateSelection(rbDateAdded)
            MediaStore.Audio.Media.DATE_MODIFIED -> updateSelection(rbDateModified)
            "custom_preference" -> updateSelection(rbCustom)
        }

        fun saveSortPrefs() {
            val prefs = requireContext().getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            prefs.edit {
                putString("sort_column", sortColumn)
                putBoolean("is_ascending", isAscending)
            }
        }

        switchAsc.setOnCheckedChangeListener { _, isChecked ->
            isAscending = isChecked
            saveSortPrefs()
            loadTracks()
        }

        containerTitle.setOnClickListener {
            updateSelection(rbTitle)
            sortColumn = MediaStore.Audio.Media.TITLE
            saveSortPrefs()
            loadTracks()
            dialog.dismiss()
        }

        containerDateAdded.setOnClickListener {
            updateSelection(rbDateAdded)
            sortColumn = MediaStore.Audio.Media.DATE_ADDED
            saveSortPrefs()
            loadTracks()
            dialog.dismiss()
        }

        containerDateModified.setOnClickListener {
            updateSelection(rbDateModified)
            sortColumn = MediaStore.Audio.Media.DATE_MODIFIED
            saveSortPrefs()
            loadTracks()
            dialog.dismiss()
        }

        containerCustom.setOnClickListener {
            updateSelection(rbCustom)
            sortColumn = "custom_preference"
            saveSortPrefs()
            loadTracks()
            Toast.makeText(requireContext(), "Long press and drag tracks to reorder", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTrackOptionsDialog(track: Track) {
        currentEditingTrackUri = track.uri
        val activity = requireActivity() as? AppCompatActivity ?: return
        TrackMenuManager.showTrackOptionsDialog(activity, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
            override fun onArtworkChanged() {
                loadTracks()
                updateMiniPlayer()
            }
            override fun onTrackUpdated() {
                loadTracks()
            }
            override fun onTrackDeleted() {
                loadTracks()
            }
        })
    }
}
