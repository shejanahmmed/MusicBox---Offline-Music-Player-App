package com.shejan.musicbox

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeCustomizationActivity : AppCompatActivity() {

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

        val boxes = HomeBoxPreferences.getAllBoxes()
        val adapter = HomeBoxAdapter(boxes) { box, isVisible ->
            HomeBoxPreferences.setBoxVisibility(this, box.id, isVisible)
        }
        rvBoxes.adapter = adapter
    }
}
