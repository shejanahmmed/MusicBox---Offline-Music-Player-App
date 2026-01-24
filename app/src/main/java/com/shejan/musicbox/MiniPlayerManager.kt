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

import android.app.Activity
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs


object MiniPlayerManager {

    fun setup(activity: AppCompatActivity, getMusicService: () -> MusicService?) {
        val miniPlayer = activity.findViewById<View>(R.id.cl_mini_player) ?: return
        
        // Create gesture detector for swipe detection
        val gestureDetector = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if horizontal swipe is more significant than vertical
                if (abs(diffX) > abs(diffY)) {
                    // Minimum swipe distance and velocity thresholds
                    val SWIPE_THRESHOLD = 100
                    val SWIPE_VELOCITY_THRESHOLD = 100
                    
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe left-to-right: Previous track
                            val intent = Intent(activity, MusicService::class.java)
                            intent.action = MusicService.ACTION_PREV
                            activity.startService(intent)
                            return true
                        } else {
                            // Swipe right-to-left: Next track
                            val intent = Intent(activity, MusicService::class.java)
                            intent.action = MusicService.ACTION_NEXT
                            activity.startService(intent)
                            return true
                        }
                    }
                }
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Handle tap to open Now Playing (only when it's a confirmed tap, not a swipe)
                val service = getMusicService()
                val track = service?.getCurrentTrack()
                if (track != null) {
                    NowPlayingActivity.start(activity, track.title, track.artist)
                } else if (MusicService.currentIndex != -1 && MusicService.playlist.isNotEmpty()) {
                    val t = MusicService.playlist[MusicService.currentIndex]
                    NowPlayingActivity.start(activity, t.title, t.artist)
                }
                return true
            }
        })
        
        // Attach touch listener to mini player for gesture detection
        miniPlayer.setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            true // Consume all touch events to prevent double-tap
        }

        // Setup button click listeners
        activity.findViewById<ImageButton>(R.id.btn_mini_play)?.setOnClickListener {
            val service = getMusicService()
            if (service != null) {
                if (service.isPlaying()) service.pause() else service.play()
            } else {
                val intent = Intent(activity, MusicService::class.java)
                intent.action = MusicService.ACTION_PLAY
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
             activity.findViewById<View>(R.id.cl_mini_player)?.visibility = View.GONE
        }
    }
}
