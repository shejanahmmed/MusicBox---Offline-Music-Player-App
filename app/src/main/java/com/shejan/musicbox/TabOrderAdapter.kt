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

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class TabOrderAdapter(
    private val tabs: MutableList<TabManager.TabItem>,
    private var currentHomeId: String,
    private val onHomeSelected: (String) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<TabOrderAdapter.TabViewHolder>() {

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_tab_name)
        val handle: ImageView = itemView.findViewById(R.id.iv_drag_handle)
        val btnVisibility: ImageView = itemView.findViewById(R.id.btn_toggle_visibility)
        val btnHome: ImageView = itemView.findViewById(R.id.btn_set_home)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tab_order, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.tvName.text = tab.label
        
        // Visibility Logic
        updateVisibilityIcon(holder.btnVisibility, tab.isVisible)
        holder.btnVisibility.setOnClickListener {
            tab.isVisible = !tab.isVisible
            updateVisibilityIcon(holder.btnVisibility, tab.isVisible)
        }

        // Home Logic
        if (tab.id == currentHomeId) {
            holder.btnHome.alpha = 1.0f
        } else {
            holder.btnHome.alpha = 0.5f // Light white
        }

        holder.btnHome.setOnClickListener {
             if (currentHomeId != tab.id) {
                 currentHomeId = tab.id
                 notifyDataSetChanged()
                 onHomeSelected(currentHomeId)
             }
        }

        holder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }
    
    private fun updateVisibilityIcon(view: ImageView, isVisible: Boolean) {
        if (isVisible) {
            view.alpha = 1.0f
        } else {
            view.alpha = 0.3f
        }
    }

    override fun getItemCount(): Int = tabs.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        // Bounds checking to prevent IndexOutOfBoundsException
        if (tabs.isEmpty() || fromPosition < 0 || toPosition < 0 || 
            fromPosition >= tabs.size || toPosition >= tabs.size) return
        
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                if (i + 1 < tabs.size) {
                    Collections.swap(tabs, i, i + 1)
                }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                if (i - 1 >= 0) {
                    Collections.swap(tabs, i, i - 1)
                }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentHomeId(): String {
        return currentHomeId
    }
}

