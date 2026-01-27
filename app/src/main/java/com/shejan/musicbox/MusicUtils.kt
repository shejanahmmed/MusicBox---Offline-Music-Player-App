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
    
    fun loadTrackArt(context: Context, trackId: Long, albumId: Long, trackUri: String, imageView: ImageView) {
        // Forward to ImageLoader for async handling
        ImageLoader.load(context, trackId, albumId, trackUri, imageView)
    }

    // Retaining this for internal use by ImageLoader or other background threads
    fun getTrackArtworkBitmap(context: Context, trackId: Long, albumId: Long, trackUri: String): android.graphics.Bitmap? {
         // Check for custom artwork
         if (TrackArtworkManager.hasCustomArtwork(context, trackUri)) {
             val customUri = TrackArtworkManager.getArtworkUri(context, trackUri)
             if (customUri == "REMOVED") {
                 return null
             } else if (customUri != null) {
                 try {
                     val uri = customUri.toUri()
                     
                     // 1. Decode Bounds First
                     val boundsOptions = android.graphics.BitmapFactory.Options()
                     boundsOptions.inJustDecodeBounds = true
                     context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                         android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, boundsOptions)
                     } ?: return null
                     
                     // Calculate inSampleSize
                     val reqWidth = 500
                     val reqHeight = 500
                     val options = android.graphics.BitmapFactory.Options()
                     options.inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
                     options.inJustDecodeBounds = false
                     
                     // 2. Decode Full
                     var bitmap: android.graphics.Bitmap? = null
                     context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                         bitmap = android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                     }
                     
                     if (bitmap == null) return null
                     
                     // 3. Read EXIF Orientation
                     var rotation = 0f
                     context.contentResolver.openInputStream(uri)?.use { inputStream ->
                         val exif = ExifInterface(inputStream)
                         val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                         when (orientation) {
                             ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90f
                             ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180f
                             ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270f
                         }
                     }
                     
                     // 4. Rotate if necessary
                     if (rotation != 0f) {
                         val matrix = android.graphics.Matrix()
                         matrix.postRotate(rotation)
                         return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                     }
                     
                     return bitmap

                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
         }
         
          // If we don't have IDs but have a path, try to find them in MediaStore first
          // This allows us to use efficient system thumbnails for library files
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

          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && resolvedTrackId > 0) {
              val uri = ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, resolvedTrackId)
               try {
                   val size = android.util.Size(300, 300)
                   return context.contentResolver.loadThumbnail(uri, size, null)
               } catch (_: Exception) {
                   // Fallback
               }
          }
          
          // Fallback to Album Art if available
          if (resolvedAlbumId != -1L && resolvedAlbumId > 0) {
              val bitmap = getAlbumArtBitmap(context, resolvedAlbumId)
              if (bitmap != null) return bitmap
          }

           // Fallback: Use MediaMetadataRetriever for raw file paths (Folder Browser)
           if (trackUri.isNotEmpty() && !trackUri.startsWith("content://")) {
               try {
                   val mmr = android.media.MediaMetadataRetriever()
                   try {
                       // Use setDataSource(Context, Uri) for better compatibility
                       // Note: We use Uri.fromFile as we are dealing with raw paths here
                       mmr.setDataSource(context, android.net.Uri.fromFile(java.io.File(trackUri)))
                       val rawArt = mmr.embeddedPicture
                       
                       if (rawArt != null) {
                           val options = android.graphics.BitmapFactory.Options()
                           options.inJustDecodeBounds = true
                           android.graphics.BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                           options.inSampleSize = calculateInSampleSize(options, 300, 300)
                           options.inJustDecodeBounds = false
                           
                           return android.graphics.BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                       }
                   } finally {
                       mmr.release()
                   }
               } catch (e: Exception) {
                   e.printStackTrace()
               }
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
                 context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                      val options = android.graphics.BitmapFactory.Options()
                      options.inJustDecodeBounds = true
                      android.graphics.BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)
                      
                      options.inSampleSize = calculateInSampleSize(options, 500, 500)
                      options.inJustDecodeBounds = false
                      
                      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd2 ->
                          return android.graphics.BitmapFactory.decodeFileDescriptor(pfd2.fileDescriptor, null, options)
                      }
                 }
            } catch (_: Exception) { }
        } catch (_: Exception) { }
        
        return null
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
