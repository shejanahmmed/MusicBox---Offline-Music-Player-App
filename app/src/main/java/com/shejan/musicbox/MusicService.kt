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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media.app.NotificationCompat.MediaStyle
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.Collections

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var placeholderBitmap: Bitmap? = null

    // Queue Management
    companion object {
        const val CHANNEL_ID = "MusicBoxChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        
        // Thread-safe playlist to prevent race conditions
        var playlist: MutableList<Track> = Collections.synchronizedList(mutableListOf())
        var currentIndex: Int = -1
        var isShuffleEnabled = false
        
        // Repeat Modes
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
        var repeatMode = REPEAT_OFF
        
        var currentTrackUri: String? = null
    }

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isPlaying()) {
                    pause()
                }
            }
        }
    }

    private val deletionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
             if (intent?.action == "com.shejan.musicbox.TRACK_DELETED") {
                 val deletedUri = intent.getStringExtra("DELETED_TRACK_URI") ?: return
                 
                 synchronized(playlist) {
                     val mutableList = playlist.toMutableList()
                     val index = mutableList.indexOfFirst { it.uri == deletedUri }
                     
                     if (index != -1) {
                         val wasPlaying = (index == currentIndex)
                         mutableList.removeAt(index)
                         
                         // Update the synchronized list
                         playlist.clear()
                         playlist.addAll(mutableList)
                         
                         if (wasPlaying) {
                             if (currentIndex >= playlist.size) {
                                 currentIndex = 0 
                             }
                             if (playlist.isNotEmpty()) {
                                 playTrack(currentIndex)
                             } else {
                                 stopForeground(STOP_FOREGROUND_REMOVE)
                                 stopSelf()
                             }
                         } else {
                             if (index < currentIndex) {
                                 currentIndex--
                             }
                         }
                     }
                 }
             }
        }
    }

    private val binder = MusicBinder()

    inner class MusicBinder : android.os.Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusicBoxMediaSession")
        placeholderBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_album)

        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                play()
            }

            override fun onPause() {
                pause()
            }

            override fun onSkipToNext() {
                playNext()
            }

            override fun onSkipToPrevious() {
                playPrev()
            }

            override fun onSeekTo(pos: Long) {
                try {
                    mediaPlayer?.seekTo(pos.toInt())
                    updateMediaSessionState()
                } catch (_: Exception) {}
            }
        })
        mediaSession.isActive = true

        // Register Noisy Receiver
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(noisyReceiver, filter)
        }
        
        // Register Deletion Receiver
        val deleteFilter = android.content.IntentFilter("com.shejan.musicbox.TRACK_DELETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(deletionReceiver, deleteFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(deletionReceiver, deleteFilter)
        }

        // Initialize MediaPlayer
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = android.media.MediaPlayer().apply {
            setOnCompletionListener {
                if (repeatMode == REPEAT_ONE) {
                    playTrack(currentIndex)
                } else if (repeatMode == REPEAT_ALL) {
                    playNext()
                } else {
                    if (currentIndex < playlist.size - 1) {
                         playNext()
                    }
                }
            }
            setOnPreparedListener {
                it.start()
                updateNotification()
                updateMediaSessionMetadata()
                updateMediaSessionState()
                
                // Broadcast Change
                val track = getCurrentTrack()
                if (track != null) {
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply {
                        putExtra("IS_PLAYING", true)
                        putExtra("TITLE", track.title)
                        putExtra("ARTIST", track.artist)
                    })
                }
            }
            setOnErrorListener { mp, what, extra ->
                mp.reset()
                false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the service starts in foreground immediately to avoid crashes on Android 12+
        startForegroundWithPlaceholder()

        val action = intent?.action
        if (action != null) {
            if (action == Intent.ACTION_MEDIA_BUTTON) {
                androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
            }
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> playNext()
                ACTION_PREV -> playPrev()
            }
        } else {
             val uri = intent?.getStringExtra("URI")
             if (uri != null) {
                 if (uri == currentTrackUri) {
                      val index = playlist.indexOfFirst { it.uri == uri }
                      if (index != -1) currentIndex = index
                      if (!isPlaying()) play()
                 } else {
                      val index = playlist.indexOfFirst { it.uri == uri }
                      if (index != -1) {
                          currentIndex = index
                          playTrack(index)
                      }
                 }
             }
        }

        updateNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundWithPlaceholder() {
        val track = getCurrentTrack()
        val notification = if (track != null) {
            buildNotification(track.title, track.artist, isPlaying())
        } else {
            // Fallback notification if no track is yet loaded
            buildNotification("MusicBox", "Ready to play", false)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    fun playTrack(index: Int) {
        synchronized(playlist) {
            if (index < 0 || index >= playlist.size) return
            
            val track = playlist.getOrNull(index) ?: return
            currentIndex = index
            currentTrackUri = track.uri
            
            try {
                mediaPlayer?.reset()
                mediaPlayer?.setDataSource(applicationContext, track.uri.toUri())
                mediaPlayer?.prepareAsync() // FIXED: Non-blocking
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun play() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    updateNotification()
                    updateMediaSessionState()
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("IS_PLAYING", true) })
                }
            }
        } catch (e: IllegalStateException) {
            initMediaPlayer()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    updateNotification()
                    updateMediaSessionState()
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("IS_PLAYING", false) })
                }
            }
        } catch (e: IllegalStateException) {
            initMediaPlayer()
        }
    }
    
    fun playNext() {
        synchronized(playlist) {
            if (playlist.isEmpty()) return
            val nextIndex = if (isShuffleEnabled) {
                 (playlist.indices).random()
            } else {
                 (currentIndex + 1) % playlist.size
            }
            playTrack(nextIndex)
        }
    }
    
    fun playPrev() {
        synchronized(playlist) {
            if (playlist.isEmpty()) return
            val prevIndex = if (isShuffleEnabled) {
                 (playlist.indices).random()
            } else {
                 if (currentIndex <= 0) playlist.size - 1 else currentIndex - 1
            }
            playTrack(prevIndex)
        }
    }
    
    fun toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("REPEAT_MODE", repeatMode) })
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("SHUFFLE_STATE", isShuffleEnabled) })
    }
    
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (_: IllegalStateException) {
            false
        }
    }
    
    fun getCurrentTrack(): Track? {
        synchronized(playlist) {
            if (currentIndex in playlist.indices) {
                return playlist[currentIndex]
            }
        }
        return null
    }

    fun getDuration(): Int {
        return try { mediaPlayer?.duration ?: 0 } catch (_: Exception) { 0 }
    }

    fun getCurrentPosition(): Int {
        return try { mediaPlayer?.currentPosition ?: 0 } catch (_: Exception) { 0 }
    }

    fun seekTo(position: Int) {
        try { mediaPlayer?.seekTo(position) } catch (_: Exception) {}
    }

    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: 0
    }

    private fun updateNotification() {
        val track = getCurrentTrack() ?: return
        val isPlaying = isPlaying()
        
        val notification = buildNotification(track.title, track.artist, isPlaying)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): android.app.Notification {
        val playIntent = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
        val prevIntent = PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE)

        val contentIntent = Intent(this, NowPlayingActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP 
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audiotrack) 
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(placeholderBitmap) // FIXED: Use cached placeholder
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            
        if (isPlaying) {
             builder.addAction(R.drawable.ic_pause, "Pause", pauseIntent)
        } else {
             builder.addAction(R.drawable.ic_play_arrow, "Play", playIntent)
        }
           
        builder.addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession.sessionToken))
            .setOngoing(isPlaying)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Controls for music playback"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) { }
        
        try {
            unregisterReceiver(deletionReceiver)
        } catch (_: Exception) { }
        
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = try { mediaPlayer?.currentPosition?.toLong() ?: 0L } catch (_: Exception) { 0L }
        
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or 
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, position, 1.0f)
        
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaSessionMetadata() {
        val track = getCurrentTrack() ?: return
        val duration = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (_: Exception) { 0L }
        
        // Use placeholder for metadata too, or load art in background. 
        // For metadata it's okay to try a quick load, but avoiding blocking is key.
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown Album")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, placeholderBitmap)

        mediaSession.setMetadata(metadataBuilder.build())
    }
}
