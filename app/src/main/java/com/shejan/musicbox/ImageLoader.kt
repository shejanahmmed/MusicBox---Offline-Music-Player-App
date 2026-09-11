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
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ImageLoader {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun load(context: Context, trackId: Long, albumId: Long, trackUri: String, imageView: ImageView) {
        val cacheKey = "$trackId-$albumId-$trackUri"
        imageView.tag = cacheKey
        imageView.clearColorFilter()

        val requestOptions = RequestOptions()
            .placeholder(R.drawable.ic_album)
            .error(R.drawable.ic_cd_placeholder)
            .fallback(R.drawable.ic_cd_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        ioScope.launch {
            val model = withContext(Dispatchers.IO) {
                MusicUtils.getTrackArtworkModel(context.applicationContext, trackId, albumId, trackUri)
            }
            withContext(Dispatchers.Main) {
                if (imageView.tag == cacheKey) {
                    try {
                        Glide.with(imageView)
                            .load(model ?: R.drawable.ic_cd_placeholder)
                            .apply(requestOptions)
                            .into(imageView)
                    } catch (_: Exception) {
                        imageView.setImageResource(R.drawable.ic_cd_placeholder)
                    }
                }
            }
        }
    }

    fun clearCacheForTrack(trackUri: String) {
        try {
            MusicBoxApplication.instance?.let { app ->
                Glide.get(app).clearMemory()
                ioScope.launch {
                    Glide.get(app).clearDiskCache()
                }
            }
        } catch (_: Exception) {}
    }
}
