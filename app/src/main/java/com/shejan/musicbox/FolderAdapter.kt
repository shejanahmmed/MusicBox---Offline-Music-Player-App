package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FolderAdapter(
    private var files: List<File>,
    private val onFileClick: (File) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val name: TextView = view.findViewById(R.id.tv_folder_name)
        val chevron: ImageView = view.findViewById(R.id.iv_chevron)
        val iconCard: androidx.cardview.widget.CardView = view.findViewById(R.id.cv_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_list, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val file = files[position]
        holder.name.text = file.name

        if (file.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            holder.icon.setColorFilter(android.graphics.Color.parseColor("#B3FFFFFF")) // White-ish for folders
            holder.chevron.setImageResource(R.drawable.ic_chevron_right)
            holder.chevron.visibility = View.VISIBLE
            holder.iconCard.setCardBackgroundColor(android.graphics.Color.parseColor("#0DFFFFFF")) // Dark transparent
        } else {
            // Audio File
            holder.icon.setImageResource(R.drawable.ic_audiotrack) // Or specific file icon
            holder.icon.setColorFilter(android.graphics.Color.parseColor("#EC1313")) // Primary Red for files
            holder.chevron.setImageResource(R.drawable.ic_more_horiz) // Options menu for files
            holder.chevron.visibility = View.VISIBLE
             holder.iconCard.setCardBackgroundColor(android.graphics.Color.parseColor("#1AEC1313")) // Red transparent
        }

        holder.itemView.setOnClickListener {
            onFileClick(file)
        }
    }

    override fun getItemCount() = files.size
    
    fun updateData(newFiles: List<File>) {
        files = newFiles
        notifyDataSetChanged()
    }
}
