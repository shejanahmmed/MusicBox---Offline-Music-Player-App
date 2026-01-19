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
        
        // 1. Render Tabs Dynamically based on Order
        renderTabs(activity, bottomNav, activeNavId)

        // 2. Calculate and Apply Widths
        adjustItemWidths(activity, bottomNav)
        
        // 3. Highlight Active Item (handled in render but good to doublcheck)
        // highlightActiveItem(activity, bottomNav, activeNavId) 
    }

    private fun renderTabs(activity: Activity, root: View, activeNavId: Int) {
        val scrollContainer = root.findViewById<LinearLayout>(R.id.ll_nav_scroll_container) ?: return
        scrollContainer.removeAllViews()
        
        val tabOrder = TabManager.getTabOrder(activity)
        val inflater = android.view.LayoutInflater.from(activity)
        
        for (tab in tabOrder) {
            if (tab.isVisible) {
                val itemView = inflater.inflate(R.layout.item_nav_tab, scrollContainer, false) as LinearLayout
                itemView.id = tab.viewId
                
                val icon = itemView.getChildAt(0) as ImageView
                val text = itemView.getChildAt(1) as TextView
                
                icon.setImageResource(tab.iconResId)
                text.text = tab.label
                
                // Highlight if active
                if (tab.viewId == activeNavId) {
                    icon.setColorFilter(activity.getColor(R.color.white))
                    text.setTextColor(activity.getColor(R.color.white))
                } else {
                    icon.setColorFilter(activity.getColor(R.color.nav_text_unselected))
                    text.setTextColor(activity.getColor(R.color.nav_text_unselected))
                }
                
                // Click Listener
                itemView.setOnClickListener {
                    val targetClass = TabManager.getTargetActivity(tab.id)
                    if (activity.javaClass != targetClass) {
                        val intent = Intent(activity, targetClass)
                         // NEW: Capture current scroll position
                        val scrollView = root.findViewById<HorizontalScrollView>(R.id.hsv_nav_scroll)
                        if (scrollView != null) {
                            intent.putExtra("NAV_SCROLL_X", scrollView.scrollX)
                        }
                        intent.putExtra("IS_NAV_CLICK", true) // Mark as creating from Nav
                        activity.startActivity(intent)
                        activity.overridePendingTransition(0, 0)
                    }
                }
                
                scrollContainer.addView(itemView)
            }
        }
        
        // Fixed Listeners (Search and Settings are static in XML)
        val search = root.findViewById<View>(R.id.nav_search)
        val settings = root.findViewById<View>(R.id.nav_settings)
        
        setupFixedListener(activity, search, SearchActivity::class.java, R.id.nav_search, activeNavId)
        setupFixedListener(activity, settings, SettingsActivity::class.java, R.id.nav_settings, activeNavId)
    }
    
    private fun setupFixedListener(activity: Activity, view: View?, targetClass: Class<*>, id: Int, activeId: Int) {
        if (view == null) return
        
        // Highlight logic for fixed items
        if (view is LinearLayout) {
             val icon = view.getChildAt(0) as ImageView
             val text = view.getChildAt(1) as TextView
             if (id == activeId) {
                 icon.setColorFilter(activity.getColor(R.color.white))
                 text.setTextColor(activity.getColor(R.color.white))
             } else {
                 icon.setColorFilter(activity.getColor(R.color.nav_text_unselected))
                 text.setTextColor(activity.getColor(R.color.nav_text_unselected))
             }
        }

        view.setOnClickListener {
            if (activity.javaClass != targetClass) {
                 val intent = Intent(activity, targetClass)
                 activity.startActivity(intent)
                 activity.overridePendingTransition(0, 0)
            }
        }
    }

    private fun adjustItemWidths(activity: Activity, root: View) {
        val scrollContainer = root.findViewById<LinearLayout>(R.id.ll_nav_scroll_container) ?: return
        val fixedContainer = root.findViewById<LinearLayout>(R.id.ll_nav_fixed_container) ?: return
        
        root.post {
            val measuredWidth = root.width
            val padding = root.paddingStart + root.paddingEnd
            val utilizableWidth = measuredWidth - padding
            val itemWidth = (utilizableWidth / 5.0).toInt()
            
            for (i in 0 until scrollContainer.childCount) {
                val child = scrollContainer.getChildAt(i)
                val params = child.layoutParams
                params.width = itemWidth
                child.layoutParams = params
            }

            for (i in 0 until fixedContainer.childCount) {
                val child = fixedContainer.getChildAt(i)
                val params = child.layoutParams
                params.width = itemWidth
                child.layoutParams = params
            }
            
            restoreScrollPosition(activity, root)
        }
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
