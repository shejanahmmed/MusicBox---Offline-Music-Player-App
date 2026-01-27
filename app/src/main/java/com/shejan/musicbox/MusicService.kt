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
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.util.Collections

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat

    private var mediaPlayer: MediaPlayer? = null
    private var placeholderBitmap: Bitmap? = null
    
    // Sleep Timer
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null
    var sleepTimerEndTime: Long = 0L

    // Queue Management
    companion object {
        const val CHANNEL_ID = "MusicBoxChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        
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
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            isShuffleEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
            repeatMode = prefs.getInt(KEY_REPEAT, REPEAT_OFF)
        }
        
        private fun saveShuffle(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_SHUFFLE, enabled)
            }
        }
        
        private fun saveRepeat(context: Context, mode: Int) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
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
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (isPlaying()) {
                    pause()
                }
            }
        }
    }

    private val standardPreparedListener = MediaPlayer.OnPreparedListener { mp ->
        mp.start()
        updateNotification()
        updateMediaSessionMetadata()
        updateMediaSessionState()
        saveState() // Save state when track starts
        
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
        initPrefs(this) // Load persistent settings
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
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // Register Deletion Receiver
        val deleteFilter = IntentFilter("com.shejan.musicbox.TRACK_DELETED")
        ContextCompat.registerReceiver(this, deletionReceiver, deleteFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Initialize MediaPlayer
        initMediaPlayer()
        
        // Restore State (Queue and Position)
        restoreState()
    }

    private fun initMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnCompletionListener {
                saveState() // Save state on completion (track change)
                if (repeatMode == REPEAT_ONE) {
                    playTrack(currentIndex)
                } else {
                    playNext(autoPlay = true)
                }
            }
            setOnPreparedListener(standardPreparedListener)
            setOnErrorListener { mp, _, _ ->
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
        stopForeground(STOP_FOREGROUND_REMOVE)
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
                mediaPlayer?.prepareAsync() // This triggers onPrepared, which sends the ONE update
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback for race conditions ensuring player is reset
                if (e is IllegalStateException) {
                    initMediaPlayer()
                    try {
                        mediaPlayer?.setDataSource(applicationContext, track.uri.toUri())
                        mediaPlayer?.prepareAsync()
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
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
        } catch (_: IllegalStateException) {
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
                    saveState() // Save specific position on pause
                    sendBroadcast(Intent("MUSIC_BOX_UPDATE").setPackage(packageName).apply { putExtra("IS_PLAYING", false) })
                }
            }
        } catch (_: IllegalStateException) {
            initMediaPlayer()
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
                    // Stop or go to start paused?
                    // Typically 'Next' at end wraps to start or stops. 
                    // If autoPlay (natural end), we stop.
                    // If forced by user (Next Button), we wrap? Let's wrap to 0.
                    if (!autoPlay) {
                         playTrack(0)
                    } else {
                         // Stop playback
                         pause()
                         mediaPlayer?.seekTo(0)
                         currentIndex = 0 // Reset to 0 but don't play
                         // We might want to just stop
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

    // Scope for UI/Notification updates
    private val uiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

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
        uiScope.launch {
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
        uiScope.cancel() // Cancel all pending UI updates
        cancelSleepTimer() // Clean up runnables
        
        saveState()
        saveStateExecutor.shutdown() // Prevent thread leaks
        
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
        val currentUriCopy = currentTrackUri

        // Run Serialization in Background Thread Sequentially
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
            
            // Apply to Prefs
            editor.putInt("current_index", currentIndexCopy)
            editor.putInt("current_position", currentPosCopy)
            editor.putString("current_track_uri", currentUriCopy)
            if (currentPlaylistCopy.isNotEmpty()) editor.putString("saved_playlist", jsonArray.toString())
            if (isShuffleEnabled && originalPlaylistCopy.isNotEmpty()) editor.putString("saved_original_playlist", jsonArrayComp.toString())
            
            editor.apply()
        }
    }
    
    private fun restoreState() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val idx = prefs.getInt("current_index", -1)
        val pos = prefs.getInt("current_position", 0)
        val savedPlaylist = prefs.getString("saved_playlist", null)
        val savedOriginal = prefs.getString("saved_original_playlist", null)
        
        if (savedPlaylist != null) {
            try {
                val list = mutableListOf<Track>()
                val jsonArray = org.json.JSONArray(savedPlaylist)
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
        
        if (savedOriginal != null) {
             try {
                val list = mutableListOf<Track>()
                val jsonArray = org.json.JSONArray(savedOriginal)
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
        
        // Restore Index and Prepare Player
        if (idx != -1 && idx < playlist.size) {
            val track = playlist[idx]
            currentIndex = idx
            currentTrackUri = track.uri
            
            // Init player but DO NOT START
            try {
                mediaPlayer?.reset()
                currentTrackUri?.let { uri ->
                    mediaPlayer?.setDataSource(applicationContext, uri.toUri())
                    mediaPlayer?.setOnPreparedListener { 
                        it.seekTo(pos)
                        updateNotification()
                        updateMediaSessionMetadata()
                        updateMediaSessionState()
                        // Re-set listener for normal playback
                        it.setOnPreparedListener(standardPreparedListener)
                    }
                    mediaPlayer?.prepareAsync()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
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

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        
        val delayMs = minutes * 60 * 1000L
        sleepTimerEndTime = System.currentTimeMillis() + delayMs
        
        sleepTimerRunnable = Runnable {
            if (isPlaying()) {
                pause()
                // Optional: Stop service or close app? Pause is safer.
            }
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
