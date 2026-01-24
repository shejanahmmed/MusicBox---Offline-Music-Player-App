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

data class Album(val id: Long, val title: String, val artist: String, val artUri: String?)

class AlbumAdapter(private val albums: List<Album>, private val onClick: (Album) -> Unit) :
    RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val art: ImageView = view.findViewById(R.id.iv_album_art)
        val title: TextView = view.findViewById(R.id.tv_album_title)
        val artist: TextView = view.findViewById(R.id.tv_album_artist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.title.text = album.title
        holder.artist.text = album.artist
        
        // Load Art
        MusicUtils.loadAlbumArt(holder.itemView.context, album.id, holder.art)
        
        // Use a simple art loader if possible later, for now static + filter
        // If we had a load function in Context extension:
        // context.loadAlbumArt(album.id, holder.art)
        
        holder.itemView.setOnClickListener { onClick(album) }
    }

    override fun getItemCount() = albums.size
}

