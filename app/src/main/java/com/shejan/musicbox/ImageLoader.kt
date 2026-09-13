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

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.collection.LruCache
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ImageLoader {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Max 1/8th of application memory for instantaneous bitmap cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun load(context: Context, trackId: Long, albumId: Long, trackUri: String, imageView: ImageView) {
        val cacheKey = "$trackId-$albumId-$trackUri"

        // 1. Instant Synchronous Cache Check (0ms latency, zero flicker)
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.tag = cacheKey
            imageView.setImageBitmap(cachedBitmap)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.clearColorFilter()
            return
        }

        // 2. Set tag and placeholder for background fetch
        imageView.tag = cacheKey
        imageView.setImageResource(R.drawable.ic_cd_placeholder)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.clearColorFilter()

        // 3. Background Decode & Cache Populate
        val appContext = context.applicationContext
        ioScope.launch {
            val bitmap = MusicUtils.getTrackArtworkBitmap(appContext, trackId, albumId, trackUri)
            withContext(Dispatchers.Main) {
                if (imageView.tag == cacheKey) {
                    if (bitmap != null) {
                        memoryCache.put(cacheKey, bitmap)
                        imageView.setImageBitmap(bitmap)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.clearColorFilter()
                    } else {
                        imageView.setImageResource(R.drawable.ic_cd_placeholder)
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.clearColorFilter()
                    }
                }
            }
        }
    }

    fun clearCacheForTrack(trackUri: String) {
        try {
            val snapshot = memoryCache.snapshot()
            for ((key, _) in snapshot) {
                if (key.endsWith("-$trackUri")) {
                    memoryCache.remove(key)
                }
            }
            MusicBoxApplication.instance?.let { app ->
                Glide.get(app).clearMemory()
                ioScope.launch {
                    Glide.get(app).clearDiskCache()
                }
            }
        } catch (_: Exception) {}
    }
}
