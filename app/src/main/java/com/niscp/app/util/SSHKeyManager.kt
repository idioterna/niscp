package com.niscp.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.niscp.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

class SSHKeyManager(private val context: Context) {
    
    fun generateKeyPair(): Pair<String, String> {
        // Generate ECDSA key pair using JSch's built-in key generation (more secure than RSA)
        val keyPair = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
        
        // Get private key in JSch format
        val privateKeyStream = ByteArrayOutputStream()
        keyPair.writePrivateKey(privateKeyStream)
        val privateKeyString = privateKeyStream.toString()
        
        // Get public key in OpenSSH format
        val publicKeyStream = ByteArrayOutputStream()
        keyPair.writePublicKey(publicKeyStream, "niscp@android")
        val publicKeyString = publicKeyStream.toString().trim()
        
        // Debug: Log the public key format for verification
        android.util.Log.d("SSHKeyManager", "Generated public key: $publicKeyString")
        
        return Pair(privateKeyString, publicKeyString)
    }
    
    fun copyPublicKeyToClipboard(publicKey: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SSH Public Key", publicKey)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(context, context.getString(R.string.public_key_copied), Toast.LENGTH_SHORT).show()
    }
    
    suspend fun testConnection(hostname: String, port: Int, username: String, privateKey: String): Result<String> {
        return withContext(Dispatchers.IO) {
            var session: Session? = null
            
            try {
                val jsch = JSch()
                jsch.addIdentity("test", privateKey.toByteArray(), null, null)
                
                session = jsch.getSession(username, hostname, port)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setConfig("server_host_key", "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,ssh-rsa")
                session.connect(5000) // 5 second timeout
                session.disconnect()
                
                Result.success("Connection successful")
            } catch (e: Exception) {
                // Provide more detailed error information for debugging
                val errorDetails = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "Connection timeout. Check hostname, port, and network connectivity."
                    e.message?.contains("auth", ignoreCase = true) == true -> 
                        "Authentication failed. Make sure the public key is added to ~/.ssh/authorized_keys on the server. Copy the public key from the app and add it to the server."
                    e.message?.contains("host", ignoreCase = true) == true -> 
                        "Host unreachable. Check hostname and port number."
                    e.message?.contains("key", ignoreCase = true) == true -> 
                        "SSH key error. Verify key format and permissions."
                    else -> 
                        "Connection failed: ${e.message ?: e.javaClass.simpleName}"
                }
                Result.failure(Exception(errorDetails, e))
            } finally {
                try {
                    session?.disconnect()
                } catch (e: Exception) {
                    // Ignore cleanup errors
                }
            }
        }
    }
}