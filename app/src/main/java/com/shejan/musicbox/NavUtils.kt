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
        
        // 1. Render Tabs Dynamically based on Order
        renderTabs(activity, bottomNav, activeNavId)

        // 2. Calculate and Apply Widths
        adjustItemWidths(activity, bottomNav)
        
        // 3. Highlight Active Item (handled in render but good to double check)
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
            if (activity.javaClass != targetClass || id != activeId) {
                 val intent = Intent(activity, targetClass)
                 intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                 activity.startActivity(intent)
                 @Suppress("DEPRECATION")
                 activity.overridePendingTransition(0, 0)
            }
        }
    }

    private fun adjustItemWidths(activity: Activity, root: View) {
        val scrollContainer = root.findViewById<LinearLayout>(R.id.ll_nav_scroll_container) ?: return
        val fixedContainer = root.findViewById<LinearLayout>(R.id.ll_nav_fixed_container) ?: return
        
        root.post {
            var measuredWidth = root.width
            if (measuredWidth == 0) {
                // Fallback to display metrics if view isn't laid out yet
                measuredWidth = activity.resources.displayMetrics.widthPixels
            }
            
            val padding = root.paddingStart + root.paddingEnd
            val utilizableWidth = measuredWidth - padding
            
            // Safety check
            if (utilizableWidth > 0) {
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

