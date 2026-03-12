package com.shejan.musicbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {

    private lateinit var adapter: TrackAdapter
    private var currentSearchQuery: String = ""
    private lateinit var tvSearchCount: TextView

    // Result Launcher for Artwork
    private var currentEditingTrackUri: String? = null
    private val pickArtworkLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && currentEditingTrackUri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { e.printStackTrace() }
             
            TrackArtworkManager.saveArtwork(requireContext(), currentEditingTrackUri!!, uri.toString())
            performSearch(currentSearchQuery)
        }
    }

    private val allTracks = mutableListOf<Track>()
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSearchCount = view.findViewById(R.id.tv_search_count)

        val rvResults = view.findViewById<RecyclerView>(R.id.rv_search_results)
        rvResults.layoutManager = LinearLayoutManager(requireContext())
        adapter = TrackAdapter(emptyList()) { track ->
            currentEditingTrackUri = track.uri
            TrackMenuManager.showTrackOptionsDialog(requireActivity() as AppCompatActivity, track, pickArtworkLauncher, object : TrackMenuManager.Callback {
                override fun onArtworkChanged() {
                    loadAllTracks()
                }
                override fun onTrackUpdated() {
                    loadAllTracks()
                }
                override fun onTrackDeleted() {
                    loadAllTracks()
                }
            })
        }
        rvResults.adapter = adapter

        val etSearch = view.findViewById<EditText>(R.id.et_search)
        etSearch.requestFocus()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s.toString().trim()
                performSearch(currentSearchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        loadAllTracks()
    }

    private fun loadAllTracks() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val tempList = mutableListOf<Track>()
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
                val prefs = requireContext().getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
                val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
                val minDurationMillis = minDurationSec * 1000
             
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
             
                val cursor = requireContext().contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
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
                     
                        if (!HiddenTracksManager.isHidden(requireContext(), path) && 
                            !path.lowercase().contains("ringtone") && 
                            !path.lowercase().contains("notification")) {
                            tempList.add(TrackMetadataManager.applyMetadata(requireContext(), Track(id, title, artist, path, album, albumId)))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    allTracks.clear()
                    allTracks.addAll(tempList)
                    if (currentSearchQuery.isNotEmpty()) {
                        performSearch(currentSearchQuery)
                    } else {
                        val currentCount = allTracks.size
                        tvSearchCount.text = if (currentCount == 1) "Search from 1 track" else "Search from $currentCount tracks"
                    }
                }
            }
        }
    }
    
    private fun performSearch(query: String) {
        searchJob?.cancel()
        
        if (query.isEmpty()) {
            adapter.updateData(emptyList())
            val totalCount = allTracks.size
            if (::tvSearchCount.isInitialized) {
                tvSearchCount.text = if (totalCount == 1) "Search from 1 track" else "Search from $totalCount tracks"
            }
            return
        }

        searchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
            val filteredList = allTracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true)
            }
            
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    adapter.updateData(filteredList)
                    val foundCount = filteredList.size
                    tvSearchCount.text = if (foundCount == 1) "1 track found" else "$foundCount tracks found"
                }
            }
        }
    }
}
