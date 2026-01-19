package com.shejan.musicbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

object MiniPlayerManager {

    fun setup(activity: AppCompatActivity, musicService: MusicService?) {
        val miniPlayer = activity.findViewById<View>(R.id.cl_mini_player) ?: return
        
        // Setup Clicks (mostly specific service calls which need intents if service is not bound, but here we expect binding)
        // Actually, cleaner if we just use calls to update UI, and the activity sets up listeners or we set them here if we have service.
        
        // Open Now Playing
        miniPlayer.setOnClickListener {
            val track = musicService?.getCurrentTrack()
            if (track != null) {
                NowPlayingActivity.start(activity, track.title, track.artist)
            } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
                val t = MusicService.playlist[MusicService.currentIndex]
                 NowPlayingActivity.start(activity, t.title, t.artist)
            }
        }

        activity.findViewById<ImageButton>(R.id.btn_mini_play)?.setOnClickListener {
            if (musicService != null) { // Check if valid
                 if (musicService.isPlaying()) musicService.pause() else musicService.play()
            } else {
                // If service null (rare if bound), try start intent
                val intent = Intent(activity, MusicService::class.java)
                intent.action = MusicService.ACTION_PLAY // or toggle, but safer to just start
                // We actually don't know state. 
                // Better to rely on Service binding.
            }
        }
        
        activity.findViewById<ImageButton>(R.id.btn_mini_next)?.setOnClickListener {
            val intent = Intent(activity, MusicService::class.java)
            intent.action = MusicService.ACTION_NEXT
            activity.startService(intent)
        }
        
        activity.findViewById<ImageButton>(R.id.btn_mini_prev)?.setOnClickListener {
             val intent = Intent(activity, MusicService::class.java)
             intent.action = MusicService.ACTION_PREV
             activity.startService(intent)
        }
    }

    fun update(activity: Activity, musicService: MusicService?) {
        val titleView = activity.findViewById<TextView>(R.id.tv_mini_title) ?: return
        val artistView = activity.findViewById<TextView>(R.id.tv_mini_artist) ?: return
        val playButton = activity.findViewById<ImageButton>(R.id.btn_mini_play) ?: return
        val miniArt = activity.findViewById<ImageView>(R.id.iv_mini_art) ?: return
        
        var track: Track? = null
        var isPlaying = false
        
        if (musicService != null) {
            track = musicService.getCurrentTrack()
            isPlaying = musicService.isPlaying()
        } else {
            // Fallback
             if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
                track = MusicService.playlist[MusicService.currentIndex]
            }
        }
        
        if (track != null) {
            // Apply Custom Metadata on the fly? getMetadata handles it inside applyMetadata, 
            // but we usually call applyMetadata when loading list. 
            // Here we have a Track object. If it came from Service which came from list, it has metadata.
            // If it came from restore, it might not.
            // Let's safe-check:
            val safeTrack = TrackMetadataManager.applyMetadata(activity, track)
            titleView.text = safeTrack.title
            artistView.text = safeTrack.artist
            playButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            
            MusicUtils.loadTrackArt(activity, safeTrack.id, safeTrack.albumId, miniArt)
            
            activity.findViewById<View>(R.id.cl_mini_player)?.visibility = View.VISIBLE
        } else {
             // Hide or show default?
             // Usually hide if nothing playing. 
             // But for consistent UI we might want to keep it or hide. 
             // Existing code kept it but empty.
             // Let's assume visibility stays VISIBLE but default text.
             
             // If completely nothing, maybe hide?
             if (MusicService.playlist.isEmpty()) {
                 // activity.findViewById<View>(R.id.cl_mini_player)?.visibility = View.GONE
                 // User might prefer it hidden.
             }
        }
    }
}
