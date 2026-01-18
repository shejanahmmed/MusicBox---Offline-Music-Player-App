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

class PlaylistAdapter(private val playlists: List<PlaylistItem>, private val onClick: (PlaylistItem) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

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
        holder.count.text = "${item.count} tracks"

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
    }

    override fun getItemCount() = playlists.size
}
