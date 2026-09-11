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
import android.content.Context
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
import androidx.core.content.edit
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import java.util.Collections

import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat

    private var exoPlayer: ExoPlayer? = null
    private var placeholderBitmap: Bitmap? = null
    private var playWhenPrepared = true
    
    // Sleep Timer
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    var sleepTimerEndTime: Long = 0L

    // Notification debounce — prevents shedding by the system (rate-limit: 5/sec)
    private val notificationHandler = Handler(Looper.getMainLooper())
    private var pendingNotificationRunnable: Runnable? = null
    private fun scheduleNotificationUpdate() {
        pendingNotificationRunnable?.let { notificationHandler.removeCallbacks(it) }
        val r = Runnable { updateNotification() }
        pendingNotificationRunnable = r
        notificationHandler.postDelayed(r, 200L) // coalesce bursts into one post per 200ms
    }

    // Audio Focus & Background-safe Volume Controller
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private val volumeHandler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null
    private var currentVolume: Float = 1.0f

    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isPlaying()) {
                    pauseWithFade(durationMs = 150L)
                }
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                isNoisyReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (_: Exception) {}
        }
    }

    fun setVolume(vol: Float) {
        currentVolume = vol.coerceIn(0.0f, 1.0f)
        try {
            exoPlayer?.volume = currentVolume
        } catch (_: Exception) {}
    }

    /**
     * Smoothly transitions playback volume using a background-safe Handler loop.
     * Unlike ValueAnimator, this does NOT depend on Choreographer or screen VSYNC,
     * ensuring 100% reliable volume transitions during screen-off / background playback.
     */
    fun fadeVolume(targetVolume: Float, durationMs: Long, onComplete: (() -> Unit)? = null) {
        cancelVolumeFade()
        val clampedTarget = targetVolume.coerceIn(0.0f, 1.0f)
        val startVol = currentVolume
        if (durationMs <= 0L || Math.abs(startVol - clampedTarget) < 0.01f) {
            setVolume(clampedTarget)
            onComplete?.invoke()
            return
        }

        val startTime = android.os.SystemClock.uptimeMillis()
        val stepIntervalMs = 20L

        volumeFadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = android.os.SystemClock.uptimeMillis() - startTime
                val fraction = (elapsed.toFloat() / durationMs).coerceIn(0.0f, 1.0f)
                // Decelerate interpolation
                val interpolated = 1.0f - (1.0f - fraction) * (1.0f - fraction)
                val newVol = startVol + (clampedTarget - startVol) * interpolated
                setVolume(newVol)

                if (fraction < 1.0f) {
                    volumeHandler.postDelayed(this, stepIntervalMs)
                } else {
                    setVolume(clampedTarget)
                    volumeFadeRunnable = null
                    onComplete?.invoke()
                }
            }
        }
        volumeHandler.post(volumeFadeRunnable!!)
    }

    fun cancelVolumeFade(snapToTarget: Float? = null) {
        volumeFadeRunnable?.let { volumeHandler.removeCallbacks(it) }
        volumeFadeRunnable = null
        snapToTarget?.let { setVolume(it) }
    }
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        android.util.Log.d("MusicService", "onAudioFocusChange: focusChange=$focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    if (!isPlaying()) play() // Resume playback
                } else {
                    fadeVolume(1.0f, 250L) // Restore volume smoothly
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (isPlaying()) pauseWithFade(150L, abandonFocus = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying()) {
                    resumeOnFocusGain = true
                    pauseWithFade(150L, abandonFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying()) fadeVolume(0.2f, 200L)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
                
             val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                
            audioFocusRequest = request
            when (audioManager.requestAudioFocus(request)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> true
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    resumeOnFocusGain = true
                    false
                }
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener, 
                AudioManager.STREAM_MUSIC, 
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
             @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    // Queue Management
    companion object {
        var instance: MusicService? = null
        
        const val CHANNEL_ID = "MusicBoxChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        
        const val STATE_IDLE = 0
        const val STATE_INITIALIZED = 1
        const val STATE_PREPARING = 2
        const val STATE_PREPARED = 3
        const val STATE_STARTED = 4
        const val STATE_PAUSED = 5
        const val STATE_STOPPED = 6
        const val STATE_PLAYBACK_COMPLETED = 7
        const val STATE_ERROR = 8
        const val STATE_END = 9
        
        private const val PREF_NAME = "MusicBoxPlaybackPrefs"
        private const val KEY_SHUFFLE = "shuffle_enabled"
        private const val KEY_REPEAT = "repeat_mode"
        
        // Thread-safe playlist to prevent race conditions
        var playlist: MutableList<Track> = Collections.synchronizedList(mutableListOf())
        var originalPlaylist: MutableList<Track> = Collections.synchronizedList(mutableListOf()) // Store original order
        var currentIndex: Int = -1
        
        var isShuffleEnabled = false
        var repeatMode = 0
        
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
        
        var currentTrackUri: String? = null

        fun initPrefs(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            isShuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
            repeatMode = prefs.getInt(KEY_REPEAT, REPEAT_OFF)
        }
        
        private fun saveShuffle(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit {
                putBoolean(KEY_SHUFFLE, enabled)
            }
        }
        
        private fun saveRepeat(context: Context, mode: Int) {
            context.getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit {
                putInt(KEY_REPEAT, mode)
            }
        }
        
        fun updatePlaylist(newTracks: List<Track>, startingIndex: Int) {
            synchronized(playlist) {
                originalPlaylist.clear()
                originalPlaylist.addAll(newTracks)
                
                playlist.clear()
                if (isShuffleEnabled) {
                    val shuffled = newTracks.toMutableList()
                    if (startingIndex in shuffled.indices) {
                        val startTrack = shuffled.removeAt(startingIndex)
                        shuffled.shuffle()
                        shuffled.add(0, startTrack)
                        currentIndex = 0
                    } else {
                        shuffled.shuffle()
                        currentIndex = 0
                    }
                    playlist.addAll(shuffled)
                } else {
                    playlist.addAll(newTracks)
                    currentIndex = startingIndex
                }
            }
        }

        fun getQueue(): List<Track> {
            return synchronized(playlist) { ArrayList(playlist) }
        }

        fun moveQueueItem(fromPos: Int, toPos: Int): Boolean {
            return instance?.moveQueueItem(fromPos, toPos) ?: run {
                synchronized(playlist) {
                    if (fromPos !in playlist.indices || toPos !in playlist.indices || fromPos == toPos) return false
                    val item = playlist.removeAt(fromPos)
                    playlist.add(toPos, item)
                    if (currentIndex == fromPos) {
                        currentIndex = toPos
                    } else if (fromPos < currentIndex && toPos >= currentIndex) {
                        currentIndex--
                    } else if (fromPos > currentIndex && toPos <= currentIndex) {
                        currentIndex++
                    }
                    true
                }
            }
        }

        fun removeQueueItem(pos: Int): Boolean {
            return instance?.removeQueueItem(pos) ?: run {
                synchronized(playlist) {
                    if (pos !in playlist.indices) return false
                    val removedTrack = playlist.removeAt(pos)
                    synchronized(originalPlaylist) {
                        originalPlaylist.removeIf { it.uri == removedTrack.uri }
                    }
                    if (pos < currentIndex) {
                        currentIndex--
                    }
                    true
                }
            }
        }

        fun addToPlayNext(track: Track) {
            instance?.addToPlayNext(track) ?: run {
                synchronized(playlist) {
                    if (playlist.isEmpty()) {
                        updatePlaylist(listOf(track), 0)
                    } else {
                        val index = playlist.indexOfFirst { it.uri == track.uri }
                        if (index != -1) {
                            val item = playlist.removeAt(index)
                            val targetIndex = (currentIndex + 1).coerceIn(0, playlist.size)
                            playlist.add(targetIndex, item)
                            if (index < currentIndex) {
                                currentIndex--
                            }
                        } else {
                            val targetIndex = (currentIndex + 1).coerceIn(0, playlist.size)
                            playlist.add(targetIndex, track)
                        }
                    }
                }
            }
        }

        fun addToPlayLast(track: Track) {
            instance?.addToPlayLast(track) ?: run {
                synchronized(playlist) {
                    if (playlist.isEmpty()) {
                        updatePlaylist(listOf(track), 0)
                    } else {
                        val index = playlist.indexOfFirst { it.uri == track.uri }
                        if (index != -1) {
                            val item = playlist.removeAt(index)
                            playlist.add(item)
                            if (index < currentIndex) {
                                currentIndex--
                            }
                        } else {
                            playlist.add(track)
                        }
                    }
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            android.util.Log.d("MusicService", "onPlaybackStateChanged: $stateName, isPlaying=${isPlaying()}, playWhenReady=${exoPlayer?.playWhenReady}, vol=${exoPlayer?.volume}")

            when (playbackState) {
                Player.STATE_READY -> {
                    scheduleNotificationUpdate()
                    updateMediaSessionMetadata()
                    updateMediaSessionState()
                    saveState()
                    
                    val track = getCurrentTrack()
                    if (track != null) {
                        sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply {
                            putExtra("IS_PLAYING", isPlaying())
                            putExtra("TITLE", track.title)
                            putExtra("ARTIST", track.artist)
                        })
                        BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
                    }
                }
                Player.STATE_ENDED -> {
                    saveState()
                    if (repeatMode == REPEAT_ONE) {
                        playTrack(currentIndex)
                    } else {
                        playNext(autoPlay = true)
                    }
                }
                Player.STATE_BUFFERING -> {}
                Player.STATE_IDLE -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            android.util.Log.d("MusicService", "onIsPlayingChanged: isPlaying=$isPlaying, vol=${exoPlayer?.volume}")
            scheduleNotificationUpdate()
            updateMediaSessionState()
            sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply {
                putExtra("IS_PLAYING", isPlaying)
            })
            BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            android.util.Log.d("MusicService", "onPlaybackSuppressionReasonChanged: reason=$playbackSuppressionReason")
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("MusicService", "ExoPlayer Error: ${error.errorCodeName} (${error.errorCode}) - ${error.message}", error)
            updateNotification()
            updateMediaSessionState()
            sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply {
                putExtra("IS_PLAYING", false)
            })
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            android.util.Log.d("MusicService", "onAudioSessionIdChanged: $audioSessionId")
            if (audioSessionId != 0 && audioSessionId != android.media.audiofx.AudioEffect.ERROR_BAD_VALUE) {
                try {
                    EqManager.attach(applicationContext, audioSessionId)
                } catch (_: Exception) {}
            }
        }
    }

    private val deletionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
             if (intent?.action == "com.shejan.musicbox.TRACK_DELETED") {
                 val deletedUri = intent.getStringExtra("DELETED_TRACK_URI") ?: return
                 
                 synchronized(playlist) {
                     val mutableList = playlist.toMutableList()
                     val index = mutableList.indexOfFirst { it.uri == deletedUri }
                     
                     if (index != -1) {
                         val wasPlaying = (index == currentIndex)
                         mutableList.removeAt(index)
                         
                         // Update synchronized lists
                         playlist.clear()
                         playlist.addAll(mutableList)
                         
                         // Also remove from original if it exists
                         synchronized(originalPlaylist) {
                             val origMutable = originalPlaylist.toMutableList()
                             origMutable.removeIf { it.uri == deletedUri }
                             originalPlaylist.clear()
                             originalPlaylist.addAll(origMutable)
                         }
                         
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
        instance = this
        initPrefs(this) // Load persistent settings
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                    seekTo(pos.toInt())
                    updateMediaSessionState()
                } catch (_: Exception) {}
            }
        })
        mediaSession.isActive = true
        
        // Register Deletion Receiver
        val deleteFilter = IntentFilter("com.shejan.musicbox.TRACK_DELETED")
        androidx.core.content.ContextCompat.registerReceiver(this, deletionReceiver, deleteFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)

        // Initialize ExoPlayer
        initExoPlayer()
        
        // Restore State (Queue and Position)
        restoreState()
        BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun initExoPlayer() {
        exoPlayer?.release()
        val audioAttributes = Media3AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        currentVolume = 1.0f
        exoPlayer = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(audioAttributes, false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(false)
            .build().apply {
                volume = 1.0f
                addListener(playerListener)
            }
        attachEqualizer()
    }

    private fun attachEqualizer() {
        val sessionId = getAudioSessionId()
        if (sessionId != 0 && sessionId != android.media.audiofx.AudioEffect.ERROR_BAD_VALUE) {
            try {
                EqManager.attach(applicationContext, sessionId)
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure the service starts in foreground immediately to avoid crashes on Android 12+
        startForegroundWithPlaceholder()

        val action = intent?.action
        if (action != null) {
            if (action == Intent.ACTION_MEDIA_BUTTON) {
                if (::mediaSession.isInitialized) {
                    androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
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
                      // Just re-sync index if needed, but it should be correct
                      if (!isPlaying()) play()
                 } else {
                      // Playing a specific track from a list context (usually TracksActivity)
                      // This usually implies a new playlist context or jumping to a track in current.
                      // NOTE: We assume 'playlist' is already updated by caller BEFORE calling service,
                      // OR the caller set the playlist static variable.
                      // Since playlist is static in Companion, it is already set.
                      
                      // If shuffle is ON, we should probably reshuffle but keep this track first?
                      // Or if the user just clicked a song, we might want to respect that.
                      // For simplicity, if user clicks a song, we find it in current playlist.
                      
                      val index = playlist.indexOfFirst { it.uri == uri }
                      if (index != -1) {
                          currentIndex = index
                          playTrack(index)
                      } else {
                          // Track not in current shuffled list? 
                          // It might be in original. If so, and we are shuffled, what to do?
                          // Ideally, the caller (TracksActivity) sets the playlist.
                          // If TracksActivity loaded a NEW list, it overwrote 'playlist'.
                          
                          // We need to ensure originalPlaylist is also set when a new list is loaded.
                          // This logic isn't here, it's where playlist is assigned.
                          // Assuming TracksActivity assigns playlist directly.
                          
                          // If playlist was just assigned, we should sync originalPlaylist.
                          // We'll add a helper for setting playlist properly.
                      }
                 }
             }
        }

        updateNotification()
        return START_STICKY
    }
    

    private fun startForegroundWithPlaceholder() {
        val track = getCurrentTrack()
        val notification = if (track != null) {
            buildNotification(track.title, track.artist, isPlaying(), placeholderBitmap)
        } else {
            // Fallback notification if no track is yet loaded
            buildNotification("MusicBox", "Ready to play", false, placeholderBitmap)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stop service when user swipes app away from recents
        pause(abandonFocus = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun playTrack(index: Int) {
        synchronized(playlist) {
            if (index < 0 || index >= playlist.size) return
            
            // Promote to started service to survive unbinding
            try {
                androidx.core.content.ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, MusicService::class.java))
            } catch (_: Exception) {}
            
            val track = playlist.getOrNull(index) ?: return
            currentIndex = index
            currentTrackUri = track.uri
            playWhenPrepared = true
            
            // Update widget immediately with new track metadata
            BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
            
            try {
                if (exoPlayer == null) {
                    initExoPlayer()
                }
                
                val sourceUri = if (track.uri.startsWith("content://") || track.uri.startsWith("http://") || track.uri.startsWith("https://")) {
                    track.uri.toUri()
                } else if (track.id > 0) {
                    if (track.artist == "Video") {
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, track.id)
                    } else {
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
                    }
                } else {
                    android.net.Uri.fromFile(java.io.File(track.uri))
                }
                
                android.util.Log.d("MusicService", "Attempting to play URI: $sourceUri")
                
                val mediaItem = MediaItem.fromUri(sourceUri)
                exoPlayer?.let { player ->
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    
                    if (requestAudioFocus()) {
                        registerNoisyReceiver()
                        cancelVolumeFade()
                        setVolume(1.0f) // Keep normal listening volume for seamless track transitions
                        player.play()
                    } else {
                        player.pause()
                    }
                }
                attachEqualizer()
                
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("MusicService", "Exception in playTrack: ${e.message}", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(applicationContext, "Failed to load media file.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun play() {
        try {
            // Promote to started service to survive unbinding
            try {
                androidx.core.content.ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, MusicService::class.java))
            } catch (_: Exception) {}
            
            if (exoPlayer == null) {
                initExoPlayer()
            }
            
            exoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount == 0) {
                    val currentIdx = currentIndex
                    if (currentIdx != -1) {
                        playTrack(currentIdx)
                    }
                    return
                }
                
                if (!isPlaying()) {
                    if (!requestAudioFocus()) return@let
                    
                    registerNoisyReceiver()
                    cancelVolumeFade()
                    setVolume(1.0f) // Ensure full volume on start/resume
                    player.play()
                    updateNotification()
                    updateMediaSessionState()
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("IS_PLAYING", true) })
                    BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Exception in play: ${e.message}", e)
        }
    }

    fun pause(abandonFocus: Boolean = true) {
        if (abandonFocus) abandonAudioFocus()
        unregisterNoisyReceiver()
        cancelVolumeFade()
        setVolume(1.0f) // Ensure volume is normalized for next session
        
        try {
            exoPlayer?.let { player ->
                if (player.isPlaying || player.playWhenReady) {
                    player.pause()
                    updateNotification()
                    updateMediaSessionState()
                    saveState() // Save specific position on pause
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("IS_PLAYING", false) })
                    BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicService", "Exception in pause: ${e.message}", e)
        }
    }

    fun pauseWithFade(durationMs: Long = 150L, abandonFocus: Boolean = true) {
        if (!isPlaying()) {
            pause(abandonFocus)
            return
        }
        fadeVolume(0.0f, durationMs) {
            pause(abandonFocus)
            setVolume(1.0f) // reset baseline volume for next playback session
        }
    }
    
    fun playNext(autoPlay: Boolean = false) {
        synchronized(playlist) {
            if (playlist.isEmpty()) return
            
            // Standard Next Logic: currentIndex + 1
            if (currentIndex < playlist.size - 1) {
                playTrack(currentIndex + 1)
            } else {
                // End of list
                if (repeatMode == REPEAT_ALL || repeatMode == REPEAT_ONE) { 
                    // If Repeat One, Next button still goes next (wrapping), unlike auto-completion
                    playTrack(0)
                } else {
                    if (!autoPlay) {
                         playTrack(0)
                    } else {
                         // Stop playback
                         pause()
                         exoPlayer?.seekTo(0)
                         currentIndex = 0 // Reset to 0 but don't play
                    }
                }
            }
        }
    }
    
    fun playPrev() {
        synchronized(playlist) {
            if (playlist.isEmpty()) return
            
            // If more than 3 sec played, restart song
            if (getCurrentPosition() > 3000) {
                seekTo(0)
                return
            }
            
            if (currentIndex > 0) {
                playTrack(currentIndex - 1)
            } else {
                 playTrack(playlist.size - 1)
            }
        }
    }
    
    fun toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        saveRepeat(this, repeatMode) // Persist change
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("REPEAT_MODE", repeatMode) })
        BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        saveShuffle(this, isShuffleEnabled) // Persist change
        
        synchronized(playlist) {
            val currentTrack = getCurrentTrack()
            
            if (isShuffleEnabled) {
                // Shuffle ON
                // 1. Sync original if empty (recovery)
                if (originalPlaylist.isEmpty()) { 
                    originalPlaylist.addAll(playlist)
                }
                
                // 2. Create shuffled list
                val newOrder = ArrayList(originalPlaylist)
                // Remove current playing to prevent duplicate issues or just to put it at top
                if (currentTrack != null) {
                    newOrder.removeIf { it.uri == currentTrack.uri }
                    newOrder.shuffle()
                    newOrder.add(0, currentTrack)
                } else {
                    newOrder.shuffle()
                }
                
                playlist.clear()
                playlist.addAll(newOrder)
                currentIndex = 0 // Because we put current track at 0
                
            } else {
                // Shuffle OFF
                // Restore original order
                if (originalPlaylist.isNotEmpty()) {
                    playlist.clear()
                    playlist.addAll(originalPlaylist)
                    
                    // Find current track in original list
                    if (currentTrack != null) {
                        val index = playlist.indexOfFirst { it.uri == currentTrack.uri }
                        currentIndex = if (index != -1) index else 0
                    } else {
                        currentIndex = 0
                    }
                }
            }
        }
        
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("SHUFFLE_STATE", isShuffleEnabled) })
        BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
    }
    
    fun getQueue(): List<Track> {
        return synchronized(playlist) { ArrayList(playlist) }
    }

    fun getOriginalQueue(): List<Track> {
        return synchronized(originalPlaylist) { ArrayList(originalPlaylist) }
    }

    fun getCurrentIndex(): Int {
        return currentIndex
    }

    fun getQueueSize(): Int {
        return synchronized(playlist) { playlist.size }
    }

    fun isQueueEmpty(): Boolean {
        return synchronized(playlist) { playlist.isEmpty() }
    }

    fun moveQueueItem(fromPos: Int, toPos: Int): Boolean {
        synchronized(playlist) {
            if (fromPos !in playlist.indices || toPos !in playlist.indices || fromPos == toPos) return false
            val item = playlist.removeAt(fromPos)
            playlist.add(toPos, item)
            if (currentIndex == fromPos) {
                currentIndex = toPos
            } else if (fromPos < currentIndex && toPos >= currentIndex) {
                currentIndex--
            } else if (fromPos > currentIndex && toPos <= currentIndex) {
                currentIndex++
            }
            saveState()
            return true
        }
    }

    fun removeQueueItem(pos: Int): Boolean {
        synchronized(playlist) {
            if (pos !in playlist.indices) return false
            val wasCurrent = (pos == currentIndex)
            val removedTrack = playlist.removeAt(pos)

            synchronized(originalPlaylist) {
                originalPlaylist.removeIf { it.uri == removedTrack.uri }
            }

            if (pos < currentIndex) {
                currentIndex--
            }

            saveState()

            if (wasCurrent) {
                if (playlist.isNotEmpty()) {
                    val nextIndex = currentIndex.coerceIn(0, playlist.size - 1)
                    playTrack(nextIndex)
                } else {
                    pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            return true
        }
    }

    fun addToPlayNext(track: Track) {
        synchronized(playlist) {
            if (playlist.isEmpty()) {
                updatePlaylist(listOf(track), 0)
                playTrack(0)
            } else {
                val index = playlist.indexOfFirst { it.uri == track.uri }
                if (index != -1) {
                    val item = playlist.removeAt(index)
                    val targetIndex = (currentIndex + 1).coerceIn(0, playlist.size)
                    playlist.add(targetIndex, item)
                    if (index < currentIndex) {
                        currentIndex--
                    }
                } else {
                    val targetIndex = (currentIndex + 1).coerceIn(0, playlist.size)
                    playlist.add(targetIndex, track)
                }
                saveState()
            }
        }
    }

    fun addToPlayLast(track: Track) {
        synchronized(playlist) {
            if (playlist.isEmpty()) {
                updatePlaylist(listOf(track), 0)
                playTrack(0)
            } else {
                val index = playlist.indexOfFirst { it.uri == track.uri }
                if (index != -1) {
                    val item = playlist.removeAt(index)
                    playlist.add(item)
                    if (index < currentIndex) {
                        currentIndex--
                    }
                } else {
                    playlist.add(track)
                }
                saveState()
            }
        }
    }

    fun isPlaying(): Boolean {
        return exoPlayer?.isPlaying ?: false
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
        val dur = exoPlayer?.duration ?: 0L
        return if (dur > 0 && dur != C.TIME_UNSET) dur.toInt() else 0
    }

    fun getCurrentPosition(): Int {
        val pos = exoPlayer?.currentPosition ?: 0L
        return if (pos >= 0) {
            pos.toInt()
        } else {
            val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            prefs.getInt("current_position", 0)
        }
    }

    fun seekTo(position: Int) {
        try { 
            exoPlayer?.seekTo(position.toLong())
            BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
        } catch (_: Exception) {
            getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putInt("current_position", position).apply()
            BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    fun getAudioSessionId(): Int {
        return try { exoPlayer?.audioSessionId ?: 0 } catch (_: Exception) { 0 }
    }

    // High-Resolution DSP Playback Parameters
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        val currentPitch = exoPlayer?.playbackParameters?.pitch ?: 1.0f
        exoPlayer?.playbackParameters = PlaybackParameters(clampedSpeed, currentPitch)
    }

    fun setPlaybackPitch(pitch: Float) {
        val clampedPitch = pitch.coerceIn(0.25f, 2.0f)
        val currentSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f
        exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed, clampedPitch)
    }

    fun getPlaybackSpeed(): Float {
        return exoPlayer?.playbackParameters?.speed ?: 1.0f
    }

    fun getPlaybackPitch(): Float {
        return exoPlayer?.playbackParameters?.pitch ?: 1.0f
    }

    // Scope for UI/Notification updates
    private val uiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

    private var artworkJob: kotlinx.coroutines.Job? = null

    private fun updateNotification() {
        val track = getCurrentTrack() ?: return
        val isPlaying = isPlaying()
        
        // 1. Show immediate notification with placeholder to ensure responsiveness
        var notification = buildNotification(track.title, track.artist, isPlaying, placeholderBitmap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // 2. Load High-Res Artwork in Background
        artworkJob?.cancel() // Cancel previous load to prevent race condition
        artworkJob = uiScope.launch {
            val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                MusicUtils.getTrackArtworkBitmap(applicationContext, track.id, track.albumId, track.uri)
            }
            
            // Check if track is still the same (prevent race condition)
            val current = getCurrentTrack()
            if (current?.uri == track.uri) {
                // Update notification with real artwork
                notification = buildNotification(track.title, track.artist, isPlaying, bitmap ?: placeholderBitmap)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean, largeIcon: Bitmap?): android.app.Notification {
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
            .setLargeIcon(largeIcon) 
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
        instance = null
        uiScope.cancel() // Cancel all pending UI updates
        cancelVolumeFade()
        volumeHandler.removeCallbacksAndMessages(null)
        unregisterNoisyReceiver()
        cancelSleepTimer() // Clean up runnables
        sleepTimerHandler.removeCallbacksAndMessages(null) // Detailed cleanup
        abandonAudioFocus()
        
        saveState()
        saveStateExecutor.shutdown() // Prevent thread leaks
        
        try {
            unregisterReceiver(deletionReceiver)
        } catch (_: Exception) { }
        
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        EqManager.release()
        try {
            if (::mediaSession.isInitialized) {
                mediaSession.release()
            }
        } catch (_: Exception) { }
        
        // Final widget update to reflect stopped/paused state
        BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
    }

    // SingleThreadExecutor for saving state sequentially to prevent race conditions
    private val saveStateExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun saveState() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        
        // Clone logic to avoid concurrency issues during saving
        val currentPlaylistCopy = synchronized(playlist) { ArrayList(playlist) }
        val originalPlaylistCopy = synchronized(playlist) { ArrayList(originalPlaylist) }
        val currentIndexCopy = currentIndex
        val currentPosCopy = getCurrentPosition()
        val currentDurationCopy = getDuration()
        val currentUriCopy = currentTrackUri

        // Run Serialization in Background Thread Sequentially
        try {
            saveStateExecutor.execute {
                val jsonArray = org.json.JSONArray()
                if (currentPlaylistCopy.isNotEmpty()) {
                    for (track in currentPlaylistCopy) {
                        val jsonObj = org.json.JSONObject()
                        jsonObj.put("id", track.id)
                        jsonObj.put("title", track.title)
                        jsonObj.put("artist", track.artist)
                        jsonObj.put("uri", track.uri)
                        jsonObj.put("album", track.album ?: "")
                        jsonObj.put("albumId", track.albumId)
                        jsonArray.put(jsonObj)
                    }
                }
                
                val jsonArrayComp = org.json.JSONArray()
                if (isShuffleEnabled && originalPlaylistCopy.isNotEmpty()) {
                     for (track in originalPlaylistCopy) {
                        val jsonObj = org.json.JSONObject()
                        jsonObj.put("id", track.id)
                        jsonObj.put("title", track.title)
                        jsonObj.put("artist", track.artist)
                        jsonObj.put("uri", track.uri)
                        jsonObj.put("album", track.album ?: "")
                        jsonObj.put("albumId", track.albumId)
                        jsonArrayComp.put(jsonObj)
                     }
                }
                
                // Apply to Prefs (Index/Pos only)
                editor.putInt("current_index", currentIndexCopy)
                editor.putInt("current_position", currentPosCopy)
                editor.putInt("current_duration", currentDurationCopy)
                editor.putString("current_track_uri", currentUriCopy)
                // Remove legacy keys to save space
                editor.remove("saved_playlist")
                editor.remove("saved_original_playlist")
                editor.apply()
                
                // Save Queue to File (Avoids SharedPrefs size limit)
                try {
                    val file = java.io.File(filesDir, "queue_layout.json")
                    val root = org.json.JSONObject()
                    if (currentPlaylistCopy.isNotEmpty()) root.put("playlist", jsonArray)
                    if (isShuffleEnabled && originalPlaylistCopy.isNotEmpty()) root.put("original", jsonArrayComp)
                    file.writeText(root.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            e.printStackTrace()
        }
    }
    
    private fun restoreState() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val idx = prefs.getInt("current_index", -1)
        val pos = prefs.getInt("current_position", 0)
        val savedPlaylistLegacy = prefs.getString("saved_playlist", null)
        val savedOriginalLegacy = prefs.getString("saved_original_playlist", null)
        
        // 1. Try Loading from File
        val file = java.io.File(filesDir, "queue_layout.json")
        if (file.exists()) {
            try {
                val content = file.readText()
                val root = org.json.JSONObject(content)
                val playlistArray = root.optJSONArray("playlist")
                val originalArray = root.optJSONArray("original")
                
                if (playlistArray != null) {
                    val list = mutableListOf<Track>()
                    for (i in 0 until playlistArray.length()) {
                        val obj = playlistArray.getJSONObject(i)
                        list.add(Track(
                            obj.getLong("id"),
                            obj.getString("title"),
                            obj.getString("artist"),
                            obj.getString("uri"),
                            obj.optString("album").ifEmpty { null },
                            obj.optLong("albumId", -1L)
                        ))
                    }
                    synchronized(playlist) {
                        playlist.clear()
                        playlist.addAll(list)
                    }
                }
                
                if (originalArray != null) {
                    val list = mutableListOf<Track>()
                    for (i in 0 until originalArray.length()) {
                        val obj = originalArray.getJSONObject(i)
                        list.add(Track(
                            obj.getLong("id"),
                            obj.getString("title"),
                            obj.getString("artist"),
                            obj.getString("uri"),
                            obj.optString("album").ifEmpty { null },
                            obj.optLong("albumId", -1L)
                        ))
                    }
                    synchronized(playlist) {
                        originalPlaylist.clear()
                        originalPlaylist.addAll(list)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        } else {
            // 2. Fallback to Legacy Prefs
            if (savedPlaylistLegacy != null) {
                try {
                    val list = mutableListOf<Track>()
                    val jsonArray = org.json.JSONArray(savedPlaylistLegacy)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(Track(
                            obj.getLong("id"),
                            obj.getString("title"),
                            obj.getString("artist"),
                            obj.getString("uri"),
                            obj.optString("album").ifEmpty { null },
                            obj.optLong("albumId", -1L)
                        ))
                    }
                    
                    synchronized(playlist) {
                        playlist.clear()
                        playlist.addAll(list)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            
            if (savedOriginalLegacy != null) {
                 try {
                    val list = mutableListOf<Track>()
                    val jsonArray = org.json.JSONArray(savedOriginalLegacy)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(Track(
                            obj.getLong("id"),
                            obj.getString("title"),
                            obj.getString("artist"),
                            obj.getString("uri"),
                            obj.optString("album").ifEmpty { null },
                            obj.optLong("albumId", -1L)
                        ))
                    }
                    
                    synchronized(playlist) {
                        originalPlaylist.clear()
                        originalPlaylist.addAll(list)
                    }
                 } catch (e: Exception) { e.printStackTrace() }
            }
        }

        
        // Restore Index and Prepare Player
        // Stability check: prevent Out of Bounds if playlist size changed
        if (idx != -1 && idx < playlist.size) {
            val track = playlist[idx]
            currentIndex = idx
            currentTrackUri = track.uri
            
            // Init player but DO NOT START
            try {
                if (exoPlayer == null) {
                    initExoPlayer()
                }
                
                val sourceUri = if (track.uri.startsWith("content://") || track.uri.startsWith("http://") || track.uri.startsWith("https://")) {
                    track.uri.toUri()
                } else if (track.id > 0) {
                    if (track.artist == "Video") {
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, track.id)
                    } else {
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id)
                    }
                } else {
                    android.net.Uri.fromFile(java.io.File(track.uri))
                }
                
                val mediaItem = MediaItem.fromUri(sourceUri)
                exoPlayer?.let { player ->
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.seekTo(pos.toLong())
                    player.playWhenReady = false
                }
                updateNotification()
                updateMediaSessionMetadata()
                updateMediaSessionState()
                BaseMusicWidgetProvider.updateAllWidgets(applicationContext)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = try { exoPlayer?.currentPosition ?: 0L } catch (_: Exception) { 0L }
        
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

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        
        val delayMs = minutes * 60 * 1000L
        sleepTimerEndTime = System.currentTimeMillis() + delayMs
        
        sleepTimerRunnable = Runnable {
            if (isPlaying()) {
                pause(abandonFocus = true)
            }
            // Stop service and remove notification
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            sleepTimerEndTime = 0L
            sleepTimerRunnable = null
        }
        sleepTimerHandler.postDelayed(sleepTimerRunnable!!, delayMs)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        sleepTimerEndTime = 0L
    }
    
    private fun updateMediaSessionMetadata() {
        val track = getCurrentTrack() ?: return
        val duration = try {
            val dur = exoPlayer?.duration ?: 0L
            if (dur > 0 && dur != C.TIME_UNSET) dur else 0L
        } catch (_: Exception) { 0L }
        
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

