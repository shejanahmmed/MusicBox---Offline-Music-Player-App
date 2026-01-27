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

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object NavUtils {

    fun setupNavigation(activity: Activity, activeNavId: Int) {
        val bottomNav = activity.findViewById<View>(R.id.bottom_nav) ?: return
        
        // 0. Disable Activity Transition Animation immediately
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)

        // 1. Calculate Layout Dimensions Synchronously
        val displayMetrics = activity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density
        
        // Account for:
        // 1. BottomNav internal padding (4dp start + 4dp end = 8dp)
        // 2. BottomNav external layout margin (16dp * 2 = 32dp) - Common across activities
        val totalHorizontalDeduction = ((8 + 32) * density).toInt() 
        val utilizableWidth = screenWidth - totalHorizontalDeduction
        
        val itemWidth = if (utilizableWidth > 0) (utilizableWidth / 5.0).toInt() else 0

        // 2. Render Tabs Dynamically based on Order
        renderTabs(activity, bottomNav, activeNavId, itemWidth)
        
        // 3. Setup Fixed Items (Search/Settings)
        setupFixedItems(activity, bottomNav, activeNavId, itemWidth)
        
        // 4. Restore Scroll
        restoreScrollPosition(activity, bottomNav)
    }

    private fun renderTabs(activity: Activity, root: View, activeNavId: Int, itemWidth: Int) {
        val scrollContainer = root.findViewById<LinearLayout>(R.id.ll_nav_scroll_container) ?: return
        scrollContainer.removeAllViews()
        
        val tabOrder = TabManager.getTabOrder(activity)
        val inflater = android.view.LayoutInflater.from(activity)
        
        for (tab in tabOrder) {
            if (tab.isVisible) {
                val itemView = inflater.inflate(R.layout.item_nav_tab, scrollContainer, false) as LinearLayout
                itemView.id = tab.viewId
                
                // Apply Width Immediately
                if (itemWidth > 0) {
                    val params = LinearLayout.LayoutParams(itemWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    itemView.layoutParams = params
                }
                
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
                    if (activity.javaClass != targetClass || tab.viewId != activeNavId) {
                        val intent = Intent(activity, targetClass)
                         // NEW: Capture current scroll position
                        val scrollView = root.findViewById<HorizontalScrollView>(R.id.hsv_nav_scroll)
                        if (scrollView != null) {
                            intent.putExtra("NAV_SCROLL_X", scrollView.scrollX)
                        }
                        intent.putExtra("IS_NAV_CLICK", true) // Mark as creating from Nav
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // Avoid stacking
                        activity.startActivity(intent)
                        @Suppress("DEPRECATION")
                        activity.overridePendingTransition(0, 0)
                    }
                }
                
                scrollContainer.addView(itemView)
            }
        }
    }
    
    private fun setupFixedItems(activity: Activity, root: View, activeNavId: Int, itemWidth: Int) {
        val search = root.findViewById<View>(R.id.nav_search)
        val settings = root.findViewById<View>(R.id.nav_settings)
        
        setupFixedListener(activity, search, SearchActivity::class.java, R.id.nav_search, activeNavId, itemWidth)
        setupFixedListener(activity, settings, SettingsActivity::class.java, R.id.nav_settings, activeNavId, itemWidth)
    }
    
    private fun setupFixedListener(activity: Activity, view: View?, targetClass: Class<*>, id: Int, activeId: Int, itemWidth: Int) {
        if (view == null) return
        
        // Apply Width Immediately
        if (itemWidth > 0) {
            val params = view.layoutParams
            params.width = itemWidth
            view.layoutParams = params
        }
        
        // Highlight logic for fixed items
        if (view is LinearLayout) {
             val icon = view.getChildAt(0) as? ImageView ?: return
             val text = view.getChildAt(1) as? TextView ?: return
             if (id == activeId) {
                 icon.setColorFilter(activity.getColor(R.color.white))
                 text.setTextColor(activity.getColor(R.color.white))
             } else {
                 icon.setColorFilter(activity.getColor(R.color.nav_text_unselected))
                 text.setTextColor(activity.getColor(R.color.nav_text_unselected))
             }
        }

        view.setOnClickListener {
            if (activity.javaClass != targetClass || id != activeId) {
                 val intent = Intent(activity, targetClass)
                 intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                 activity.startActivity(intent)
                 @Suppress("DEPRECATION")
                 activity.overridePendingTransition(0, 0)
            }
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

