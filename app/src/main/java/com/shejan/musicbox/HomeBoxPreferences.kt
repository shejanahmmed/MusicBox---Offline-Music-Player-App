package com.shejan.musicbox

import android.content.Context

object HomeBoxPreferences {
    private const val PREF_NAME = "HomeBoxPrefs"
    
    // Box IDs
    const val BOX_FAVORITES = "favorites"
    const val BOX_PLAYLISTS = "playlists"
    const val BOX_ALBUMS = "albums"
    const val BOX_ARTISTS = "artists"
    const val BOX_TRACKS = "tracks"
    const val BOX_EQUALIZER = "equalizer"
    
    fun isBoxVisible(context: Context, boxId: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(boxId, true) // Default: all visible
    }
    
    fun setBoxVisibility(context: Context, boxId: String, visible: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(boxId, visible).apply()
    }
    
    fun getAllBoxes(): List<HomeBox> {
        return listOf(
            HomeBox(BOX_FAVORITES, "Favorites", R.drawable.ic_heart),
            HomeBox(BOX_PLAYLISTS, "Playlists", R.drawable.ic_queue_music),
            HomeBox(BOX_ALBUMS, "Albums", R.drawable.ic_album),
            HomeBox(BOX_ARTISTS, "Artists", R.drawable.ic_person),
            HomeBox(BOX_TRACKS, "Tracks", R.drawable.ic_music_note),
            HomeBox(BOX_EQUALIZER, "Equalizer", R.drawable.ic_equalizer)
        )
    }
}

data class HomeBox(
    val id: String,
    val name: String,
    val iconRes: Int
)
