package com.shejan.musicbox

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.widget.ImageView
import java.io.FileNotFoundException

object MusicUtils {
    
    fun loadAlbumArt(context: Context, albumId: Long, imageView: ImageView) {
        if (albumId <= 0) {
            setDefaultArt(imageView)
            return
        }
        
        val sArtworkUri = Uri.parse("content://media/external/audio/albumart")
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 try {
                     val size = android.util.Size(300, 300)
                     val thumb = context.contentResolver.loadThumbnail(uri, size, null)
                     imageView.setImageBitmap(thumb)
                     imageView.clearColorFilter()
                     return
                 } catch (e: Exception) {
                     // Try legacy approach
                     try {
                         imageView.clearColorFilter()
                         imageView.setImageURI(uri)
                         if (imageView.drawable != null) {
                             return
                         }
                     } catch (e2: Exception) {
                         // Fall through to default
                     }
                 }
            } else {
                 // Pre-Q: Use setImageURI
                 imageView.clearColorFilter()
                 imageView.setImageURI(uri)
                 if (imageView.drawable != null) {
                     return
                 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // If all else fails, show default
        setDefaultArt(imageView)
    }
    
    fun loadTrackArt(context: Context, trackId: Long, albumId: Long, imageView: ImageView) {
         if (trackId <= 0L) {
             setDefaultArt(imageView)
             return
         }
         
         // Check for custom artwork
         if (TrackArtworkManager.hasCustomArtwork(context, trackId)) {
             val customUri = TrackArtworkManager.getArtworkUri(context, trackId)
             if (customUri == "REMOVED") {
                 setDefaultArt(imageView)
                 return
             } else if (customUri != null) {
                 try {
                     val uri = Uri.parse(customUri)
                     imageView.setImageURI(uri)
                     imageView.clearColorFilter()
                     if (imageView.drawable != null) return
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
         }
         
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
              try {
                  val size = android.util.Size(300, 300)
                  val thumb = context.contentResolver.loadThumbnail(uri, size, null)
                  imageView.setImageBitmap(thumb)
                  imageView.clearColorFilter()
                  return
              } catch (e: Exception) {
                  // e.printStackTrace()
              }
          }
          
          // Fallback to Album Art if available
          if (albumId != -1L) {
              loadAlbumArt(context, albumId, imageView)
              return
          }
          
          // Fallback to default
          setDefaultArt(imageView)
    }

    // Overload for backward compatibility
    fun loadTrackArt(context: Context, trackId: Long, imageView: ImageView) {
        loadTrackArt(context, trackId, -1L, imageView)
    }

    private fun setDefaultArt(imageView: ImageView) {
        imageView.setImageResource(R.drawable.ic_album)
        imageView.setColorFilter(android.graphics.Color.DKGRAY)
    }
}
