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
        
        // Background Load
        executor.execute {
            val bitmap = MusicUtils.getTrackArtworkBitmap(context, trackId, albumId)
            
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                
                // Post to Main Thread
                handler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(bitmap)
                        imageView.clearColorFilter()
                    }
                }
            }
        }
    }
}
