package com.niscp.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MediaProcessor(private val context: Context) {
    
    data class ProcessedMedia(
        val file: File,
        val originalName: String,
        val newName: String,
        val isVideo: Boolean
    )
    
    suspend fun processMedia(
        uri: Uri,
        useOriginalSize: Boolean,
        customWidth: Int,
        filenamePrefix: String? = null,
        isMultipleFiles: Boolean = false
    ): ProcessedMedia = withContext(Dispatchers.IO) {
        Log.d("MediaProcessor", "Processing media: $uri")
        
        val originalName = getFileName(uri)
        // Extract filename from URI path as fallback
        val filenameFromPath = uri.lastPathSegment ?: "unknown"
        val actualFilename = if (originalName != "unknown") originalName else filenameFromPath
        
        val isVideoByExtension = isVideoFile(actualFilename)
        val isVideoByMime = isVideoMimeType(uri)
        val isVideo = isVideoByExtension || isVideoByMime
        
        Log.d("MediaProcessor", "Original name: $originalName")
        Log.d("MediaProcessor", "Filename from path: $filenameFromPath")
        Log.d("MediaProcessor", "Actual filename: $actualFilename")
        Log.d("MediaProcessor", "Is video by extension: $isVideoByExtension")
        Log.d("MediaProcessor", "Is video by MIME: $isVideoByMime")
        Log.d("MediaProcessor", "Final isVideo: $isVideo")
        
        if (isVideo) {
            // For videos, just copy the file without processing
            processVideo(uri, actualFilename, filenamePrefix, isMultipleFiles)
        } else {
            // For images, process as before
            processImage(uri, useOriginalSize, customWidth, filenamePrefix, isMultipleFiles)
        }
    }
    
    private suspend fun processVideo(
        uri: Uri,
        originalName: String,
        filenamePrefix: String?,
        isMultipleFiles: Boolean
    ): ProcessedMedia = withContext(Dispatchers.IO) {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            Log.e("MediaProcessor", "SecurityException when opening video stream: ${e.message}")
            throw e
        }
        
        if (inputStream == null) {
            throw Exception("Could not open video stream")
        }
        
        val newName = generateVideoFilename(originalName, filenamePrefix, isMultipleFiles)
        val outputFile = File(context.cacheDir, newName)
        
        inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
        
        Log.d("MediaProcessor", "Video processed: $originalName -> $newName")
        ProcessedMedia(outputFile, originalName, newName, true)
    }
    
    private suspend fun processImage(
        uri: Uri,
        useOriginalSize: Boolean,
        customWidth: Int,
        filenamePrefix: String?,
        isMultipleFiles: Boolean
    ): ProcessedMedia = withContext(Dispatchers.IO) {
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            Log.e("MediaProcessor", "SecurityException when opening image stream: ${e.message}")
            throw e
        }
        
        if (inputStream == null) {
            throw Exception("Could not open image stream")
        }
        
        val originalName = getFileName(uri)
        val bitmap = try {
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("MediaProcessor", "Failed to decode bitmap: ${e.message}")
            throw Exception("Failed to decode image: ${e.message}")
        }
        
        if (bitmap == null) {
            throw Exception("Could not decode image")
        }
        
        Log.d("MediaProcessor", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
        
        val processedBitmap = if (useOriginalSize) {
            bitmap
        } else {
            resizeBitmap(bitmap, customWidth)
        }
        
        val rotatedBitmap = rotateBitmapIfNeeded(processedBitmap, uri)
        val compressedBitmap = compressBitmap(rotatedBitmap)
        
        val newName = generateImageFilename(originalName, filenamePrefix, isMultipleFiles)
        val outputFile = File(context.cacheDir, newName)
        
        FileOutputStream(outputFile).use { output ->
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }
        
        Log.d("MediaProcessor", "Image processed: $originalName -> $newName")
        ProcessedMedia(outputFile, originalName, newName, false)
    }
    
    private fun isVideoFile(filename: String): Boolean {
        val videoExtensions = listOf(".mp4", ".avi", ".mov", ".mkv", ".webm", ".3gp", ".flv", ".wmv", ".m4v")
        val lowerFilename = filename.lowercase()
        val isVideo = videoExtensions.any { lowerFilename.endsWith(it) }
        Log.d("MediaProcessor", "isVideoFile: filename='$filename', lowerFilename='$lowerFilename', isVideo=$isVideo")
        return isVideo
    }
    
    private fun isVideoMimeType(uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            Log.d("MediaProcessor", "MIME type for $uri: $mimeType")
            val isVideo = mimeType?.startsWith("video/") == true
            Log.d("MediaProcessor", "Is video MIME: $isVideo")
            isVideo
        } catch (e: Exception) {
            Log.w("MediaProcessor", "Failed to get MIME type: ${e.message}")
            false
        }
    }
    
    private fun generateVideoFilename(originalName: String, filenamePrefix: String?, isMultipleFiles: Boolean): String {
        val baseName = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "")
        
        val prefix = filenamePrefix?.takeIf { it.isNotBlank() } ?: "video"
        val timestamp = System.currentTimeMillis()
        
        return if (isMultipleFiles || filenamePrefix != null) {
            "${prefix}-${timestamp}.${extension}"
        } else {
            "${baseName}-${timestamp}.${extension}"
        }
    }
    
    private fun generateImageFilename(originalName: String, filenamePrefix: String?, isMultipleFiles: Boolean): String {
        val baseName = originalName.substringBeforeLast(".")
        val extension = "jpg" // Always use jpg for processed images
        
        val prefix = filenamePrefix?.takeIf { it.isNotBlank() } ?: "image"
        val timestamp = System.currentTimeMillis()
        
        return if (isMultipleFiles || filenamePrefix != null) {
            "${prefix}-${timestamp}.${extension}"
        } else {
            "${baseName}-${timestamp}.${extension}"
        }
    }
    
    private fun getFileName(uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        it.getString(nameIndex) ?: "unknown"
                    } else {
                        "unknown"
                    }
                } else {
                    "unknown"
                }
            } ?: "unknown"
        } catch (e: SecurityException) {
            Log.w("MediaProcessor", "SecurityException when getting filename: ${e.message}")
            "unknown-${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.w("MediaProcessor", "Exception when getting filename: ${e.message}")
            "unknown-${System.currentTimeMillis()}"
        }
    }
    
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED
                )
                
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            Log.w("MediaProcessor", "Failed to read EXIF data: ${e.message}")
            bitmap
        }
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val compressedByteArray = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(compressedByteArray, 0, compressedByteArray.size)
    }
}
