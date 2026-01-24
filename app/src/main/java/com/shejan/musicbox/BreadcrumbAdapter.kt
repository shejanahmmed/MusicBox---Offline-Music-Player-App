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
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class BreadcrumbAdapter(
    private val breadcrumbs: MutableList<File>,
    private val onBreadcrumbClick: (File) -> Unit
) : RecyclerView.Adapter<BreadcrumbAdapter.BreadcrumbViewHolder>() {

    class BreadcrumbViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_breadcrumb_name)
        val separator: View = view.findViewById(R.id.v_separator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BreadcrumbViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_breadcrumb, parent, false)
        return BreadcrumbViewHolder(view)
    }

    override fun onBindViewHolder(holder: BreadcrumbViewHolder, position: Int) {
        val file = breadcrumbs[position]
        
        // Root override name
        if (file.absolutePath == android.os.Environment.getExternalStorageDirectory().absolutePath) {
             holder.name.text = holder.itemView.context.getString(R.string.breadcrumb_storage)
        } else {
             holder.name.text = file.name
        }

        // Highlight last item (current folder)
        if (position == breadcrumbs.size - 1) {
            holder.name.setTextColor("#EC1313".toColorInt()) // Primary Red
            holder.name.alpha = 1.0f
            holder.separator.visibility = View.GONE
        } else {
            holder.name.setTextColor(android.graphics.Color.WHITE)
            holder.name.alpha = 0.4f
            holder.separator.visibility = View.VISIBLE
        }

        holder.name.setOnClickListener {
            onBreadcrumbClick(file)
        }
    }

    override fun getItemCount() = breadcrumbs.size

    fun updateBreadcrumbs(path: File) {
        val oldSize = breadcrumbs.size
        breadcrumbs.clear()
        
        // Build hierarchy up to root
        var current: File? = path
        val rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath
        val tempStack = java.util.Stack<File>()
        
        while (current != null && current.absolutePath.startsWith(rootPath)) {
            tempStack.push(current)
            if (current.absolutePath == rootPath) break
            current = current.parentFile
        }
        
        while (!tempStack.isEmpty()) {
            breadcrumbs.add(tempStack.pop())
        }
        
        if (breadcrumbs.isEmpty()) {
            breadcrumbs.add(path) // Fallback if outside root
        }

        // Use more specific notification instead of notifyDataSetChanged
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize)
        }
        notifyItemRangeInserted(0, breadcrumbs.size)
    }
}

