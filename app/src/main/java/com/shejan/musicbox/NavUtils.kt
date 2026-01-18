package com.shejan.musicbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object NavUtils {

    fun setupNavigation(activity: Activity, activeNavId: Int) {
        val bottomNav = activity.findViewById<View>(R.id.bottom_nav) ?: return

        // 1. Calculate and Apply Widths
        adjustItemWidths(activity, bottomNav)

        // 2. Setup Click Listeners
        setupListeners(activity, bottomNav)

        // 3. Highlight Active Item
        highlightActiveItem(activity, bottomNav, activeNavId)
    }

    private fun adjustItemWidths(activity: Activity, root: View) {
        val scrollContainer = root.findViewById<LinearLayout>(R.id.ll_nav_scroll_container) ?: return
        val fixedContainer = root.findViewById<LinearLayout>(R.id.ll_nav_fixed_container) ?: return
        
        // Wait for layout or measure immediately if possible. 
        // Safer to use post to ensure fixed container has measured logic or use DisplayMetrics.
        
        root.post {
            // 1. Calculate Utilizable Width using actual root width (handles margins)
            val measuredWidth = root.width
            val padding = root.paddingStart + root.paddingEnd
            val utilizableWidth = measuredWidth - padding
            
            // 2. We want exactly 5 items visible in total width (3 scrollable + 2 fixed)
            val itemWidth = (utilizableWidth / 5.0).toInt()
            
            for (i in 0 until scrollContainer.childCount) {
                val child = scrollContainer.getChildAt(i)
                val params = child.layoutParams
                params.width = itemWidth
                child.layoutParams = params
            }

            // Apply same width/gap logic to fixed items (Search & Settings)
            for (i in 0 until fixedContainer.childCount) {
                val child = fixedContainer.getChildAt(i)
                val params = child.layoutParams
                params.width = itemWidth
                child.layoutParams = params
            }
            
            // 3. NOW restore scroll position because widths are set
            restoreScrollPosition(activity, root)
        }
    }

    private fun setupListeners(activity: Activity, root: View) {
        setListener(activity, root, R.id.nav_home, MainActivity::class.java)
        setListener(activity, root, R.id.nav_tracks, TracksActivity::class.java)
        setListener(activity, root, R.id.nav_albums, AlbumsActivity::class.java)
        setListener(activity, root, R.id.nav_folders, FoldersActivity::class.java)
        setListener(activity, root, R.id.nav_artists, ArtistsActivity::class.java)
        setListener(activity, root, R.id.nav_playlist, PlaylistActivity::class.java)
        setListener(activity, root, R.id.nav_search, SearchActivity::class.java)
        setListener(activity, root, R.id.nav_settings, SettingsActivity::class.java)
    }
    
    private fun setListener(activity: Activity, root: View, id: Int, targetClass: Class<*>) {
        root.findViewById<View>(id)?.setOnClickListener {
            if (activity.javaClass != targetClass) {
                val intent = Intent(activity, targetClass)
                
                // NEW: Capture current scroll position
                val scrollView = root.findViewById<HorizontalScrollView>(R.id.hsv_nav_scroll)
                if (scrollView != null) {
                    intent.putExtra("NAV_SCROLL_X", scrollView.scrollX)
                }
                
                // Add flags if needed to clear stack or reorder
                activity.startActivity(intent)
                activity.overridePendingTransition(0, 0)
                if (activity !is MainActivity) { 
                    // Optional: Finish current activity if not Main to prevent huge stack?
                    // For now keeping standard behavior as requested.
                }
            }
        }
    }

    private fun highlightActiveItem(activity: Activity, root: View, activeId: Int) {
        val activeItem = root.findViewById<LinearLayout>(activeId) ?: return
        
        val icon = activeItem.getChildAt(0) as? ImageView
        val text = activeItem.getChildAt(1) as? TextView
        
        icon?.setColorFilter(activity.getColor(R.color.white))
        text?.setTextColor(activity.getColor(R.color.white))
    }
    
    private fun restoreScrollPosition(activity: Activity, root: View) {
         val scrollView = root.findViewById<HorizontalScrollView>(R.id.hsv_nav_scroll) ?: return
         val scrollX = activity.intent.getIntExtra("NAV_SCROLL_X", -1)
         
         if (scrollX != -1) {
             scrollView.post {
                 scrollView.scrollTo(scrollX, 0)
             }
         }
    }
}
