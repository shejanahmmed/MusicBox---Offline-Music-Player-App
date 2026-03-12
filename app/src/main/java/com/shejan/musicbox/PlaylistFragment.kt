package com.shejan.musicbox

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
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

class PlaylistFragment : Fragment() {

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
            // Note: Mini Player is handled by MainActivity
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_add_playlist).setOnClickListener {
            startActivity(Intent(requireContext(), CreatePlaylistActivity::class.java))
        }

        val rv = view.findViewById<RecyclerView>(R.id.rv_playlists)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = PlaylistAdapter(emptyList(), onClick = { item ->
            val bundle = Bundle()
            bundle.putLong("PLAYLIST_ID", item.id)
            bundle.putString("PLAYLIST_NAME", item.name)
            findNavController().navigate(R.id.nav_tracks, bundle)
        }, onLongClick = { item ->
            showDeleteDialog(item)
        })

        loadPlaylists()
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
            loadPlaylists()
        }
    }

    private fun updateTopCards(count: Int = -1) {
        val view = view ?: return
        if (count != -1) {
             view.findViewById<TextView>(R.id.tv_playlists_count).text = getString(R.string.lists_count, count)
        } else {
             view.findViewById<TextView>(R.id.tv_playlists_count).text = getString(R.string.lists_count, 0)
        }
    }

    private fun loadPlaylists() {
        localContentVersion = MusicUtils.contentVersion
        
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<PlaylistItem>()

            val appPlaylists = AppPlaylistManager.getAllPlaylists(requireContext())
            list.addAll(appPlaylists.map { PlaylistItem(it.id, it.name, it.trackPaths.size) })

            withContext(Dispatchers.Main) {
                val view = view ?: return@withContext

                val rv = view.findViewById<RecyclerView>(R.id.rv_playlists)
                val emptyView = view.findViewById<TextView>(R.id.tv_empty_state)
                
                if (list.isEmpty()) {
                    rv.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    rv.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                }

                rv.layoutManager = LinearLayoutManager(requireContext())
                rv.adapter = PlaylistAdapter(list, onClick = { item ->
                     val bundle = Bundle()
                     bundle.putLong("PLAYLIST_ID", item.id)
                     bundle.putString("PLAYLIST_NAME", item.name)
                     findNavController().navigate(R.id.nav_tracks, bundle)
                }, onLongClick = { item ->
                     showDeleteDialog(item)
                })
                
                updateTopCards(list.size)
            }
        }
    }

    @SuppressLint("InflateParams", "SetTextI18n")
    private fun showDeleteDialog(playlist: PlaylistItem) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_playlist, null)
        dialog.setContentView(dialogView)
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        
        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text = 
            "Are you sure you want to delete \"${playlist.name}\"?"
            
        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btn_delete).setOnClickListener {
            dialog.dismiss()
            deletePlaylist(playlist)
        }
        
        dialog.show()
    }
    
    private fun deletePlaylist(playlist: PlaylistItem) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AppPlaylistManager.deletePlaylist(requireContext(), playlist.id)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
                    loadPlaylists()
                }
            }
        }
    }
}
