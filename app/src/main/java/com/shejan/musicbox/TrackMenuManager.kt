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
import android.annotation.SuppressLint
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import android.provider.MediaStore

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
        @SuppressLint("InflateParams")
        val view = activity.layoutInflater.inflate(R.layout.dialog_track_options, null)
        dialog.setContentView(view)
        
        view.post {
            (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        
        // Track Info
        val customMetadata = TrackMetadataManager.getMetadata(activity, track.uri)
        val displayTitle = customMetadata?.title ?: track.title
        val displayArtist = customMetadata?.artist ?: track.artist
        val unknownAlbum = activity.getString(R.string.unknown_album)
        val displayAlbum = customMetadata?.album ?: track.album ?: unknownAlbum
        
        val tvTitle = view.findViewById<TextView>(R.id.tv_track_title)
        val tvArtist = view.findViewById<TextView>(R.id.tv_track_artist)
        
        tvTitle.text = displayTitle
        tvArtist.text = activity.getString(R.string.track_artist_album_format, displayArtist, displayAlbum)
        
        // Enable Marquee
        tvTitle.isSelected = true
        tvArtist.isSelected = true
        
        val ivArt = view.findViewById<ImageView>(R.id.iv_track_art)
        MusicUtils.loadTrackArt(activity, track.id, track.albumId, track.uri, ivArt)
        
        // File Info
        val file = java.io.File(track.uri)
        val fileSize = if (file.exists()) String.format(Locale.getDefault(), activity.getString(R.string.file_size_mb), file.length() / (1024.0 * 1024.0)) else activity.getString(R.string.na_placeholder)
        
        var durationStr = "--:--"
        var bitrate = activity.getString(R.string.na_placeholder)
        var format = "AUDIO" // Default
        
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(track.uri)
            val durMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durMs)
            val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durMs) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
            durationStr = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            
            bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                 activity.getString(R.string.bitrate_kbits, (it.toLong() / 1000).toString())
            } ?: activity.getString(R.string.na_placeholder)
            
            val mimetype = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (mimetype != null) {
                // e.g. audio/mpeg -> MPEG or MP3
                val subtype = mimetype.substringAfter("/")
                format = when(subtype.lowercase()) {
                    "mpeg" -> "MP3"
                    "flac" -> "FLAC"
                    "mp4" -> "M4A"
                    "wav" -> "WAV"
                    "ogg" -> "OGG"
                    "x-matroska" -> "MKA"
                    else -> subtype.uppercase()
                }
            }
            
            retriever.release()
        } catch (_: Exception) { }

        view.findViewById<TextView>(R.id.tv_file_size_badge).text = fileSize
        view.findViewById<TextView>(R.id.tv_stat_format).text = format
        view.findViewById<TextView>(R.id.tv_stat_bitrate).text = bitrate
        view.findViewById<TextView>(R.id.tv_stat_duration).text = durationStr
        view.findViewById<TextView>(R.id.tv_track_path).text = track.uri
        
        // Favorite
        val btnFavorite = view.findViewById<ImageButton>(R.id.btn_favorite)
        if (FavoritesManager.isFavorite(activity, track.uri)) {
            btnFavorite.setImageResource(R.drawable.ic_favorite)
            btnFavorite.setColorFilter(activity.getColor(R.color.primary_red))
        }
        btnFavorite.setOnClickListener {
            if (FavoritesManager.isFavorite(activity, track.uri)) {
                FavoritesManager.removeFavorite(activity, track.uri)
                Toast.makeText(activity, activity.getString(R.string.removed_from_favorites), Toast.LENGTH_SHORT).show()
            } else {
                FavoritesManager.addFavorite(activity, track.uri)
                Toast.makeText(activity, activity.getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show()
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
            showEditTrackDialog(activity, track, callback)
            dialog.dismiss()
        }
        
        // Share
        view.findViewById<ImageButton>(R.id.btn_share).setOnClickListener {
            shareTrack(activity, track)
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
            // Hide Track (Remove from app only)
            HiddenTracksManager.hideTrack(activity, track.uri)
            
            // Sync: Remove from all playlists
            AppPlaylistManager.removeTrackFromAllPlaylists(activity, track.uri)
            
            MusicUtils.contentVersion++
            Toast.makeText(activity, activity.getString(R.string.removed_from_library), Toast.LENGTH_SHORT).show()
            
            // Broadcast deletion to update other lists
            val intent = Intent("com.shejan.musicbox.TRACK_DELETED")
            intent.putExtra("DELETED_TRACK_URI", track.uri)
            activity.sendBroadcast(intent)
            
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
        @SuppressLint("InflateParams")
        val view = activity.layoutInflater.inflate(R.layout.dialog_artwork_editor, null)
        dialog.setContentView(view)
        
        view.post { (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
        
        // Force 85% Height
        dialog.setOnShowListener {
            @SuppressLint("DiscouragedApi")
            val bottomSheetId = activity.resources.getIdentifier("design_bottom_sheet", "id", "com.google.android.material")
            val bottomSheet = dialog.findViewById<View>(bottomSheetId)
            if (bottomSheet != null) {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet)
                val displayMetrics = activity.resources.displayMetrics
                val height = displayMetrics.heightPixels
                val maxHeight = (height * 0.90).toInt()
                
                bottomSheet.layoutParams.height = maxHeight
                bottomSheet.requestLayout()
                behavior.peekHeight = maxHeight
                behavior.skipCollapsed = true
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }
        
        val ivPreview = view.findViewById<ImageView>(R.id.iv_artwork_preview)
        MusicUtils.loadTrackArt(activity, track.id, track.albumId, track.uri, ivPreview)
        
        view.findViewById<View>(R.id.btn_default_artwork).setOnClickListener {
            TrackArtworkManager.resetArtwork(activity, track.uri)
            callback?.onArtworkChanged()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_remove_artwork).setOnClickListener {
            TrackArtworkManager.removeArtwork(activity, track.uri)
            callback?.onArtworkChanged()
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_change_artwork).setOnClickListener {
            if (pickArtworkLauncher != null) {
                pickArtworkLauncher.launch("image/*")
                dialog.dismiss()
            } else {
                 Toast.makeText(activity, activity.getString(R.string.not_available_here), Toast.LENGTH_SHORT).show()
            }
        }
        
        dialog.show()
    }

    private fun showAddToPlaylistDialog(context: Context, track: Track) {

        val playlists = AppPlaylistManager.getAllPlaylists(context).map { Pair(it.id, it.name) }.toMutableList()
        
        val options = mutableListOf<String>()
        options.add(context.getString(R.string.create_new_playlist_option))
        options.addAll(playlists.map { it.second })
        
        AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(context.getString(R.string.add_to_playlist_title))
            .setAdapter(android.widget.ArrayAdapter(context, R.layout.item_playlist_dialog_option, android.R.id.text1, options)) { _, which ->
                if (which == 0) showCreatePlaylistDialog(context, track)
                else addTrackToPlaylist(context, track, playlists[which - 1].first, playlists[which - 1].second)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }
    
    private fun showCreatePlaylistDialog(context: Context, track: Track) {
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_create_playlist, null)
        val input = view.findViewById<EditText>(R.id.et_playlist_name)
        
        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialog)
            .setTitle(context.getString(R.string.create_playlist_title))
            .setView(view)
            .setPositiveButton(context.getString(R.string.create), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()

        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                createPlaylistAndAddTrack(context, name, track)
                dialog.dismiss()
            } else {
                Toast.makeText(context, context.getString(R.string.please_enter_name), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createPlaylistAndAddTrack(context: Context, name: String, track: Track) {
        try {
            AppPlaylistManager.createPlaylist(context, name, listOf(track.uri))
            Toast.makeText(context, context.getString(R.string.playlist_created), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun addTrackToPlaylist(context: Context, track: Track, playlistId: Long, playlistName: String) {
        try {
            AppPlaylistManager.addTrackToPlaylist(context, playlistId, track.uri)
            Toast.makeText(context, context.getString(R.string.added_to_playlist, playlistName), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.failed_add_playlist), Toast.LENGTH_SHORT).show() 
        }
    }

    private fun showEditTrackDialog(activity: AppCompatActivity, track: Track, callback: Callback?) {
        val dialog = BottomSheetDialog(activity)
        @SuppressLint("InflateParams")
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_track, null)
        dialog.setContentView(view)

        view.post {
            (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Get current metadata
        val customMetadata = TrackMetadataManager.getMetadata(activity, track.uri)
        val currentTitle = customMetadata?.title ?: track.title
        val currentArtist = customMetadata?.artist ?: track.artist
        val currentAlbum = customMetadata?.album ?: track.album ?: ""
        val currentYear = customMetadata?.year ?: ""

        val etTrackName = view.findViewById<EditText>(R.id.et_track_name)
        val etArtistName = view.findViewById<EditText>(R.id.et_artist_name)
        val etAlbumName = view.findViewById<EditText>(R.id.et_album_name)
        val etYear = view.findViewById<EditText>(R.id.et_year)

        etTrackName.setText(currentTitle)
        etArtistName.setText(currentArtist)
        etAlbumName.setText(currentAlbum)
        etYear.setText(currentYear)

        // Reset
        view.findViewById<ImageButton>(R.id.btn_reset).setOnClickListener {
             TrackMetadataManager.removeMetadata(activity, track.uri)
             Toast.makeText(activity, activity.getString(R.string.reset_original_values), Toast.LENGTH_SHORT).show()
             callback?.onTrackUpdated()
             dialog.dismiss()
        }

        // Cancel
        view.findViewById<ImageButton>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        // Save
        view.findViewById<ImageButton>(R.id.btn_save).setOnClickListener {
            val newTitle = etTrackName.text.toString().trim()
            val newArtist = etArtistName.text.toString().trim()
            val newAlbum = etAlbumName.text.toString().trim()
            val newYear = etYear.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(activity, activity.getString(R.string.track_name_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            TrackMetadataManager.saveMetadata(
                activity,
                track.uri,
                newTitle,
                newArtist.takeIf { it.isNotEmpty() },
                newAlbum.takeIf { it.isNotEmpty() },
                newYear.takeIf { it.isNotEmpty() }
            )

            Toast.makeText(activity, activity.getString(R.string.metadata_saved), Toast.LENGTH_SHORT).show()
            callback?.onTrackUpdated()
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun shareTrack(context: Context, track: Track) {
        try {
            val file = java.io.File(track.uri)
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "audio/*"
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_track_title)))
            } else {
                 Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
             Toast.makeText(context, context.getString(R.string.could_not_share_file, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
