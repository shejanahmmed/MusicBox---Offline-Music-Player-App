package com.shejan.musicbox

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object TrackMetadataManager {
    private const val PREFS_NAME = "track_metadata"
    private const val KEY_METADATA = "custom_metadata"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun saveMetadata(context: Context, trackId: Long, title: String?, artist: String?, album: String?, year: String?) {
        val prefs = getPrefs(context)
        val allMetadata = getAllMetadata(context).toMutableMap()
        
        val metadata = JSONObject()
        metadata.put("title", title ?: "")
        metadata.put("artist", artist ?: "")
        metadata.put("album", album ?: "")
        metadata.put("year", year ?: "")
        
        allMetadata[trackId.toString()] = metadata.toString()
        
        prefs.edit().putString(KEY_METADATA, JSONObject(allMetadata).toString()).apply()
    }
    
    fun getMetadata(context: Context, trackId: Long): CustomTrackMetadata? {
        val allMetadata = getAllMetadata(context)
        val metadataStr = allMetadata[trackId.toString()] ?: return null
        
        return try {
            val json = JSONObject(metadataStr)
            CustomTrackMetadata(
                trackId = trackId,
                title = json.optString("title").takeIf { it.isNotEmpty() },
                artist = json.optString("artist").takeIf { it.isNotEmpty() },
                album = json.optString("album").takeIf { it.isNotEmpty() },
                year = json.optString("year").takeIf { it.isNotEmpty() }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun resetMetadata(context: Context, trackId: Long) {
        val prefs = getPrefs(context)
        val allMetadata = getAllMetadata(context).toMutableMap()
        allMetadata.remove(trackId.toString())
        prefs.edit().putString(KEY_METADATA, JSONObject(allMetadata).toString()).apply()
    }
    
    fun hasCustomMetadata(context: Context, trackId: Long): Boolean {
        return getAllMetadata(context).containsKey(trackId.toString())
    }
    
    private fun getAllMetadata(context: Context): Map<String, String> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_METADATA, "{}") ?: "{}"
        
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
    fun applyMetadata(context: Context, track: Track): Track {
        val metadata = getMetadata(context, track.id) ?: return track
        
        return track.copy(
            title = metadata.title ?: track.title,
            artist = metadata.artist ?: track.artist,
            album = metadata.album ?: track.album,
            // Track object doesn't have year field yet, but if we add it later or use it in adapters, it's ready.
            // For now, only copying what Track has.
        )
    }
}

data class CustomTrackMetadata(
    val trackId: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: String?
)
