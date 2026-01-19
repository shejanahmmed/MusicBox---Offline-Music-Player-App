package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent

data class Track(val id: Long, val title: String, val artist: String, val uri: String, val album: String? = null, val albumId: Long = -1L, val isActive: Boolean = false)

class TrackAdapter(private var tracks: List<Track>, private val onMoreClicked: (Track) -> Unit) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {

    class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_track_title)
        val artist: TextView = view.findViewById(R.id.tv_track_artist)
        val activeIndicator: FrameLayout = view.findViewById(R.id.fl_active_indicator)
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

        val ivArt = holder.root.findViewById<ImageView>(R.id.iv_album_art)
        if (ivArt != null) {
            // Always use loadTrackArt which now handles custom artwork, track thumbnails, and album art fallback
            MusicUtils.loadTrackArt(holder.root.context, track.id, track.albumId, ivArt)
        }

        // Click listener to open Now Playing and Start Service
        holder.root.setOnClickListener {
            
            // Set Playlist in Service
            MusicService.playlist = tracks
            // Use title/uri to find index if needed, but since we are clicking an item, 
            // we really should pass the index. However, we have the track object.
            // Let's iterate to find index to be safe (or pass position if we change adapter signature).
            // Since `tracks` is passed to adapter, we can find index of `track`.
            // But duplicate tracks might exist. Ideally use position from ViewHolder.
            // holder.bindingAdapterPosition gives position.
            
            val intent = Intent(holder.root.context, MusicService::class.java).apply {
                putExtra("TITLE", track.title)
                putExtra("ARTIST", track.artist)
                putExtra("URI", track.uri)
                putExtra("IS_PLAYING", true)
            }
            androidx.core.content.ContextCompat.startForegroundService(holder.root.context, intent)
            
            NowPlayingActivity.start(holder.root.context, track.title, track.artist)
        }
        
        // Options Button Click
        holder.root.findViewById<android.widget.ImageButton>(R.id.btn_options).setOnClickListener {
            onMoreClicked(track)
        }

        if (track.isActive) {
            holder.activeIndicator.visibility = View.GONE
            holder.title.setTextColor(holder.root.context.getColor(R.color.primary_red))
            holder.root.setBackgroundResource(R.drawable.bg_track_card_active)
        } else {
            holder.activeIndicator.visibility = View.GONE
            holder.title.setTextColor(holder.root.context.getColor(R.color.white))
            holder.root.setBackgroundResource(R.drawable.bg_track_card)
        }
    }

    override fun getItemCount() = tracks.size

    fun updateData(newTracks: List<Track>) {
        this.tracks = newTracks
        notifyDataSetChanged()
    }

    fun updateActiveTrack(activeId: Long) {
        var changed = false
        // Create new list to trigger recomposition/state change if needed, 
        // but for RecyclerView we can just modify items if they are mutable. 
        // Since Track is a data class (val), we need to copy.
        // Actually, let's map it.
        this.tracks = this.tracks.map { track ->
             val shouldBeActive = (track.id == activeId)
             if (track.isActive != shouldBeActive) {
                 changed = true
                 track.copy(isActive = shouldBeActive)
             } else {
                 track
             }
        }
        
        if (changed) {
            notifyDataSetChanged()
        }
    }
}
