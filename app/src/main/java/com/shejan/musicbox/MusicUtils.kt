package com.shejan.musicbox

import android.content.ContentUris
import android.content.Context

import android.widget.ImageView
import androidx.core.net.toUri

object MusicUtils {
    
    fun loadAlbumArt(context: Context, albumId: Long, imageView: ImageView) {
        if (albumId <= 0) {
            setDefaultArt(imageView)
            return
        }
        
        @Suppress("SpellCheckingInspection")
        val sArtworkUri = "content://media/external/audio/albumart".toUri()
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 try {
                     val size = android.util.Size(300, 300)
                     val thumb = context.contentResolver.loadThumbnail(uri, size, null)
                     imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                     imageView.setImageBitmap(thumb)
                     imageView.clearColorFilter()
                     return
                 } catch (_: Exception) {
                     // Try legacy approach
                     try {
                         imageView.clearColorFilter()
                         imageView.setImageURI(uri)
                         if (imageView.drawable != null) {
                             imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                             return
                         }
                     } catch (_: Exception) {
                         // Fall through to default
                     }
                 }
            } else {
                 // Pre-Q: Use setImageURI
                 imageView.clearColorFilter()
                 imageView.setImageURI(uri)
                 if (imageView.drawable != null) {
                     imageView.scaleType = ImageView.ScaleType.CENTER_CROP
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

        // Direct Load (Old way, blocking)
        // We will keep this for now but it should ideally be used via ImageLoader
        val bitmap = getTrackArtworkBitmap(context, trackId, albumId)
        if (bitmap != null) {
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageBitmap(bitmap)
            imageView.clearColorFilter()
        } else {
            setDefaultArt(imageView)
        }
    }



    fun getTrackArtworkBitmap(context: Context, trackId: Long, albumId: Long): android.graphics.Bitmap? {
         // Check for custom artwork
         if (TrackArtworkManager.hasCustomArtwork(context, trackId)) {
             val customUri = TrackArtworkManager.getArtworkUri(context, trackId)
             if (customUri == "REMOVED") {
                 return null
             } else if (customUri != null) {
                 try {
                     val uri = customUri.toUri()
                     // Use ImageDecoder or BitmapFactory to get Bitmap from URI
                     // For simplicity and backward compat, using contentResolver
                     val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                     if (pfd != null) {
                         return android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
                     }
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
         }
         
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
             val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, trackId)
              try {
                  val size = android.util.Size(300, 300)
                  return context.contentResolver.loadThumbnail(uri, size, null)
              } catch (_: Exception) {
                  // Fallback
              }
          }
          
          // Fallback to Album Art if available
          if (albumId != -1L) {
              return getAlbumArtBitmap(context, albumId)
          }
          
          return null
    }

    private fun setDefaultArt(imageView: ImageView) {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.setImageResource(R.drawable.ic_cd_placeholder)
        // Remove color filter so the CD vector colors show
        imageView.clearColorFilter()
    }

    fun getAlbumArtBitmap(context: Context, albumId: Long): android.graphics.Bitmap? {
        if (albumId <= 0) return null
        
         @Suppress("SpellCheckingInspection")
        val sArtworkUri = "content://media/external/audio/albumart".toUri()
        val uri = ContentUris.withAppendedId(sArtworkUri, albumId)
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    val size = android.util.Size(300, 300)
                    return context.contentResolver.loadThumbnail(uri, size, null)
                } catch (_: Exception) { }
            }
            
            // Fallback / Pre-Q
            try {
                 val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                 if (pfd != null) {
                      val fd = pfd.fileDescriptor
                      return android.graphics.BitmapFactory.decodeFileDescriptor(fd)
                 }
            } catch (_: Exception) { }
        } catch (_: Exception) { }
        
        return null
    }
}
