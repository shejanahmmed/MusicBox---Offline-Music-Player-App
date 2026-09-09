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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_about)

        // Apply WindowInsets for edge-to-edge support
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        // Back Navigation
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Version Badge
        val tvVersion = findViewById<TextView>(R.id.tv_app_version)
        val versionString = getString(R.string.about_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        tvVersion.text = versionString

        // Copy version details on clicking version badge
        findViewById<LinearLayout>(R.id.btn_version_badge).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipInfo = "MusicBox $versionString | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val clip = ClipData.newPlainText("MusicBox Version", clipInfo)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.about_version_copied, Toast.LENGTH_SHORT).show()
        }

        // Developer Social Links
        findViewById<View>(R.id.btn_social_email).setOnClickListener {
            openUrl("mailto:farjan.swe@gmail.com")
        }
        findViewById<View>(R.id.btn_social_linkedin).setOnClickListener {
            openAppOrUrl("https://www.linkedin.com/in/farjanahmmed/", "com.linkedin.android")
        }
        findViewById<View>(R.id.btn_social_github).setOnClickListener {
            openUrl("https://github.com/shejanahmmed")
        }
        findViewById<View>(R.id.btn_social_facebook).setOnClickListener {
            openAppOrUrl("https://www.facebook.com/beingshejan/", "com.facebook.katana")
        }
        findViewById<View>(R.id.btn_social_instagram).setOnClickListener {
            openAppOrUrl("https://www.instagram.com/iamshejan/", "com.instagram.android")
        }

        // GitHub Repository Link
        findViewById<LinearLayout>(R.id.btn_github).setOnClickListener {
            openUrl("https://github.com/shejanahmmed/MusicBox---Offline-Music-Player-App")
        }
    }

    private fun openAppOrUrl(url: String, appPackage: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                setPackage(appPackage)
            }
            startActivity(intent)
        } catch (_: Exception) {
            openUrl(url)
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_browser_error, Toast.LENGTH_SHORT).show()
        }
    }
}
