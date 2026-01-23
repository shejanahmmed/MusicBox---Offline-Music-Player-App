package com.shejan.musicbox

import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeCustomizationActivity : AppCompatActivity() {
    
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_customization)

        // Apply WindowInsets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Back Button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Setup RecyclerView
        val rvBoxes = findViewById<RecyclerView>(R.id.rv_boxes)
        rvBoxes.layoutManager = LinearLayoutManager(this)

        // Load boxes in saved order
        val savedOrder = HomeBoxPreferences.getBoxOrder(this)
        val allBoxes = HomeBoxPreferences.getAllBoxes()
        val orderedBoxes = savedOrder.mapNotNull { boxId ->
            allBoxes.find { it.id == boxId }
        }.toMutableList()

        val adapter = HomeBoxAdapter(
            orderedBoxes,
            onVisibilityChanged = { box, isVisible ->
                HomeBoxPreferences.setBoxVisibility(this, box.id, isVisible)
            },
            onOrderChanged = { boxes ->
                val order = boxes.map { it.id }
                HomeBoxPreferences.saveBoxOrder(this, order)
            }
        )
        rvBoxes.adapter = adapter
        
        // Setup ItemTouchHelper for drag and drop
        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }
            
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            
            override fun isLongPressDragEnabled() = false
        }
        
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rvBoxes)
        
        // Setup drag handles
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                setupDragHandles(rvBoxes)
            }
        })
        rvBoxes.post { setupDragHandles(rvBoxes) }
    }
    
    private fun setupDragHandles(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val viewHolder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (viewHolder is HomeBoxAdapter.ViewHolder) {
                viewHolder.dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(viewHolder)
                    }
                    false
                }
            }
        }
    }
}
