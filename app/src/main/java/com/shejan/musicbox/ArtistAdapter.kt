package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Artist(val id: Long, val name: String, val trackCount: Int)

class ArtistAdapter(private val artists: List<Artist>, private val onClick: (Artist) -> Unit) :
    RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder>() {

    class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_artist_name)
        val count: TextView = view.findViewById(R.id.tv_track_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]
        holder.name.text = artist.name
        holder.count.text = "${artist.trackCount} TRACKS"
        
        holder.itemView.setOnClickListener { onClick(artist) }
    }

    override fun getItemCount() = artists.size
}
