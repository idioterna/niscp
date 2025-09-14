package com.niscp.app.data

data class UploadResult(
    val success: Boolean,
    val filename: String,
    val url: String? = null
)
