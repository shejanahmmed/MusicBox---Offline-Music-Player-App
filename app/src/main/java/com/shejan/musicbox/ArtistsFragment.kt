package com.shejan.musicbox

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArtistsFragment : Fragment() {

    private var localContentVersion: Long = 0
    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Mini Player handled by MainActivity
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_artists)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = ArtistAdapter(emptyList()) { artist ->
            val bundle = Bundle()
            bundle.putString("ARTIST_NAME", artist.name)
            findNavController().navigate(R.id.nav_tracks, bundle)
        }

        loadArtists()
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
        try {
            requireContext().unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (localContentVersion != MusicUtils.contentVersion) {
            loadArtists()
        }
    }

    private fun loadArtists() {
        localContentVersion = MusicUtils.contentVersion
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<Artist>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Artists._ID,
                    MediaStore.Audio.Artists.ARTIST,
                    MediaStore.Audio.Artists.NUMBER_OF_TRACKS
                )
                
                val cursor = requireContext().contentResolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    MediaStore.Audio.Artists.ARTIST + " ASC"
                )
    
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
                    val countCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
    
                    while (it.moveToNext()) {
                        val id = it.getLong(idCol)
                        val name = it.getString(nameCol)
                        val count = it.getInt(countCol)
                        list.add(Artist(id, name, count))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                     Toast.makeText(requireContext(), "Error loading artists", Toast.LENGTH_SHORT).show()
                }
            }
            
            withContext(Dispatchers.Main) {
                val view = view ?: return@withContext
                val countView = view.findViewById<android.widget.TextView>(R.id.tv_artists_count)
                val countText = if (list.size == 1) "1 Artist" else "${list.size} Artists"
                countView.text = countText
        
                val rv = view.findViewById<RecyclerView>(R.id.rv_artists)
                rv.layoutManager = LinearLayoutManager(requireContext())
                rv.adapter = ArtistAdapter(list) { artist ->
                     val bundle = Bundle()
                     bundle.putString("ARTIST_NAME", artist.name)
                     findNavController().navigate(R.id.nav_tracks, bundle)
                }
            }
        }
    }
}
