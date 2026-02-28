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

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class VideoItem(
    val id: Long,
    val title: String,
    val duration: Long,   // milliseconds
    val uri: String,      // file path / content uri string
    val size: Long        // bytes
)

class VideoAdapter(
    private var videos: List<VideoItem>,
    private val onMoreClicked: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var currentActiveId: Long = -1L

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_video_title)
        val subtitle: TextView = view.findViewById(R.id.tv_video_subtitle)
        val duration: TextView = view.findViewById(R.id.tv_video_duration)
        val thumb: ImageView = view.findViewById(R.id.iv_video_thumb)
        val options: ImageButton = view.findViewById(R.id.btn_options)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]

        holder.title.text = video.title
        holder.subtitle.text = formatSize(video.size)
        holder.duration.text = formatDuration(video.duration)

        // Use a video icon as thumbnail placeholder
        holder.thumb.setImageResource(R.drawable.ic_videocam)
        holder.thumb.setPadding(8.dpToPx(holder.root), 8.dpToPx(holder.root), 8.dpToPx(holder.root), 8.dpToPx(holder.root))
        holder.thumb.setColorFilter(ContextCompat.getColor(holder.root.context, R.color.colorTextSecondary))

        // Active highlight
        if (video.id == currentActiveId) {
            holder.title.setTextColor(holder.root.context.getColor(R.color.primary_red))
            holder.root.setBackgroundResource(R.drawable.bg_track_card_active)
        } else {
            holder.title.setTextColor(ContextCompat.getColor(holder.root.context, R.color.colorTextPrimary))
            holder.root.setBackgroundResource(R.drawable.bg_track_card)
        }

        holder.root.setOnClickListener {
            MusicUtils.performHapticFeedback(holder.root.context)
            // Convert video list to Track list and play audio only via MusicService
            val trackList = videos.map { v ->
                Track(v.id, v.title, "Video", v.uri, null, -1L)
            }
            MusicService.updatePlaylist(trackList, position)

            val intent = Intent(holder.root.context, MusicService::class.java).apply {
                putExtra("TITLE", video.title)
                putExtra("ARTIST", "Video")
                putExtra("URI", video.uri)
            }
            ContextCompat.startForegroundService(holder.root.context, intent)
            NowPlayingActivity.start(holder.root.context, video.title, "Video")
        }

        holder.options.setOnClickListener {
            MusicUtils.performHapticFeedback(holder.options.context)
            onMoreClicked(video)
        }
    }

    override fun getItemCount() = videos.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newVideos: List<VideoItem>) {
        this.videos = newVideos
        notifyDataSetChanged()
    }

    fun updateActiveVideo(activeId: Long) {
        if (currentActiveId == activeId) return
        val oldId = currentActiveId
        currentActiveId = activeId
        val oldPos = videos.indexOfFirst { it.id == oldId }
        val newPos = videos.indexOfFirst { it.id == activeId }
        if (oldPos != -1) notifyItemChanged(oldPos)
        if (newPos != -1) notifyItemChanged(newPos)
    }

    fun indexOfVideo(videoId: Long) = videos.indexOfFirst { it.id == videoId }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%d KB", bytes / 1024)
            else -> "$bytes B"
        }
    }

    private fun Int.dpToPx(view: View): Int {
        return (this * view.context.resources.displayMetrics.density).toInt()
    }
}
