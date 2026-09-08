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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeBoxItemData(
    val id: String,
    val name: String,
    val iconRes: Int,
    val count: Int,
    val countLabel: String
)

class MainViewModel : ViewModel() {

    private val _homeBoxes = MutableStateFlow<List<HomeBoxItemData>>(emptyList())
    val homeBoxes: StateFlow<List<HomeBoxItemData>> = _homeBoxes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadHomeBoxes(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val appContext = context.applicationContext

            val savedOrder = HomeBoxPreferences.getBoxOrder(appContext)
            val allBoxes = HomeBoxPreferences.getAllBoxes()

            val visibleBoxes = savedOrder.mapNotNull { boxId ->
                if (HomeBoxPreferences.isBoxVisible(appContext, boxId)) {
                    allBoxes.find { it.id == boxId }
                } else {
                    null
                }
            }

            val items = visibleBoxes.map { box ->
                val (count, label) = when (box.id) {
                    HomeBoxPreferences.BOX_FAVORITES -> Pair(MusicRepository.getFavoriteCount(appContext), "Favorites")
                    HomeBoxPreferences.BOX_PLAYLISTS -> Pair(AppPlaylistManager.getAllPlaylists(appContext).size, "Playlists")
                    HomeBoxPreferences.BOX_ALBUMS -> Pair(MusicRepository.getAlbumCount(appContext), "Albums")
                    HomeBoxPreferences.BOX_ARTISTS -> Pair(MusicRepository.getArtistCount(appContext), "Artists")
                    HomeBoxPreferences.BOX_TRACKS -> Pair(MusicRepository.getTrackCount(appContext), "Tracks")
                    HomeBoxPreferences.BOX_EQUALIZER -> Pair(-1, "Tune Sound")
                    else -> Pair(0, "")
                }
                HomeBoxItemData(
                    id = box.id,
                    name = box.name.uppercase(),
                    iconRes = box.iconRes,
                    count = count,
                    countLabel = label
                )
            }

            _homeBoxes.value = items
            _isLoading.value = false
        }
    }
}
