package com.shejan.musicbox

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseMusicWidgetProvider(private val isLight: Boolean) : AppWidgetProvider() {

    companion object {
        private val widgetScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            // Update Light widgets
            val lightProvider = ComponentName(context, MusicWidgetProviderLight::class.java)
            val lightIds = appWidgetManager.getAppWidgetIds(lightProvider)
            if (lightIds.isNotEmpty()) {
                val intent = Intent(context, MusicWidgetProviderLight::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, lightIds)
                }
                context.sendBroadcast(intent)
            }

            // Update Dark widgets
            val darkProvider = ComponentName(context, MusicWidgetProviderDark::class.java)
            val darkIds = appWidgetManager.getAppWidgetIds(darkProvider)
            if (darkIds.isNotEmpty()) {
                val intent = Intent(context, MusicWidgetProviderDark::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, darkIds)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action

        // Handle widget action broadcasts to run service as foreground service safely
        if (action == MusicService.ACTION_PLAY || action == MusicService.ACTION_PAUSE ||
            action == MusicService.ACTION_PREV || action == MusicService.ACTION_NEXT) {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicWidget", "Failed to start MusicService from widget action broadcast", e)
            }
            return
        }

        if (action == "MUSIC_BOX_UPDATE" || action == "com.shejan.musicbox.TRACK_DELETED" || action == Intent.ACTION_BOOT_COMPLETED) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providerClass = if (isLight) MusicWidgetProviderLight::class.java else MusicWidgetProviderDark::class.java
            val provider = ComponentName(context, providerClass)
            val ids = appWidgetManager.getAppWidgetIds(provider)
            if (ids.isNotEmpty()) {
                val pendingResult = goAsync()
                widgetScope.launch {
                    try {
                        for (appWidgetId in ids) {
                            updateWidget(context, appWidgetManager, appWidgetId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            doUpdateWidget(context, appWidgetManager, appWidgetId)
        } catch (e: Exception) {
            android.util.Log.e("MusicWidget", "Fatal error updating widget $appWidgetId", e)
        }
    }

    private suspend fun doUpdateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val layoutId = if (isLight) R.layout.widget_music_light else R.layout.widget_music_dark
        val views = RemoteViews(context.packageName, layoutId)

        // 1. Fetch current playback state and track metadata
        val service = MusicService.instance
        val isPlaying = service?.isPlaying() ?: false
        val track = getSavedTrack(context)

        // 2. Update metadata text views
        if (track != null) {
            views.setTextViewText(R.id.widget_track_title, track.title)
            views.setTextViewText(R.id.widget_track_artist, track.artist)

            // Progress Bar
            val duration = if (service != null) service.getDuration() else {
                val prefs = context.getSharedPreferences("MusicBoxPlaybackPrefs", Context.MODE_PRIVATE)
                prefs.getInt("current_duration", 0)
            }
            val position = if (service != null) service.getCurrentPosition() else {
                val prefs = context.getSharedPreferences("MusicBoxPlaybackPrefs", Context.MODE_PRIVATE)
                prefs.getInt("current_position", 0)
            }

            if (duration > 0) {
                views.setProgressBar(R.id.widget_progress, duration, position, false)
            } else {
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
            }
        } else {
            views.setTextViewText(R.id.widget_track_title, "No track loaded")
            views.setTextViewText(R.id.widget_track_artist, "Select a song to play")
            views.setProgressBar(R.id.widget_progress, 100, 0, false)
        }

        // 3. Update play/pause button icon and state
        val playIconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        views.setImageViewResource(R.id.widget_btn_play, playIconRes)

        // Equalizer level meter bars (dynamic heights)
        if (isPlaying) {
            val h1 = (4..12).random().toFloat()
            val h2 = (10..18).random().toFloat()
            val h3 = (6..14).random().toFloat()
            val h4 = (8..16).random().toFloat()
            views.setViewLayoutHeight(R.id.widget_bar_1, h1, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_2, h2, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_3, h3, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_4, h4, android.util.TypedValue.COMPLEX_UNIT_DIP)
        } else {
            views.setViewLayoutHeight(R.id.widget_bar_1, 3f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_2, 3f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_3, 3f, android.util.TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(R.id.widget_bar_4, 3f, android.util.TypedValue.COMPLEX_UNIT_DIP)
        }

        val playIntent = Intent(context, this::class.java).apply {
            action = if (isPlaying) MusicService.ACTION_PAUSE else MusicService.ACTION_PLAY
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            context,
            if (isLight) 100 else 200,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_play, playPendingIntent)

        val prevIntent = Intent(context, this::class.java).apply {
            action = MusicService.ACTION_PREV
        }
        val prevPendingIntent = PendingIntent.getBroadcast(
            context,
            if (isLight) 101 else 201,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

        val nextIntent = Intent(context, this::class.java).apply {
            action = MusicService.ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getBroadcast(
            context,
            if (isLight) 102 else 202,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

        // 5. Setup PendingIntent to launch the app (clicking on the widget container)
        val clickIntent = if (track != null) {
            Intent(context, NowPlayingActivity::class.java).apply {
                putExtra("extra_title", track.title)
                putExtra("extra_artist", track.artist)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context,
            if (isLight) 103 else 203,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, clickPendingIntent)

        // 6. Load and round the album art bitmap in the background
        val roundedArt = withContext(Dispatchers.IO) {
            try {
                val rawBitmap = if (track != null) {
                    MusicUtils.getTrackArtworkBitmap(context, track.id, track.albumId, track.uri)
                } else null

                val bitmapToRound = rawBitmap ?: getBitmapFromVectorDrawable(context, R.drawable.ic_widget_placeholder)

                bitmapToRound?.let {
                    val scaled = scaleBitmapToMax(it, 200)
                    val density = context.resources.displayMetrics.density
                    val radiusPx = 4f * density
                    getRoundedCornerBitmap(scaled, radiusPx)
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicWidget", "Error loading artwork", e)
                null
            }
        }

        if (roundedArt != null) {
            views.setImageViewBitmap(R.id.widget_album_art, roundedArt)
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_widget_placeholder)
        }

        // 7. Update widget instance (AppWidgetManager.updateAppWidget is thread-safe)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun getSavedTrack(context: Context): Track? {
        val service = MusicService.instance
        if (service != null) {
            val track = service.getCurrentTrack()
            if (track != null) return track
        }

        val staticIndex = MusicService.currentIndex
        val staticPlaylist = MusicService.playlist
        if (staticIndex >= 0 && staticIndex < staticPlaylist.size) {
            return staticPlaylist[staticIndex]
        }

        val prefs = context.getSharedPreferences("MusicBoxPlaybackPrefs", Context.MODE_PRIVATE)
        val idx = prefs.getInt("current_index", -1)
        val file = java.io.File(context.filesDir, "queue_layout.json")
        if (idx != -1 && file.exists()) {
            try {
                val content = file.readText()
                val root = org.json.JSONObject(content)
                val playlistArray = root.optJSONArray("playlist")
                if (playlistArray != null && idx < playlistArray.length()) {
                    val obj = playlistArray.getJSONObject(idx)
                    return Track(
                        obj.getLong("id"),
                        obj.getString("title"),
                        obj.getString("artist"),
                        obj.getString("uri"),
                        obj.optString("album").ifEmpty { null },
                        obj.optLong("albumId", -1L)
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicWidget", "Error reading saved track", e)
            }
        }
        return null
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, radiusPx: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = 0xff424242.toInt()
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
            val size = 128
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleBitmapToMax(bitmap: Bitmap, maxSize: Int): Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
