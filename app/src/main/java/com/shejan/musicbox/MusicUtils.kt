package com.shejan.musicbox

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import java.io.FileNotFoundException

object MusicUtils {
    
    fun loadAlbumArt(context: Context, albumId: Long, imageView: ImageView) {
        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        
        // Simple scaling/loading logic since we don't have Glide/Picasso here.
        // For production, use Glide.load(uri).
        // Here we'll try to set URI directly. ImageView handles content:// URIs if simple.
        // But for Album Art, it might need stream opening.
        
        try {
            // This is the quickest way for valid URIs
            // imageView.setImageURI(uri) 
            // BUT setImageURI is synchronous mostly or checks cache.
            // Better to try/catch.
            
            // Check if file exists roughly or just let ImageView fail? 
            // ImageView doesn't handle "content://media/external/audio/albumart" reliably on all androids directly via setImageURI without a generic loader.
            // On newer Android (Q+), ALBUM_ART column is deprecated, we should use loadThumbnail.
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 try {
                     val size = android.util.Size(300, 300)
                     val thumb = context.contentResolver.loadThumbnail(uri, size, null)
                     imageView.setImageBitmap(thumb)
                     imageView.clearColorFilter() // Remove tint if set
                 } catch (e: Exception) {
                     // Fallback
                     setDefaultArt(imageView)
                 }
            } else {
                 // Reset color filter before setting
                 imageView.clearColorFilter()
                 imageView.setImageURI(uri)
                 if (imageView.drawable == null) {
                     setDefaultArt(imageView)
                 }
            }
            
        } catch (e: Exception) {
            setDefaultArt(imageView)
        }
    }
    
    // For when we only have track ID (API 29+ prefers loadThumbnail on specific URI)
    fun loadTrackArt(context: Context, trackId: Long, imageView: ImageView) {
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
              try {
                 val size = android.util.Size(300, 300)
                 val thumb = context.contentResolver.loadThumbnail(uri, size, null)
                 imageView.setImageBitmap(thumb)
                 imageView.clearColorFilter()
             } catch (e: Exception) {
                 setDefaultArt(imageView)
             }
         } else {
             // Fallback to finding Album ID yourself? 
             // Or hope the caller passed Album ID.
             setDefaultArt(imageView)
         }
    }

    private fun setDefaultArt(imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_album)
        imageView.setColorFilter(android.graphics.Color.DKGRAY)
    }
}
