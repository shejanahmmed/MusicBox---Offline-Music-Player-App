package com.shejan.musicbox

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

class HomeBoxAdapter(
    private val boxes: List<HomeBox>,
    private val onVisibilityChanged: (HomeBox, Boolean) -> Unit
) : RecyclerView.Adapter<HomeBoxAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_box_icon)
        val name: TextView = view.findViewById(R.id.tv_box_name)
        val switch: SwitchMaterial = view.findViewById(R.id.switch_visibility)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_box, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val box = boxes[position]
        holder.icon.setImageResource(box.iconRes)
        holder.name.text = box.name
        
        // Load current visibility state
        val isVisible = HomeBoxPreferences.isBoxVisible(holder.itemView.context, box.id)
        holder.switch.isChecked = isVisible
        
        // Handle switch changes
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            onVisibilityChanged(box, isChecked)
        }
        
        // Also toggle switch when card is clicked
        holder.itemView.setOnClickListener {
            holder.switch.isChecked = !holder.switch.isChecked
        }
    }

    override fun getItemCount() = boxes.size
}
