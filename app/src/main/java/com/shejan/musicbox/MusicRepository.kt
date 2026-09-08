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
import android.provider.MediaStore

object MusicRepository {

    data class HomeCounts(
        val favorites: Int = 0,
        val playlists: Int = 0,
        val albums: Int = 0,
        val artists: Int = 0,
        val tracks: Int = 0
    )

    fun getHomeCounts(context: Context): HomeCounts {
        val favCount = getFavoriteCount(context)
        val playlistCount = AppPlaylistManager.getAllPlaylists(context).size
        val albumCount = getAlbumCount(context)
        val artistCount = getArtistCount(context)
        val trackCount = getTrackCount(context)
        return HomeCounts(favCount, playlistCount, albumCount, artistCount, trackCount)
    }

    fun getFavoriteCount(context: Context): Int {
        val favorites = FavoritesManager.getFavorites(context)
        if (favorites.isEmpty()) return 0

        var count = 0
        try {
            val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
            val minDurationMillis = minDurationSec * 1000

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                selection,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null &&
                        favorites.contains(path) &&
                        !HiddenTracksManager.isHidden(context, path) &&
                        !path.lowercase().contains("ringtone") &&
                        !path.lowercase().contains("notification")
                    ) {
                        count++
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun getAlbumCount(context: Context): Int {
        var count = 0
        try {
            context.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) {}
        return count
    }

    fun getArtistCount(context: Context): Int {
        var count = 0
        try {
            context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Artists._ID),
                null, null, null
            )?.use { count = it.count }
        } catch (_: Exception) {}
        return count
    }

    fun getTrackCount(context: Context): Int {
        var count = 0
        val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
        val minDurationMs = minDurationSec * 1000

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null, null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getInt(durationColumn)

                    if (path != null && !HiddenTracksManager.isHidden(context, path) && duration >= minDurationMs) {
                        count++
                    }
                }
            }
        } catch (_: Exception) {}
        return count
    }

    fun getTracks(
        context: Context,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortColumn: String = MediaStore.Audio.Media.TITLE,
        isAscending: Boolean = true
    ): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
            val minDurationSec = prefs.getInt("min_track_duration_sec", 10)
            val minDurationMillis = minDurationSec * 1000

            val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $minDurationMillis"
            val finalSelection = if (selection != null) "($baseSelection) AND ($selection)" else baseSelection

            val order = if (isAscending) "ASC" else "DESC"
            val sortOrder = if (sortColumn == "custom_preference") {
                "${MediaStore.Audio.Media.TITLE} ASC"
            } else {
                "$sortColumn $order"
            }

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                finalSelection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown Artist"
                    val path = it.getString(dataColumn) ?: continue
                    val album = it.getString(albumColumn)
                    val albumId = it.getLong(albumIdColumn)

                    if (!HiddenTracksManager.isHidden(context, path) &&
                        !path.lowercase().contains("ringtone") &&
                        !path.lowercase().contains("notification")
                    ) {
                        list.add(TrackMetadataManager.applyMetadata(context, Track(id, title, artist, path, album, albumId)))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (sortColumn == "custom_preference") {
            val customOrder = CustomSortHelper.getCustomOrder(context)
            var sortedList = CustomSortHelper.sortTracksCustom(list, customOrder)
            if (!isAscending) {
                sortedList = sortedList.reversed()
            }
            return sortedList
        }
        return list
    }

    fun getFavorites(
        context: Context,
        sortColumn: String = MediaStore.Audio.Media.TITLE,
        isAscending: Boolean = true
    ): List<Track> {
        val favorites = FavoritesManager.getFavorites(context)
        if (favorites.isEmpty()) return emptyList()
        return getTracks(context, null, null, sortColumn, isAscending).filter { favorites.contains(it.uri) }
    }

    fun getPlaylistTracks(context: Context, playlistId: Long): List<Track> {
        val playlist = AppPlaylistManager.getPlaylist(context, playlistId) ?: return emptyList()
        val allTracks = getTracks(context, null, null)
        val trackMap = allTracks.associateBy { it.uri }
        return playlist.trackPaths.mapNotNull { trackMap[it] }
    }

    fun getAlbums(context: Context): List<Album> {
        val albumMap = mutableMapOf<Long, Album>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA
            )

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.ALBUM} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val pathCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val path = it.getString(pathCol) ?: continue
                    if (HiddenTracksManager.isHidden(context, path)) continue

                    val albumId = it.getLong(idCol)
                    if (!albumMap.containsKey(albumId)) {
                        val title = it.getString(albumCol) ?: "Unknown Album"
                        val artist = it.getString(artistCol) ?: "Unknown Artist"
                        albumMap[albumId] = Album(albumId, title, artist, path)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return albumMap.values.toList().sortedBy { it.title }
    }

    fun getArtists(context: Context): List<Artist> {
        val list = mutableListOf<Artist>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Artists._ID,
                MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS
            )

            val cursor = context.contentResolver.query(
                MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Audio.Artists.ARTIST + " ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
                val countCol = it.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Unknown Artist"
                    val count = it.getInt(countCol)
                    list.add(Artist(id, name, count))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getVideos(
        context: Context,
        sortColumn: String = MediaStore.Video.Media.TITLE,
        isAscending: Boolean = true
    ): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE
            )

            val order = if (isAscending) "ASC" else "DESC"
            val sortOrder = "$sortColumn $order"

            val videoPrefs = context.getSharedPreferences("MusicBoxVideoPrefs", Context.MODE_PRIVATE)
            val minSec = videoPrefs.getInt("video_min_duration_sec", 0)
            val maxSec = videoPrefs.getInt("video_max_duration_sec", 0)
            val minMs = minSec * 1000L
            val maxMs = if (maxSec > 0) maxSec * 1000L else Long.MAX_VALUE

            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Unknown Video"
                    val duration = it.getLong(durationCol)
                    val path = it.getString(dataCol) ?: continue
                    val size = it.getLong(sizeCol)

                    if (duration < minMs || duration > maxMs) continue

                    list.add(VideoItem(id, title, duration, path, size))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getHiddenTracks(context: Context): List<Track> {
        val list = mutableListOf<Track>()
        val hiddenUris = HiddenTracksManager.getHiddenTracks(context)
        if (hiddenUris.isEmpty()) return list

        // 1. Audio
        try {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.ALBUM_ID
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null && hiddenUris.contains(path)) {
                        list.add(
                            Track(
                                id = cursor.getLong(idColumn),
                                title = cursor.getString(titleColumn) ?: "Unknown",
                                artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                                album = cursor.getString(albumColumn) ?: "Unknown Album",
                                uri = path,
                                albumId = cursor.getLong(albumIdColumn)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Video
        try {
            @Suppress("DEPRECATION")
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DATA
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    if (hiddenUris.contains(path)) {
                        list.add(
                            Track(
                                id = cursor.getLong(idCol),
                                title = cursor.getString(titleCol) ?: "Unknown Video",
                                artist = "Video",
                                album = null,
                                uri = path,
                                albumId = -1L
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
