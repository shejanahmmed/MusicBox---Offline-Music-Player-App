package com.shejan.musicbox

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TabManager {

    data class TabItem(
        val id: String,
        val label: String,
        val iconResId: Int,
        var isVisible: Boolean = true,
        val viewId: Int
    )

    private const val PREFS_NAME = "MusicBoxPrefs"
    private const val KEY_TAB_ORDER = "tab_order_list"
    private const val KEY_HOME_TAB = "home_tab_id"

    val defaultTabs = listOf(
        TabItem("home", "Home", R.drawable.ic_home, true, R.id.nav_home),
        TabItem("tracks", "Tracks", R.drawable.ic_library_music, true, R.id.nav_tracks),
        TabItem("albums", "Albums", R.drawable.ic_album, true, R.id.nav_albums),
        TabItem("folders", "Folders", R.drawable.ic_folder, true, R.id.nav_folders),
        TabItem("artists", "Artists", R.drawable.ic_person, true, R.id.nav_artists),
        TabItem("playlist", "Playlist", R.drawable.ic_queue_music, true, R.id.nav_playlist)
    )

    fun getTabOrder(context: Context): List<TabItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TAB_ORDER, null)

        val resultList = if (json != null) {
            try {
                val list = mutableListOf<TabItem>()
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    // Find default to restore static props like icon/viewId
                    // Note: If ID no longer exists in default (removed feature), we skip it to avoid crash
                    val def = defaultTabs.find { it.id == id }
                    if (def != null) {
                        list.add(
                            def.copy(isVisible = obj.getBoolean("isVisible"))
                        )
                    }
                }
                if (list.isNotEmpty()) list else defaultTabs.toMutableList()
            } catch (e: Exception) {
                defaultTabs.toMutableList()
            }
        } else {
            defaultTabs.toMutableList()
        }

        // Merge: Ensure all default tabs are present (if new ones added in update)
        val existingIds = resultList.map { it.id }.toSet()
        for (defTab in defaultTabs) {
            if (defTab.id !in existingIds) {
                resultList.add(defTab)
            }
        }
        
        // Safety: If all tabs are hidden (data corruption or user mistake), force show them
        if (resultList.none { it.isVisible }) {
            resultList.forEach { it.isVisible = true }
        }

        return resultList
    }

    fun saveTabOrder(context: Context, tabs: List<TabItem>) {
        val array = JSONArray()
        for (tab in tabs) {
            val obj = JSONObject()
            obj.put("id", tab.id)
            obj.put("isVisible", tab.isVisible)
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_TAB_ORDER, array.toString()).apply()
    }

    fun getHomeTabId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HOME_TAB, "home") ?: "home"
    }

    fun setHomeTabId(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HOME_TAB, id).apply()
    }
    
    fun getTargetActivity(tabId: String): Class<*> {
        return when(tabId) {
            "home" -> MainActivity::class.java
            "tracks" -> TracksActivity::class.java
            "albums" -> AlbumsActivity::class.java
            "folders" -> FoldersActivity::class.java
            "artists" -> ArtistsActivity::class.java
            "playlist" -> PlaylistActivity::class.java
            else -> MainActivity::class.java
        }
    }
}
