package com.shejan.musicbox

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val nameInput = findViewById<EditText>(R.id.et_name)
        val getStartedBtn = findViewById<MaterialButton>(R.id.btn_get_started)

        getStartedBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            } else {
                val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
                prefs.edit().apply {
                    putString("USER_NAME", name)
                    putBoolean("IS_FIRST_RUN", false)
                    apply()
                }

                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }
}
