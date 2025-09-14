package com.niscp.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.niscp.app.databinding.ActivityShareReceiverBinding
import com.niscp.app.service.ImageUploadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ShareReceiverActivity : ComponentActivity() {
    
    private lateinit var binding: ActivityShareReceiverBinding
    private var imageUris: List<Uri> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        handleIntent(intent)
        setupUI()
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    imageUris = listOf(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (uris != null) {
                    imageUris = uris
                }
            }
        }
        
        updateImageCount()
    }
    
    private fun setupUI() {
        binding.uploadButton.setOnClickListener {
            uploadImages()
        }
        
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun updateImageCount() {
        val count = imageUris.size
        binding.imageCountText.text = if (count == 1) {
            "1 image selected"
        } else {
            "$count images selected"
        }
    }
    
    private fun uploadImages() {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_selected), Toast.LENGTH_SHORT).show()
            return
        }
        
        val filenamePrefix = binding.filenamePrefixEditText.text.toString().trim()
        
        // Show loading state
        binding.uploadButton.isEnabled = false
        binding.uploadButton.text = getString(R.string.preparing_images)
        
        lifecycleScope.launch {
            try {
                val tempUris = copyImagesToTempLocation(imageUris)
                
                val intent = Intent(this@ShareReceiverActivity, ImageUploadService::class.java).apply {
                    action = ImageUploadService.ACTION_UPLOAD_IMAGES
                    putParcelableArrayListExtra(ImageUploadService.EXTRA_IMAGE_URIS, ArrayList(tempUris))
                    putExtra(ImageUploadService.EXTRA_FILENAME_PREFIX, filenamePrefix)
                }
                
                startService(intent)
                
                Toast.makeText(this@ShareReceiverActivity, "Upload started in background. Check notification for progress.", Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                binding.uploadButton.isEnabled = true
                binding.uploadButton.text = getString(R.string.upload)
                Toast.makeText(this@ShareReceiverActivity, "Failed to prepare images: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private suspend fun copyImagesToTempLocation(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val tempDir = File(cacheDir, "temp_images")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        val tempUris = mutableListOf<Uri>()
        
        for ((index, uri) in uris.withIndex()) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val tempFile = File(tempDir, "temp_image_${index}_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(tempFile)
                    
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    tempUris.add(Uri.fromFile(tempFile))
                } else {
                    throw Exception("Could not open input stream for URI: $uri")
                }
            } catch (e: Exception) {
                android.util.Log.e("ShareReceiverActivity", "Failed to copy image $index: ${e.message}", e)
                throw Exception("Failed to copy image $index: ${e.message}")
            }
        }
        
        tempUris
    }
}
