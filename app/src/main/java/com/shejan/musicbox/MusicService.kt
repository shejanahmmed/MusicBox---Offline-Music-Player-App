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
import androidx.media.app.NotificationCompat.MediaStyle

class MusicService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: android.media.MediaPlayer? = null
    var isServiceBound = false

    // Queue Management
    companion object {
        const val CHANNEL_ID = "MusicBoxChannel"
        const val NOTIFICATION_ID = 101
        
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
        
        // Static playlist for simplicity in this demo
        var playlist: List<Track> = emptyList()
        var currentIndex: Int = -1
        var isShuffleEnabled = false
        
        // Repeat Modes
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
        var repeatMode = REPEAT_OFF
        
        var currentTrackUri: String? = null
    }

    private val binder = MusicBinder()

    inner class MusicBinder : android.os.Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "MusicBoxMediaSession")

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
                mediaPlayer?.seekTo(pos.toInt())
                updateMediaSessionState()
            }
        })
        mediaSession.isActive = true

        // Initialize MediaPlayer
        mediaPlayer = android.media.MediaPlayer().apply {
            setOnCompletionListener {
                if (repeatMode == REPEAT_ONE) {
                    playTrack(currentIndex)
                } else if (repeatMode == REPEAT_ALL) {
                    playNext()
                } else {
                    // Repeat Off: Stop at end of list
                    if (currentIndex < playlist.size - 1) {
                         playNext()
                    } else {
                         playNext() // For now, let's just default to next to avoid complexity, but usually Repeat Off means stop.
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action != null) {
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_NEXT -> playNext()
                ACTION_PREV -> playPrev()
            }
        } else {
             // Initial Start from List
             val title = intent?.getStringExtra("TITLE")
             val uri = intent?.getStringExtra("URI")
             
             // If launched with a specific song (from Adapter), assume the playlist is already set correctly by the Adapter/Activity
             // and the current index is handled there, OR find the track in our static list.
             if (uri != null && title != null) {
                 if (uri == currentTrackUri) {
                      // Already the current track. Update index just in case playlist context changed (e.g. All -> Fav)
                      val index = playlist.indexOfFirst { it.uri == uri }
                      if (index != -1) currentIndex = index
                      
                      if (!isPlaying()) play()
                 } else {
                      // Find index in playlist
                      val index = playlist.indexOfFirst { it.uri == uri }
                      if (index != -1) {
                          currentIndex = index
                          playTrack(index)
                      }
                 }
             }
        }

        // Always update notification
        updateNotification()

        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    fun playTrack(index: Int) {
        if (index < 0 || index >= playlist.size) return
        
        val track = playlist[index]
        currentIndex = index
        currentTrackUri = track.uri
        
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(applicationContext, android.net.Uri.parse(track.uri))
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            
            updateNotification()
            
            // Broadcast Change
            sendBroadcast(Intent("MUSIC_BOX_UPDATE").apply {
                putExtra("IS_PLAYING", true)
                putExtra("TITLE", track.title)
                putExtra("ARTIST", track.artist)
            })

            updateMediaSessionMetadata()
            updateMediaSessionState()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                updateNotification()
                updateMediaSessionState() // State changed to Playing
                sendBroadcast(Intent("MUSIC_BOX_UPDATE").apply { putExtra("IS_PLAYING", true) })
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                updateNotification()
                updateMediaSessionState() // State changed to Paused
                sendBroadcast(Intent("MUSIC_BOX_UPDATE").apply { putExtra("IS_PLAYING", false) })
            }
        }
    }
    
    fun playNext() {
        if (playlist.isEmpty()) return
        
        var nextIndex: Int
        if (isShuffleEnabled) {
             nextIndex = (playlist.indices).random()
        } else {
             nextIndex = currentIndex + 1
             if (nextIndex >= playlist.size) nextIndex = 0 // Loop
        }
        playTrack(nextIndex)
    }
    
    fun playPrev() {
        if (playlist.isEmpty()) return
        
         var prevIndex: Int
        if (isShuffleEnabled) {
             prevIndex = (playlist.indices).random()
        } else {
             prevIndex = currentIndex - 1
             if (prevIndex < 0) prevIndex = playlist.size - 1 // Loop
        }
        playTrack(prevIndex)
    }
    
    fun toggleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").apply { putExtra("REPEAT_MODE", repeatMode) })
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        sendBroadcast(Intent("MUSIC_BOX_UPDATE").apply { putExtra("SHUFFLE_STATE", isShuffleEnabled) })
    }
    
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    fun getCurrentTrack(): Track? {
        if (currentIndex in playlist.indices) {
            return playlist[currentIndex]
        }
        return null
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    private fun updateNotification() {
        val track = getCurrentTrack() ?: return
        val isPlaying = isPlaying()
        
        startForeground(NOTIFICATION_ID, buildNotification(track.title, track.artist, isPlaying))
    }

    private fun buildNotification(title: String, artist: String, isPlaying: Boolean): android.app.Notification {
        
        // Intents for actions
        val playIntent = PendingIntent.getService(this, 0, Intent(this, MusicService::class.java).setAction(ACTION_PLAY), PendingIntent.FLAG_IMMUTABLE)
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, MusicService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 2, Intent(this, MusicService::class.java).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)
        val prevIntent = PendingIntent.getService(this, 3, Intent(this, MusicService::class.java).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE)

        // Open Activity Intent
        val contentIntent = Intent(this, NowPlayingActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP 
        }
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_audiotrack) 
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_album)) 
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
        mediaPlayer?.release()
        mediaSession.release()
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val playbackSpeed = if (isPlaying()) 1.0f else 0.0f
        
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or 
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(state, position, 1.0f) // always 1.0f speed when playing, but for showing paused we can set speed 0 logic if preferred, but setState handles state enum. 
                                             // Actually Android recommends passing 1.0f for speed if playing, 0 if paused? No, usually just state matters.
        
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaSessionMetadata() {
        val track = getCurrentTrack() ?: return
        val duration = mediaPlayer?.duration?.toLong() ?: 0L
        
        // Try to load art bitmap
        var artBitmap: android.graphics.Bitmap? = null
        try {
             artBitmap = MusicUtils.getAlbumArtBitmap(this, track.albumId)
        } catch (e: Exception) { e.printStackTrace() }
        
        if (artBitmap == null) {
            artBitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.ic_album)
        }

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album ?: "Unknown Album")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artBitmap)

        mediaSession.setMetadata(metadataBuilder.build())
    }
}
