package com.shejan.musicbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private var isReceiverRegistered = false

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.shejan.musicbox.REFRESH_DATA") {
                setupHomeBoxes()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // We need a layout for the home fragment that just contains the RecyclerView
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.rv_home_boxes)
        setupHomeBoxes()
    }

    override fun onStart() {
        super.onStart()
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter("com.shejan.musicbox.REFRESH_DATA")
                ContextCompat.registerReceiver(requireContext(), updateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                isReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        setupHomeBoxes()
    }

    override fun onStop() {
        super.onStop()
        if (isReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(updateReceiver)
                isReceiverRegistered = false
            } catch (_: IllegalArgumentException) {}
        }
    }

    private fun setupHomeBoxes() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val context = context ?: return@launch
            
            val savedOrder = HomeBoxPreferences.getBoxOrder(context)
            val allBoxes = HomeBoxPreferences.getAllBoxes()
            
            val visibleBoxes = savedOrder.mapNotNull { boxId ->
                if (HomeBoxPreferences.isBoxVisible(context, boxId)) {
                    allBoxes.find { it.id == boxId }
                } else {
                    null
                }
            }
            
            val homeBoxes = visibleBoxes.map { box ->
                val (count, label, onClick) = when (box.id) {
                    HomeBoxPreferences.BOX_FAVORITES -> {
                        Triple(getFavoriteCount(context), "Favorites") {
                            MusicUtils.performHapticFeedback(context)
                            val bundle = Bundle()
                            bundle.putBoolean("SHOW_FAVORITES", true)
                            findNavController().navigate(R.id.nav_tracks, bundle)
                        }
                    }
                    HomeBoxPreferences.BOX_PLAYLISTS -> {
                        Triple(getPlaylistCount(context), "Playlists") {
                             MusicUtils.performHapticFeedback(context)
                             findNavController().navigate(R.id.nav_playlists)
                        }
                    }
                    HomeBoxPreferences.BOX_ALBUMS -> {
                        Triple(getAlbumCount(context), "Albums") {
                             MusicUtils.performHapticFeedback(context)
                             findNavController().navigate(R.id.nav_albums)
                        }
                    }
                    HomeBoxPreferences.BOX_ARTISTS -> {
                        Triple(getArtistCount(context), "Artists") {
                             MusicUtils.performHapticFeedback(context)
                             findNavController().navigate(R.id.nav_artists)
                        }
                    }
                    HomeBoxPreferences.BOX_TRACKS -> {
                        Triple(getTrackCount(context), "Tracks") {
                             MusicUtils.performHapticFeedback(context)
                             findNavController().navigate(R.id.nav_tracks)
                        }
                    }
                    HomeBoxPreferences.BOX_EQUALIZER -> {
                        Triple(-1, "Tune Sound") {
                             MusicUtils.performHapticFeedback(context)
                            openEqualizer(context)
                        }
                    }
                    else -> Triple(0, "") {}
                }
                
                MainHomeBox(
                    id = box.id,
                    name = box.name.uppercase(),
                    iconRes = box.iconRes,
                    iconTint = getBoxIconTint(context, box.id),
                    count = count,
                    countLabel = label,
                    onClick = onClick
                )
            }
            
            withContext(Dispatchers.Main) {
                if (recyclerView.layoutManager == null) {
                    val layoutManager = GridLayoutManager(context, 2)
                    recyclerView.layoutManager = layoutManager
                    
                    val spacing = (8 * resources.displayMetrics.density).toInt()
                    recyclerView.addItemDecoration(GridSpacingItemDecoration(2, spacing, spacing, false))
                }
                
                recyclerView.adapter = MainHomeBoxAdapter(homeBoxes)
            }
        }
    }
    
    private fun getBoxIconTint(context: Context, boxId: String): Int {
        return when (boxId) {
            HomeBoxPreferences.BOX_FAVORITES -> ContextCompat.getColor(context, R.color.primary_red)
            else -> ContextCompat.getColor(context, R.color.colorIcon)
        }
    }

    private fun getFavoriteCount(context: Context): Int {
        val favorites = FavoritesManager.getFavorites(context)
        if (favorites.isEmpty()) return 0

        var count = 0
        try {
            val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
            val minDurationMillis = minDurationSec * 1000
            
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
            
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA), // valid column
                selection,
                null, 
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (favorites.contains(path) && 
                        !HiddenTracksManager.isHidden(context, path) && 
                        !path.lowercase().contains("ringtone") && 
                        !path.lowercase().contains("notification")) {
                        count++
                    }
                }
            }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getPlaylistCount(context: Context): Int {
        return AppPlaylistManager.getAllPlaylists(context).size
    }
    
    private fun getAlbumCount(context: Context): Int {
        var count = 0
        try {
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getArtistCount(context: Context): Int {
        var count = 0
        try {
            context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Artists._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) { }
        return count
    }
    
    private fun getTrackCount(context: Context): Int {
        var count = 0
        val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
        val minDurationMs = minDurationSec * 1000
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getInt(durationColumn)
                    
                    if (!HiddenTracksManager.isHidden(context, path) && duration >= minDurationMs) {
                        count++
                    }
                }
            }
        } catch (_: Exception) { }
        return count
    }

    private fun openEqualizer(context: Context) {
        try {
            val intent = Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Equalizer not available", Toast.LENGTH_SHORT).show()
        }
    }
}
