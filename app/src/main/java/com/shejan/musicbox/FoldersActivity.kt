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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.content.Context
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.IBinder
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.View
import android.annotation.SuppressLint
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat



class FoldersActivity : AppCompatActivity() {

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private val filesList = mutableListOf<File>()
    private val breadcrumbList = mutableListOf<File>()
    
    // All audio file paths from MediaStore
    private val allAudioPaths = mutableListOf<String>()
    
    // Initial Path - start at external storage root
    private var currentPath: File = Environment.getExternalStorageDirectory()

    private var musicService: MusicService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            MiniPlayerManager.update(this@FoldersActivity, musicService)
            MiniPlayerManager.setup(this@FoldersActivity) { musicService }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "MUSIC_BOX_UPDATE") {
                MiniPlayerManager.update(this@FoldersActivity, musicService)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
        
        val filter = IntentFilter("MUSIC_BOX_UPDATE")
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        unregisterReceiver(receiver)
    }
    
    override fun onResume() {
        super.onResume()
        MiniPlayerManager.update(this, musicService)
        MiniPlayerManager.setup(this) { musicService }
        // Refresh Navigation in case Settings changed
        NavUtils.setupNavigation(this, R.id.nav_folders)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)
        
        // Apply WindowInsets to handle Navigation Bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Setup Adapters
        folderAdapter = FolderAdapter(filesList) { file ->
            if (file.isDirectory) {
                navigateTo(file)
            } else {
                playAudioFile(file)
            }
        }
        
        breadcrumbAdapter = BreadcrumbAdapter(breadcrumbList) { file ->
            navigateTo(file)
        }

        findViewById<RecyclerView>(R.id.rv_folders).apply {
            layoutManager = LinearLayoutManager(this@FoldersActivity)
            adapter = folderAdapter
        }

        findViewById<RecyclerView>(R.id.rv_breadcrumbs).apply {
            layoutManager = LinearLayoutManager(this@FoldersActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = breadcrumbAdapter
        }
        
        // Navigation Bar Listeners
        // Navigation
        NavUtils.setupNavigation(this, R.id.nav_folders)
        
        // Check Permissions
        checkPermissionsAndLoad()

        setupBackNavigation()
    }

    private fun checkPermissionsAndLoad() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 101)
        } else {
            loadAllAudioPaths()
            loadFiles(currentPath)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadAllAudioPaths()
            loadFiles(currentPath)
        } else {
            Toast.makeText(this, "Permission Denied. Cannot load folders.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllAudioPaths() {
        allAudioPaths.clear()
        
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val selection = "${MediaStore.Audio.Media.DURATION} >= 10000"
        
        try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (!path.lowercase().contains("ringtone") && !path.lowercase().contains("notification")) {
                        allAudioPaths.add(path)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFiles(directory: File) {
        currentPath = directory
        
        // Update Breadcrumbs
        breadcrumbAdapter.updateBreadcrumbs(currentPath)
        findViewById<RecyclerView>(R.id.rv_breadcrumbs).scrollToPosition(breadcrumbAdapter.itemCount - 1)

        val tempFiles = mutableListOf<File>()
        val subdirectories = mutableSetOf<String>()
        val currentPathStr = currentPath.absolutePath
        
        // Find all paths that start with current directory
        for (audioPath in allAudioPaths) {
            if (audioPath.startsWith(currentPathStr)) {
                // Get relative path from current directory
                val relativePath = audioPath.substring(currentPathStr.length).trimStart('/')
                
                if (relativePath.isEmpty()) continue
                
                // Check if this is a direct child or in a subdirectory
                val firstSlash = relativePath.indexOf('/')
                
                if (firstSlash == -1) {
                    // Direct child file
                    tempFiles.add(File(audioPath))
                } else {
                    // In a subdirectory - extract the immediate subdirectory name
                    val subdirName = relativePath.take(firstSlash)
                    subdirectories.add(subdirName)
                }
            }
        }
        
        // Add subdirectories as File objects
        for (subdir in subdirectories) {
            tempFiles.add(File(currentPath, subdir))
        }
        
        // Sort: Folders first, then files
        tempFiles.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        filesList.clear()
        filesList.addAll(tempFiles)
        folderAdapter.notifyDataSetChanged()
        
        // Empty State
        findViewById<TextView>(R.id.tv_empty_state).visibility = 
            if (filesList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun navigateTo(file: File) {
        if (file.isDirectory) {
            loadFiles(file)
        }
    }

    private fun playAudioFile(file: File) {
        Toast.makeText(this, "Playing ${file.name}", Toast.LENGTH_SHORT).show()
        
        // For now, just show a toast. Full playback integration would require
        // matching the file path back to MediaStore ID and passing to MusicService
    }



    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val rootPath = Environment.getExternalStorageDirectory().absolutePath
                
                if (currentPath.absolutePath != rootPath && currentPath.parentFile != null) {
                    val parent = currentPath.parentFile
                    if (parent != null && parent.absolutePath.startsWith(rootPath)) {
                        loadFiles(parent)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}

