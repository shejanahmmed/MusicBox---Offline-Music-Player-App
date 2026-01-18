package com.shejan.musicbox

import android.content.Context
import android.content.SharedPreferences

object TrackArtworkManager {
    private const val PREFS_NAME = "track_artwork"
    
    // Values:
    // "REMOVED" -> Artwork explicitly removed
    // "content://..." -> Custom artwork URI
    // missing -> Use default album art
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveArtwork(context: Context, trackId: Long, uri: String) {
        getPrefs(context).edit().putString(trackId.toString(), uri).apply()
    }
    
    fun removeArtwork(context: Context, trackId: Long) {
        saveArtwork(context, trackId, "REMOVED")
    }
    
    fun resetArtwork(context: Context, trackId: Long) {
        getPrefs(context).edit().remove(trackId.toString()).apply()
    }
    
    fun getArtworkUri(context: Context, trackId: Long): String? {
        return getPrefs(context).getString(trackId.toString(), null)
    }
    
    fun hasCustomArtwork(context: Context, trackId: Long): Boolean {
        return getPrefs(context).contains(trackId.toString())
    }
}
