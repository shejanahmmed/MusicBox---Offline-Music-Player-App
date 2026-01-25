package com.shejan.musicbox

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri

import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GitHubReleaseManager {
    private const val REPO_OWNER = "shejanahmmed"
    private const val REPO_NAME = "MusicBox---Offline-Music-Player-App"
    private const val API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"

    fun checkForUpdates(context: Context, isManualCheck: Boolean = false) {
        val prefs = context.getSharedPreferences("MusicBoxPrefs", Context.MODE_PRIVATE)
        val showPreReleases = prefs.getBoolean("show_pre_releases", false)

        if (isManualCheck) {
            Toast.makeText(context, context.getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                // Add User-Agent (GitHub API requires it)
                connection.setRequestProperty("User-Agent", "MusicBox-App")

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val releases = JSONArray(response.toString())
                    if (releases.length() > 0) {
                        for (i in 0 until releases.length()) {
                            val release = releases.getJSONObject(i)
                            val isPrerelease = release.getBoolean("prerelease")
                            
                            // If we don't want pre-releases, skip this one if it is a pre-release
                            if (!showPreReleases && isPrerelease) continue

                            val tagName = release.getString("tag_name") // e.g., "v1.0.1"
                            val htmlUrl = release.getString("html_url")
                            val body = release.getString("body") // Release notes
                            
                            // Check version
                            if (isNewerVersion(tagName)) {
                                (context as? Activity)?.runOnUiThread {
                                    showUpdateDialog(context, tagName, body, htmlUrl)
                                }
                                return@Thread // Found the latest applicable update and returned
                            } else {
                                // Since releases are ordered by date, if the newest one isn't newer than ours, we are up to date.
                                break 
                            }
                        }
                        
                        // If we are here, no update was found/returned
                        if (isManualCheck) {
                             (context as? Activity)?.runOnUiThread {
                                Toast.makeText(context, context.getString(R.string.no_updates_found), Toast.LENGTH_SHORT).show()
                             }
                        }
                    }
                } else {
                     if (isManualCheck) {
                         (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "${context.getString(R.string.update_error)}: ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                         }
                    }
                }
            } catch (_: Exception) {
                 if (isManualCheck) {
                     (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, context.getString(R.string.update_error), Toast.LENGTH_SHORT).show()
                     }
                }
            }
        }.start()
    }

    private fun isNewerVersion(tagName: String): Boolean {
        // Remove 'v' prefix if present
        val currentVersionName = BuildConfig.VERSION_NAME.replace("v", "", ignoreCase = true).trim()
        val newVersionName = tagName.replace("v", "", ignoreCase = true).trim()
        
        // Simple string check to avoid self-update
        if (currentVersionName.equals(newVersionName, ignoreCase = true)) return false
        
        try {
            val currentParts = currentVersionName.split(".").map { it.toInt() }
            val newParts = newVersionName.split(".").map { it.toInt() }
            
            val length = maxOf(currentParts.size, newParts.size)
            for (i in 0 until length) {
                val v1 = if (i < currentParts.size) currentParts[i] else 0
                val v2 = if (i < newParts.size) newParts[i] else 0
                if (v2 > v1) return true
                if (v2 < v1) return false
            }
        } catch (_: NumberFormatException) {
            // Fallback for non-semantic versions (e.g. "Beta 1")
            return !newVersionName.equals(currentVersionName, ignoreCase = true)
        }
        return false
    }

    private fun showUpdateDialog(context: Context, version: String, notes: String, url: String) {
        val dialog = AlertDialog.Builder(context)
            .create()
        
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_update, null)
        dialog.setView(view)
        
        // Transparent window background to let our custom background show (essential for rounded corners)
        dialog.window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tv_update_title)
        val tvNotes = view.findViewById<android.widget.TextView>(R.id.tv_update_notes)
        val btnDownload = view.findViewById<android.view.View>(R.id.btn_download)
        val btnLater = view.findViewById<android.view.View>(R.id.btn_later)

        tvTitle.text = context.getString(R.string.update_available_title, version)
        tvNotes.text = notes
        
        btnDownload.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                context.startActivity(intent)
            } catch(_: Exception) {
                Toast.makeText(context, context.getString(R.string.open_browser_error), Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        btnLater.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
