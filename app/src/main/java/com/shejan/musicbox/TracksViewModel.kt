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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TracksViewModel : ViewModel() {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadTracks(
        context: Context,
        showFavorites: Boolean,
        playlistId: Long,
        artistName: String?,
        albumName: String?,
        sortColumn: String,
        isAscending: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val appContext = context.applicationContext

            val list: List<Track> = if (showFavorites) {
                MusicRepository.getFavorites(appContext, sortColumn, isAscending)
            } else if (playlistId != -1L) {
                MusicRepository.getPlaylistTracks(appContext, playlistId)
            } else if (artistName != null) {
                MusicRepository.getTracks(
                    appContext,
                    "${MediaStore.Audio.Media.ARTIST} = ?",
                    arrayOf(artistName),
                    sortColumn,
                    isAscending
                )
            } else if (albumName != null) {
                MusicRepository.getTracks(
                    appContext,
                    "${MediaStore.Audio.Media.ALBUM} = ?",
                    arrayOf(albumName),
                    sortColumn,
                    isAscending
                )
            } else {
                MusicRepository.getTracks(appContext, null, null, sortColumn, isAscending)
            }

            _tracks.value = list
            _isLoading.value = false
        }
    }

    fun updateTracksDirectly(newTracks: List<Track>) {
        _tracks.value = newTracks
    }
}
