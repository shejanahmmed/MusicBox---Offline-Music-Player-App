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
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class MainHomeBox(
    val id: String,
    val name: String,
    val iconRes: Int,
    val iconTint: Int,
    val count: Int,
    val countLabel: String,
    val onClick: () -> Unit
)

class MainHomeBoxAdapter(
    private val boxes: List<MainHomeBox>
) : RecyclerView.Adapter<MainHomeBoxAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_box_icon)
        val label: TextView = view.findViewById(R.id.tv_box_label)
        val count: TextView = view.findViewById(R.id.tv_box_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_main_home_box, parent, false)
            
        // Calculate exact width to square the box immediately (preventing layout flash)
        val displayMetrics = parent.context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val density = displayMetrics.density
        
        // Padding: 22dp Left + 22dp Right + 8dp Middle Gap = 52dp Total Deduction
        val totalPadding = (52 * density).toInt()
        val itemWidth = (screenWidth - totalPadding) / 2
        
        val params = view.layoutParams
        params.width = itemWidth
        params.height = itemWidth
        view.layoutParams = params
            
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val box = boxes[position]
        
        holder.icon.setImageResource(box.iconRes)
        holder.icon.setColorFilter(box.iconTint)
        holder.label.text = box.name
        if (box.count == -1) {
            holder.count.text = box.countLabel
        } else {
            holder.count.text = holder.itemView.context.getString(R.string.count_with_label, box.count, box.countLabel)
        }
        
        holder.itemView.setOnClickListener {
            box.onClick()
        }
    }

    override fun getItemCount() = boxes.size
}

