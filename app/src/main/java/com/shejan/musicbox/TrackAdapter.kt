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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import android.annotation.SuppressLint

data class Track(val id: Long, val title: String, val artist: String, val uri: String, val album: String? = null, val albumId: Long = -1L, val isActive: Boolean = false)

class TrackAdapter(private var tracks: List<Track>, private val onMoreClicked: (Track) -> Unit) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    // Store active ID internally to avoid inefficient list copying
    private var currentActiveTrackId: Long = -1L

    class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_track_title)
        val artist: TextView = view.findViewById(R.id.tv_track_artist)
        val art: ImageView? = view.findViewById(R.id.iv_album_art)
        val options: ImageButton = view.findViewById(R.id.btn_options)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.title.text = track.title
        holder.artist.text = track.artist

        if (holder.art != null) {
            ImageLoader.load(holder.root.context, track.id, track.albumId, track.uri, holder.art)
        }

        holder.root.setOnClickListener {
            // FIXED: Use centralized playlist update to handle shuffle logic
            MusicService.updatePlaylist(tracks, position)
            
            val intent = Intent(holder.root.context, MusicService::class.java).apply {
                putExtra("TITLE", track.title)
                putExtra("ARTIST", track.artist)
                putExtra("URI", track.uri)
            }
            androidx.core.content.ContextCompat.startForegroundService(holder.root.context, intent)
            
            NowPlayingActivity.start(holder.root.context, track.title, track.artist)
        }
        
        holder.options.setOnClickListener {
            onMoreClicked(track)
        }

        // Optimized Highlight Logic
        if (track.id == currentActiveTrackId) {
            holder.title.setTextColor(holder.root.context.getColor(R.color.primary_red))
            holder.root.setBackgroundResource(R.drawable.bg_track_card_active)
        } else {
            holder.title.setTextColor(holder.root.context.getColor(R.color.white))
            holder.root.setBackgroundResource(R.drawable.bg_track_card)
        }
    }

    override fun getItemCount() = tracks.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newTracks: List<Track>) {
        this.tracks = newTracks
        notifyDataSetChanged()
    }

    fun updateActiveTrack(activeId: Long) {
        if (currentActiveTrackId == activeId) return // No change

        val oldActiveId = currentActiveTrackId
        currentActiveTrackId = activeId

        // Find positions to update
        val oldPos = tracks.indexOfFirst { it.id == oldActiveId }
        val newPos = tracks.indexOfFirst { it.id == activeId }

        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }
}
