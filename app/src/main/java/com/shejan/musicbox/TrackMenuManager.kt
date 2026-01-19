package com.shejan.musicbox

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

object TrackMenuManager {

    interface Callback {
        fun onArtworkChanged()
        fun onTrackDeleted()
        fun onTrackUpdated()
    }

    fun showTrackOptionsDialog(
        activity: AppCompatActivity,
        track: Track,
        pickArtworkLauncher: androidx.activity.result.ActivityResultLauncher<String>?,
        callback: Callback?
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_track_options, null)
        dialog.setContentView(view)
        
        view.post {
            (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Track Info
        val customMetadata = TrackMetadataManager.getMetadata(activity, track.id)
        val displayTitle = customMetadata?.title ?: track.title
        val displayArtist = customMetadata?.artist ?: track.artist
        val displayAlbum = customMetadata?.album ?: track.album ?: "Unknown Album"
        
        view.findViewById<TextView>(R.id.tv_track_title).text = displayTitle
        view.findViewById<TextView>(R.id.tv_track_artist).text = "$displayArtist • $displayAlbum"
        
        val ivArt = view.findViewById<ImageView>(R.id.iv_track_art)
        MusicUtils.loadTrackArt(activity, track.id, track.albumId, ivArt)
        
        // File Info
        val file = java.io.File(track.uri)
        val fileSize = if (file.exists()) String.format("%.2f MB", file.length() / (1024.0 * 1024.0)) else "N/A"
        
        var durationStr = "--:--"
        var bitrate = "N/A"
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(track.uri)
            val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durMs)
            val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durMs) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
            durationStr = String.format("%02d:%02d", minutes, seconds)
            
