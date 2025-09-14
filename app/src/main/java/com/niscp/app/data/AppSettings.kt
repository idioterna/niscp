package com.niscp.app.data

data class AppSettings(
    val hostname: String = "",
    val port: Int = 22,
    val username: String = "",
    val remoteDirectory: String = "",
    val urlPrefix: String = "",
    val useOriginalSize: Boolean = true,
    val customWidth: Int = 1920,
    val privateKey: String = "",
    val publicKey: String = ""
)
