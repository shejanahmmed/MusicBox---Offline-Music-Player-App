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
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

object ImageLoader {

    private val executor = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())
    
    // Max 1/8th of memory for cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun load(context: Context, trackId: Long, albumId: Long, imageView: ImageView) {
        val cacheKey = "$trackId-$albumId"
        
        // Check Memory Cache
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            imageView.clearColorFilter()
            return
        }
        
        // Placeholder
        imageView.setImageResource(R.drawable.ic_album)
        imageView.setColorFilter(android.graphics.Color.DKGRAY)
        
        // Tag for recycling
        imageView.tag = cacheKey
        
        // Use weak reference to prevent memory leak if imageView is GC'd
        val weakView = java.lang.ref.WeakReference(imageView)
        
        // Background Load
        executor.execute {
            try {
                val bitmap = MusicUtils.getTrackArtworkBitmap(context, trackId, albumId)
                    ?: return@execute
                
                memoryCache.put(cacheKey, bitmap)
                
                // Get view from weak reference
                weakView.get()?.let { view ->
                    // Double-check: ensure view hasn't been reused
                    if (view.tag == cacheKey) {
                        handler.post {
                            // Final check before setting image
                            if (view.tag == cacheKey) {
                                view.setImageBitmap(bitmap)
                                view.clearColorFilter()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

