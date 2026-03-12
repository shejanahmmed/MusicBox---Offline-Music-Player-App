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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumsFragment : Fragment() {

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
            // Note: Mini Player is handled by MainActivity now, but we keep receiver if Albums needs specific updates
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_albums)
        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        rv.adapter = AlbumAdapter(emptyList()) { album ->
            val bundle = Bundle()
            bundle.putString("ALBUM_NAME", album.title)
            findNavController().navigate(R.id.nav_tracks, bundle)
        }

        loadAlbums()
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
        } catch (_: IllegalArgumentException) {}
    }

    override fun onResume() {
        super.onResume()
        if (localContentVersion != MusicUtils.contentVersion) {
            loadAlbums()
        }
    }

    private fun loadAlbums() {
        localContentVersion = MusicUtils.contentVersion
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
            val albumMap = mutableMapOf<Long, Album>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DATA
                )
                
                val cursor = appContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.ALBUM} ASC"
                )
    
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    
                    while (it.moveToNext()) {
                        val path = it.getString(pathCol)
                        if (HiddenTracksManager.isHidden(appContext, path)) continue
                        
                        val albumId = it.getLong(idCol)
                        if (!albumMap.containsKey(albumId)) {
                            val title = it.getString(albumCol)
                            val artist = it.getString(artistCol)
                            albumMap[albumId] = Album(albumId, title, artist, path)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error loading albums", Toast.LENGTH_SHORT).show()
                }
            }
            
            val list = albumMap.values.toList().sortedBy { it.title }
            
            withContext(Dispatchers.Main) {
                val view = view ?: return@withContext
                val countView = view.findViewById<android.widget.TextView>(R.id.tv_albums_count)
                val countText = if (list.size == 1) "1 Album" else "${list.size} Albums"
                countView.text = countText
        
                val rv = view.findViewById<RecyclerView>(R.id.rv_albums)
                rv.layoutManager = GridLayoutManager(requireContext(), 2)
                rv.adapter = AlbumAdapter(list) { album ->
                     // Handle manual navigation via Action ID. Assuming action in nav graph: action_albums_to_tracks
                     // Since we didn't use SafeArgs plugin yet in gradle, we'll navigate using bundle
                     val bundle = Bundle()
                     bundle.putString("ALBUM_NAME", album.title)
                     findNavController().navigate(R.id.nav_tracks, bundle)
                }
            }
        }
    }
}
