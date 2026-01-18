package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
             holder.name.text = "STORAGE"
        } else {
             holder.name.text = file.name
        }

        // Highlight last item (current folder)
        if (position == breadcrumbs.size - 1) {
            holder.name.setTextColor(android.graphics.Color.parseColor("#EC1313")) // Primary Red
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

        notifyDataSetChanged()
    }
}