            // Also get bitrate here to avoid double retrieval if possible, but keep existing logic for safety
            // actually reusing retriever is better
            bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                "${it.toLong() / 1000} kbit/s"
            } ?: "N/A"
            
            retriever.release()
        } catch (e: Exception) { }

        view.findViewById<TextView>(R.id.tv_track_info).text = "$bitrate    48000 Hz    $fileSize"
        view.findViewById<TextView>(R.id.tv_track_path).text = track.uri
        view.findViewById<TextView>(R.id.tv_format_badge).text = "AUDIO/MPEG"
        view.findViewById<TextView>(R.id.tv_duration_badge).text = "⏱ $durationStr"
        
        // Favorite
        val btnFavorite = view.findViewById<ImageButton>(R.id.btn_favorite)
        if (FavoritesManager.isFavorite(activity, track.uri)) {
            btnFavorite.setImageResource(R.drawable.ic_favorite)
            btnFavorite.setColorFilter(activity.getColor(R.color.primary_red))
        }
        btnFavorite.setOnClickListener {
            if (FavoritesManager.isFavorite(activity, track.uri)) {
                FavoritesManager.removeFavorite(activity, track.uri)
                Toast.makeText(activity, "Removed from Favorites", Toast.LENGTH_SHORT).show()
            } else {
                FavoritesManager.addFavorite(activity, track.uri)
                Toast.makeText(activity, "Added to Favorites", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
            callback?.onTrackUpdated()
        }
        
        // Add to Playlist
        view.findViewById<ImageButton>(R.id.btn_add_to_playlist).setOnClickListener {
            showAddToPlaylistDialog(activity, track)
            dialog.dismiss()
        }
        
        // Edit
        view.findViewById<ImageButton>(R.id.btn_edit).setOnClickListener {
            val intent = Intent(activity, EditTrackActivity::class.java)
            intent.putExtra(EditTrackActivity.EXTRA_TRACK_ID, track.id)
            intent.putExtra(EditTrackActivity.EXTRA_TRACK_TITLE, displayTitle)
            intent.putExtra(EditTrackActivity.EXTRA_TRACK_ARTIST, displayArtist)
            intent.putExtra(EditTrackActivity.EXTRA_TRACK_ALBUM, displayAlbum)
            intent.putExtra(EditTrackActivity.EXTRA_ALBUM_ID, track.albumId)
            intent.putExtra(EditTrackActivity.EXTRA_FILE_TITLE, track.title)
            intent.putExtra(EditTrackActivity.EXTRA_FILE_ARTIST, track.artist)
            intent.putExtra(EditTrackActivity.EXTRA_FILE_ALBUM, track.album)
            activity.startActivity(intent)
            dialog.dismiss()
        }
        
        // Set As (Artwork)
        view.findViewById<ImageButton>(R.id.btn_set_as).setOnClickListener {
            showArtworkEditorDialog(activity, track, pickArtworkLauncher, callback)
            dialog.dismiss()
        }
        
        // Delete (Hide from App)
        view.findViewById<ImageButton>(R.id.btn_delete).setOnClickListener {
            if (FavoritesManager.isFavorite(activity, track.uri)) {
                FavoritesManager.removeFavorite(activity, track.uri)
            }
            
            // Hide Track (Remove from app only)
            HiddenTracksManager.hideTrack(activity, track.uri)
            Toast.makeText(activity, "Removed from library", Toast.LENGTH_SHORT).show()
            
            callback?.onTrackDeleted()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showArtworkEditorDialog(
        activity: AppCompatActivity,
        track: Track,
        pickArtworkLauncher: androidx.activity.result.ActivityResultLauncher<String>?,
        callback: Callback?
    ) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.dialog_artwork_editor, null)
        dialog.setContentView(view)
        
        view.post { (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        
        val ivPreview = view.findViewById<ImageView>(R.id.iv_artwork_preview)
        MusicUtils.loadTrackArt(activity, track.id, track.albumId, ivPreview)
        
        view.findViewById<View>(R.id.btn_default_artwork).setOnClickListener {
            TrackArtworkManager.resetArtwork(activity, track.id)
            callback?.onArtworkChanged()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_remove_artwork).setOnClickListener {
            TrackArtworkManager.removeArtwork(activity, track.id)
            callback?.onArtworkChanged()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_change_artwork).setOnClickListener {
            if (pickArtworkLauncher != null) {
                pickArtworkLauncher.launch("image/*")
                dialog.dismiss()
            } else {
                 Toast.makeText(activity, "Not available here", Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }

    private fun showAddToPlaylistDialog(context: Context, track: Track) {
        val playlists = mutableListOf<Pair<Long, String>>()
        try {
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Audio.Playlists._ID, android.provider.MediaStore.Audio.Playlists.NAME),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists._ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Playlists.NAME)
                while (cursor.moveToNext()) {
                    playlists.add(Pair(cursor.getLong(idCol), cursor.getString(nameCol)))
                }
            }
        } catch (e: Exception) { }
        
        val options = mutableListOf<String>()
        options.add("➕ Create New Playlist")
        options.addAll(playlists.map { it.second })
        
        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle("Add to Playlist")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) showCreatePlaylistDialog(context, track)
                else addTrackToPlaylist(context, track, playlists[which - 1].first, playlists[which - 1].second)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showCreatePlaylistDialog(context: Context, track: Track) {
        val input = EditText(context)
        input.hint = "Playlist name"
        input.setTextColor(context.getColor(R.color.white))
        input.setHintTextColor(context.getColor(R.color.text_white_opacity_40))
        
        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle("Create Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createPlaylistAndAddTrack(context, name, track)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun createPlaylistAndAddTrack(context: Context, name: String, track: Track) {
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Playlists.NAME, name)
                put(android.provider.MediaStore.Audio.Playlists.DATE_ADDED, System.currentTimeMillis())
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()?.let { addTrackToPlaylist(context, track, it, name) }
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun addTrackToPlaylist(context: Context, track: Track, playlistId: Long, playlistName: String) {
        try {
            val uri = android.provider.MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId)
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Playlists.Members.AUDIO_ID, track.id)
                put(android.provider.MediaStore.Audio.Playlists.Members.PLAY_ORDER, System.currentTimeMillis())
            }
            context.contentResolver.insert(uri, values)
            Toast.makeText(context, "Added to \"$playlistName\"", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to add to playlist", Toast.LENGTH_SHORT).show() 
        }
    }
}
