package com.niscp.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.niscp.app.MainActivity
import com.niscp.app.R
import com.niscp.app.data.SettingsRepository
import com.niscp.app.data.UploadResult
import com.niscp.app.util.ImageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class ImageUploadService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "upload_channel"
        const val ACTION_UPLOAD_IMAGES = "com.niscp.app.UPLOAD_IMAGES"
        
        const val EXTRA_IMAGE_URIS = "image_uris"
        const val EXTRA_FILENAME_PREFIX = "filename_prefix"
    }
    
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var imageProcessor: ImageProcessor
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        imageProcessor = ImageProcessor(this)
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("ImageUploadService", "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_UPLOAD_IMAGES -> {
                @Suppress("DEPRECATION")
                val imageUris = intent.getParcelableArrayListExtra<android.net.Uri>(EXTRA_IMAGE_URIS)
                val filenamePrefix = intent.getStringExtra(EXTRA_FILENAME_PREFIX)
                
                android.util.Log.d("ImageUploadService", "Received ${imageUris?.size ?: 0} images for upload")
                
                if (imageUris != null && imageUris.isNotEmpty()) {
                    // Start as foreground service with initial notification
                    android.util.Log.d("ImageUploadService", "Starting foreground service with notification")
                    startForeground(NOTIFICATION_ID, createInitialNotification())
                    
                    serviceScope.launch {
                        uploadImages(imageUris, filenamePrefix)
                    }
                } else {
                    android.util.Log.w("ImageUploadService", "No images received in intent")
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.upload_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.upload_notification_channel_description)
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createInitialNotification(): android.app.Notification {
        android.util.Log.d("ImageUploadService", "Creating initial notification")
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.upload_notification_title))
            .setContentText("Starting upload...")
            .setSmallIcon(R.drawable.ic_cloud_off)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, true) // Indeterminate progress initially
            .setOngoing(true)
            .build()
            
        android.util.Log.d("ImageUploadService", "Notification created successfully")
        return notification
    }
    
    private suspend fun uploadImages(imageUris: List<android.net.Uri>, filenamePrefix: String?) {
        val settings = settingsRepository.settings.first()
        
        if (settings.hostname.isEmpty() || settings.username.isEmpty() || settings.privateKey.isEmpty()) {
            updateNotification("Upload failed: Missing configuration", 0, 1)
            serviceScope.launch {
                kotlinx.coroutines.delay(2000)
                stopSelf()
            }
            return
        }
        
        val totalImages = imageUris.size
        val uploadResults = mutableListOf<UploadResult>()
        
        // Process and upload each image
        for ((index, uri) in imageUris.withIndex()) {
            try {
                android.util.Log.d("ImageUploadService", "Processing image ${index + 1} of $totalImages: $uri")
                
                // Update notification
                updateNotification(
                    "Processing image ${index + 1} of $totalImages",
                    index * 2, // Processing is step 1 of 2 for each image
                    totalImages * 2
                )
                
                // Process image
                val processedImage = imageProcessor.processImage(
                    uri,
                    settings.useOriginalSize,
                    settings.customWidth,
                    filenamePrefix,
                    isMultipleImages = totalImages > 1
                )
                
                // Update notification
                updateNotification(
                    "Uploading image ${index + 1} of $totalImages",
                    index * 2 + 1, // Uploading is step 2 of 2 for each image
                    totalImages * 2
                )
                
                // Upload image
                android.util.Log.d("ImageUploadService", "Uploading image: ${processedImage.newName}")
                val success = uploadImage(processedImage, settings)
                android.util.Log.d("ImageUploadService", "Upload result for ${processedImage.newName}: $success")
                
                // Generate URL if upload was successful
                val url = if (success && settings.urlPrefix.isNotEmpty()) {
                    val urlPrefix = if (settings.urlPrefix.endsWith("/")) {
                        settings.urlPrefix
                    } else {
                        "${settings.urlPrefix}/"
                    }
                    "${urlPrefix}${processedImage.newName}"
                } else null
                
                uploadResults.add(UploadResult(success, processedImage.newName, url))
                android.util.Log.d("ImageUploadService", "Added result: success=$success, url=$url")
                
                // Clean up temporary file
                processedImage.file.delete()
                
            } catch (e: Exception) {
                android.util.Log.e("ImageUploadService", "Failed to process image ${index + 1}: ${e.message}", e)
                uploadResults.add(UploadResult(false, "unknown", null))
            }
        }
        
        // Copy successful URLs to clipboard
        val successfulUrls = uploadResults.filter { it.success && it.url != null }.map { it.url!! }
        android.util.Log.d("ImageUploadService", "Total results: ${uploadResults.size}, Successful: ${successfulUrls.size}")
        android.util.Log.d("ImageUploadService", "Successful URLs: $successfulUrls")
        if (successfulUrls.isNotEmpty()) {
            copyUrlsToClipboard(successfulUrls)
        }
        
        // Final notification
        val successCount = uploadResults.count { it.success }
        val finalMessage = if (successfulUrls.isNotEmpty()) {
            "Upload complete: $successCount/$totalImages successful. URLs copied to clipboard."
        } else {
            "Upload complete: $successCount/$totalImages successful"
        }
        updateNotification(finalMessage, totalImages * 2, totalImages * 2)
        
        // Stop service after a delay to show final notification
        serviceScope.launch {
            kotlinx.coroutines.delay(3000)
            stopSelf()
        }
    }
    
    private fun uploadImage(processedImage: ImageProcessor.ProcessedImage, settings: com.niscp.app.data.AppSettings): Boolean {
        val jsch = JSch()
        var session: Session? = null
        var sftpChannel: ChannelSftp? = null
        
        return try {
            android.util.Log.d("ImageUploadService", "Starting upload for: ${processedImage.newName}")
            jsch.addIdentity("upload", settings.privateKey.toByteArray(), null, null)
            
            session = jsch.getSession(settings.username, settings.hostname, settings.port)
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,ssh-rsa")
            session.connect()
            android.util.Log.d("ImageUploadService", "SSH session connected")
            
            sftpChannel = session.openChannel("sftp") as ChannelSftp
            sftpChannel.connect()
            android.util.Log.d("ImageUploadService", "SFTP channel connected")
            
            val remotePath = if (settings.remoteDirectory.endsWith("/")) {
                "${settings.remoteDirectory}${processedImage.newName}"
            } else {
                "${settings.remoteDirectory}/${processedImage.newName}"
            }
            
            android.util.Log.d("ImageUploadService", "Uploading to remote path: $remotePath")
            sftpChannel.put(FileInputStream(processedImage.file), remotePath)
            android.util.Log.d("ImageUploadService", "Upload completed successfully for: ${processedImage.newName}")
            true
        } catch (e: Exception) {
            android.util.Log.e("ImageUploadService", "Upload failed for ${processedImage.newName}", e)
            e.printStackTrace()
            false
        } finally {
            sftpChannel?.disconnect()
            session?.disconnect()
        }
    }
    
    private fun copyUrlsToClipboard(urls: List<String>) {
        val urlText = urls.joinToString("\n")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Image URLs", urlText)
        clipboard.setPrimaryClip(clip)
    }
    
    private fun updateNotification(text: String, progress: Int, max: Int) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val isComplete = progress >= max
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.upload_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_cloud_off)
            .setContentIntent(pendingIntent)
            .setProgress(max, progress, false)
            .setOngoing(!isComplete) // Only ongoing while in progress
            .setAutoCancel(isComplete) // Auto-cancel when complete
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
