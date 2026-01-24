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

data class PlaylistItem(
    val id: Long,
    val name: String,
    val count: Int,
    val isFavorite: Boolean = false,
    val isAuto: Boolean = false
)

class PlaylistAdapter(
    private val playlists: List<PlaylistItem>, 
    private val onClick: (PlaylistItem) -> Unit,
    private val onLongClick: ((PlaylistItem) -> Unit)? = null
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_playlist_name)
        val count: TextView = view.findViewById(R.id.tv_track_count)
        val icon: ImageView = view.findViewById(R.id.iv_special_icon)
        val thumb: ImageView = view.findViewById(R.id.iv_playlist_thumb)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = playlists[position]
        holder.name.text = item.name
        holder.count.text = holder.root.context.getString(R.string.playlist_track_count, item.count)

        if (item.isFavorite) {
            holder.icon.visibility = View.VISIBLE
            // Set gradient thumbnail
            holder.thumb.setBackgroundResource(R.drawable.gradient_thumb_red) // Need to create this or use color
        } else {
            holder.icon.visibility = View.GONE
            // Random or specific thumb
             holder.thumb.setBackgroundColor(holder.root.context.getColor(R.color.background_dark)) // Placeholder
        }

        holder.root.setOnClickListener { onClick(item) }
        holder.root.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount() = playlists.size
}

