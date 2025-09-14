package com.niscp.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class ImageProcessor(private val context: Context) {
    
    data class ProcessedImage(
        val file: File,
        val originalName: String,
        val newName: String
    )
    
    suspend fun processImage(
        uri: Uri,
        useOriginalSize: Boolean,
        customWidth: Int,
        filenamePrefix: String? = null,
        isMultipleImages: Boolean = false
    ): ProcessedImage = withContext(Dispatchers.IO) {
        
        val originalName = getFileName(uri)
        val extension = originalName.substringAfterLast('.', "jpg")
        
        val newName = when {
            // Multiple images: always add timestamp to avoid conflicts
            isMultipleImages -> {
                if (filenamePrefix != null && filenamePrefix.isNotBlank()) {
                    "${filenamePrefix}-${System.currentTimeMillis()}.${extension}"
                } else {
                    "image-${System.currentTimeMillis()}.${extension}"
                }
            }
            // Single image with prefix: add timestamp
            filenamePrefix != null && filenamePrefix.isNotBlank() -> {
                "${filenamePrefix}-${System.currentTimeMillis()}.${extension}"
            }
            // Single image without prefix: use original name
            else -> originalName
        }
        
        android.util.Log.d("ImageProcessor", "Processing image: $uri")
        val inputStream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            android.util.Log.w("ImageProcessor", "Permission denied accessing URI: $uri", e)
            // Try alternative approach for Google Photos URIs
            try {
                val assetFileDescriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                assetFileDescriptor?.createInputStream()
            } catch (e2: Exception) {
                android.util.Log.w("ImageProcessor", "Alternative approach also failed for URI: $uri", e2)
                throw Exception("Permission denied accessing image: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageProcessor", "Failed to open input stream for: $uri", e)
            throw Exception("Failed to open input stream for image: ${e.message}")
        }
        
        if (inputStream == null) {
            android.util.Log.e("ImageProcessor", "Input stream is null for: $uri")
            throw Exception("Input stream is null for image")
        }
        
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (bitmap == null) {
            android.util.Log.e("ImageProcessor", "Failed to decode bitmap for: $uri")
            throw Exception("Failed to decode bitmap for image")
        }
        android.util.Log.d("ImageProcessor", "Successfully decoded bitmap: ${bitmap.width}x${bitmap.height}")
        
        val processedBitmap = if (useOriginalSize) {
            bitmap
        } else {
            resizeBitmap(bitmap, customWidth)
        }
        
        // Apply EXIF rotation if needed
        val rotatedBitmap = applyExifRotation(uri, processedBitmap)
        
        // Save to temporary file
        val tempFile = File(context.cacheDir, newName)
        val outputStream = FileOutputStream(tempFile)
        
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.close()
        
        bitmap.recycle()
        if (rotatedBitmap != bitmap) {
            rotatedBitmap.recycle()
        }
        
        ProcessedImage(tempFile, originalName, newName)
    }
    
    private fun getFileName(uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val name = if (nameIndex != null && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                "image_${System.currentTimeMillis()}.jpg"
            }
            cursor?.close()
            name
        } catch (e: Exception) {
            android.util.Log.w("ImageProcessor", "Failed to get filename from URI: $uri", e)
            "image_${System.currentTimeMillis()}.jpg"
        }
    }
    
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxWidth) {
            return bitmap
        }
        
        val ratio = height.toFloat() / width.toFloat()
        val newHeight = (maxWidth * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }
    
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
