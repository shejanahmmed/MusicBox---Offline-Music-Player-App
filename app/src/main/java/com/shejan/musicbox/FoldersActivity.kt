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

data class FileItem(val file: File, val isDirectory: Boolean, val name: String)

class FoldersActivity : AppCompatActivity() {

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var breadcrumbAdapter: BreadcrumbAdapter
    private val filesList = mutableListOf<File>()
    private val breadcrumbList = mutableListOf<File>()
    
    // All audio file paths from MediaStore
    private val allAudioPaths = mutableListOf<String>()
    
    // Initial Path - start at external storage root
    private var currentPath: File = Environment.getExternalStorageDirectory()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)

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
        findViewById<android.view.View>(R.id.nav_home).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_tracks).setOnClickListener {
            startActivity(Intent(this, TracksActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_albums).setOnClickListener {
            startActivity(Intent(this, AlbumsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_artists).setOnClickListener {
            startActivity(Intent(this, ArtistsActivity::class.java))
            overridePendingTransition(0, 0)
        }
        findViewById<android.view.View>(R.id.nav_playlist).setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
            overridePendingTransition(0, 0)
        }
        
        // Check Permissions
        checkPermissionsAndLoad()
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
                    val subdirName = relativePath.substring(0, firstSlash)
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
            if (filesList.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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

    override fun onBackPressed() {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        
        if (currentPath.absolutePath != rootPath && currentPath.parentFile != null) {
            val parent = currentPath.parentFile
            if (parent != null && parent.absolutePath.startsWith(rootPath)) {
                loadFiles(parent)
            } else {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }
}
