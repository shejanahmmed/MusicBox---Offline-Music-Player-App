package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrackSelectionAdapter(
    private val tracks: List<Track>,
    private val onSelectionChanged: (Track, Boolean) -> Unit
) : RecyclerView.Adapter<TrackSelectionAdapter.ViewHolder>() {

    private val selectedTracks = mutableSetOf<Long>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_track_title)
        val artist: TextView = view.findViewById(R.id.tv_track_artist)
        val checkBox: CheckBox = view.findViewById(R.id.cb_select)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_track_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks[position]
        holder.title.text = track.title
        holder.artist.text = track.artist
        
        // Remove listener before setting state to avoid infinite callbacks
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedTracks.contains(track.id)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedTracks.add(track.id)
            } else {
                selectedTracks.remove(track.id)
            }
            onSelectionChanged(track, isChecked)
        }

        holder.root.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
        }
    }

    override fun getItemCount() = tracks.size

    fun getSelectedTrackIds(): List<Long> {
        return selectedTracks.toList()
    }

    fun setSelectedTrackIds(ids: List<Long>) {
        selectedTracks.clear()
        selectedTracks.addAll(ids)
        notifyDataSetChanged()
    }
}
