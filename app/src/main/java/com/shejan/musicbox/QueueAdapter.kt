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
        val activeIndicator: View = view.findViewById(R.id.fl_active_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val track = tracks[position]
        holder.title.text = track.title
        holder.artist.text = track.artist
        
        if (track.id == currentTrackId) {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.primary_red))
            holder.activeIndicator.visibility = View.VISIBLE
        } else {
            holder.title.setTextColor(holder.itemView.context.getColor(R.color.white))
            holder.activeIndicator.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onTrackClick(position)
        }
    }

    override fun getItemCount() = tracks.size
}
