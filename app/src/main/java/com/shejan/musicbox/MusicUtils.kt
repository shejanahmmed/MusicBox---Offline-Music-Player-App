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

import android.content.ContentUris
import android.content.Context
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface

object MusicUtils {
    @Volatile var contentVersion: Long = 0

    
    fun loadAlbumArt(context: Context, albumId: Long, imageView: ImageView) {
        if (albumId <= 0) {
            setDefaultArt(imageView)
            return
        }
        
        @Suppress("SpellCheckingInspection")
        val sArtworkUri = "content://media/external/audio/albumart".toUri()
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        
        try {
            com.bumptech.glide.Glide.with(imageView)
                .load(uri)
                .placeholder(R.drawable.ic_album)
                .error(R.drawable.ic_cd_placeholder)
                .fallback(R.drawable.ic_cd_placeholder)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(imageView)
        } catch (_: Exception) {
            setDefaultArt(imageView)
        }
    }
    
    fun loadTrackArt(context: Context, trackId: Long, albumId: Long, trackUri: String, imageView: ImageView) {
        ImageLoader.load(context, trackId, albumId, trackUri, imageView)
    }

    /**
     * Resolves the artwork model (Uri, Bitmap, or ByteArray) for Glide.
     */
    fun getTrackArtworkModel(context: Context, trackId: Long, albumId: Long, trackUri: String): Any? {
        // 1. Check for custom artwork
        if (TrackArtworkManager.hasCustomArtwork(context, trackUri)) {
            val customUri = TrackArtworkManager.getArtworkUri(context, trackUri)
            if (customUri == "REMOVED") {
                return null
            } else if (customUri != null) {
                return customUri.toUri()
            }
        }

        // 2. Resolve Track/Album IDs if not provided
        var resolvedTrackId = trackId
        var resolvedAlbumId = albumId

        if (resolvedTrackId <= 0L && trackUri.isNotEmpty() && !trackUri.startsWith("content://")) {
            try {
                val projection = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.ALBUM_ID
                )
                context.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${android.provider.MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(trackUri),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        resolvedTrackId = cursor.getLong(0)
                        resolvedAlbumId = cursor.getLong(1)
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Android Q+ Thumbnail via MediaStore
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && resolvedTrackId > 0) {
            val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, resolvedTrackId)
            try {
                val size = android.util.Size(500, 500)
                return context.contentResolver.loadThumbnail(uri, size, null)
            } catch (_: Exception) {}
        }

        // 4. Album Art Content URI
        if (resolvedAlbumId > 0) {
            @Suppress("SpellCheckingInspection")
            val sArtworkUri = "content://media/external/audio/albumart".toUri()
            val uri = ContentUris.withAppendedId(sArtworkUri, resolvedAlbumId)
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    return uri
                }
            } catch (_: Exception) {}
        }

        // 5. Embedded Picture in raw audio file
        if (trackUri.isNotEmpty() && !trackUri.startsWith("content://")) {
            try {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    val file = java.io.File(trackUri)
                    if (file.exists()) {
                        mmr.setDataSource(context, android.net.Uri.fromFile(file))
                        val rawArt = mmr.embeddedPicture
                        if (rawArt != null) {
                            return rawArt
                        }
                    }
                } finally {
                    mmr.release()
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun getTrackArtworkBitmap(context: Context, trackId: Long, albumId: Long, trackUri: String): android.graphics.Bitmap? {
        val model = getTrackArtworkModel(context, trackId, albumId, trackUri) ?: return null
        if (model is android.graphics.Bitmap) return model
        return try {
            com.bumptech.glide.Glide.with(context.applicationContext)
                .asBitmap()
                .load(model)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .submit(500, 500)
                .get()
        } catch (_: Exception) {
            null
        }
    }

    private fun setDefaultArt(imageView: ImageView) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageResource(R.drawable.ic_cd_placeholder)
        imageView.clearColorFilter()
    }

    fun getAlbumArtBitmap(context: Context, albumId: Long): android.graphics.Bitmap? {
        if (albumId <= 0) return null
        @Suppress("SpellCheckingInspection")
        val sArtworkUri = "content://media/external/audio/albumart".toUri()
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        return try {
            com.bumptech.glide.Glide.with(context.applicationContext)
                .asBitmap()
                .load(uri)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .submit(500, 500)
                .get()
        } catch (_: Exception) {
            null
        }
    }
    fun viewBubbleAnimation(view: android.view.View) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    fun animateFavoriteButton(view: android.view.View, isFavorite: Boolean, onStateChanged: () -> Unit) {
        view.animate().setListener(null).cancel()
        
        if (isFavorite) {
            view.animate()
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(120)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    onStateChanged()
                    view.animate()
                        .scaleX(1.35f)
                        .scaleY(1.35f)
                        .setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(3.0f))
                        .withEndAction {
                            view.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                        }
                        .start()
                }
                .start()
        } else {
            view.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(150)
                .withEndAction {
                    onStateChanged()
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .setInterpolator(android.view.animation.OvershootInterpolator())
                        .start()
                }
                .start()
        }
    }

    fun performHapticFeedback(context: Context) {
        try {
            val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("haptic_feedback_enabled", false)) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    vibrator.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10) // Fallback for older devices
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

