package com.shejan.musicbox

import android.content.Context
import androidx.core.content.edit

object FavoritesManager {
    private const val PREF_NAME = "MusicBoxFavorites"
    private const val KEY_FAVORITES = "favorite_uris"

    fun isFavorite(context: Context, uri: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return favorites.contains(uri)
    }

    fun addFavorite(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentFavorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        
        // Create a new HashSet to avoid SharedPreferences mutation issues
        val newFavorites = HashSet(currentFavorites)
        newFavorites.add(uri)
        
        prefs.edit {
            remove(KEY_FAVORITES) // Remove old set first
            putStringSet(KEY_FAVORITES, newFavorites)
        }
    }

    fun removeFavorite(context: Context, uri: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentFavorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        
        // Create a new HashSet to avoid SharedPreferences mutation issues
        val newFavorites = HashSet(currentFavorites)
        newFavorites.remove(uri)
        
        prefs.edit {
            remove(KEY_FAVORITES) // Remove old set first
            putStringSet(KEY_FAVORITES, newFavorites)
        }
    }
    
    fun getFavorites(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }
}
