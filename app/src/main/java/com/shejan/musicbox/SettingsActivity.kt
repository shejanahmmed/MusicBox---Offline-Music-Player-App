package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Back Button
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Navigation
        NavUtils.setupNavigation(this, R.id.nav_settings)
    }
}
