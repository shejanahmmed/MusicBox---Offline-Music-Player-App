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
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QueueAdapter(
    private val tracks: List<Track>,
    private val currentTrackId: Long,
    private val onTrackClick: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_track_title)
        val artist: TextView = view.findViewById(R.id.tv_track_artist)
        val albumArt: ImageView = view.findViewById(R.id.iv_album_art)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val track = tracks[position]
        holder.title.text = track.title
        holder.artist.text = track.artist
        
        MusicUtils.loadTrackArt(holder.itemView.context, track.id, track.albumId, track.uri, holder.albumArt)
        
        if (track.id == currentTrackId) {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.primary_red))
            holder.itemView.setBackgroundResource(R.drawable.bg_track_card_active)
        } else {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.white))
            holder.itemView.setBackgroundResource(R.drawable.bg_track_card)
        }
        
        holder.itemView.setOnClickListener {
            onTrackClick(position)
        }
    }

    override fun getItemCount() = tracks.size
}

