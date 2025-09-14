package com.niscp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.niscp.app.databinding.ActivityMainBinding
import com.niscp.app.service.ImageUploadService
import com.niscp.app.util.SSHKeyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: com.niscp.app.data.SettingsRepository
    private lateinit var sshKeyManager: SSHKeyManager
    private var isServiceRunning = false
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        settingsRepository = com.niscp.app.data.SettingsRepository(this)
        sshKeyManager = SSHKeyManager(this)
        
        setupUI()
        observeSettings()
        updateServiceStatus()
    }
    
    private fun setupUI() {
        // Service control
        binding.startStopServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopService()
            } else {
                startService()
            }
        }
        
        // SSH key management
        binding.generateSshKeyButton.setOnClickListener {
            generateSshKey()
        }
        
        binding.copyPublicKeyButton.setOnClickListener {
            copyPublicKey()
        }
        
        binding.testConnectionButton.setOnClickListener {
            testConnection()
        }
        
        // Permissions
        binding.confirmPermissionsButton.setOnClickListener {
            requestPermissions()
        }
        
        // Image size settings
        binding.imageSizeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            binding.customWidthLayout.visibility = if (checkedId == R.id.customWidthRadioButton) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
        
        // Text change listeners for auto-save
        binding.hostnameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveHostname()
            }
        }
        
        binding.portEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                savePort()
            }
        }
        
        binding.usernameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveUsername()
            }
        }
        
        binding.remoteDirectoryEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveRemoteDirectory()
            }
        }
        
        binding.urlPrefixEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveUrlPrefix()
            }
        }
        
        binding.customWidthEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveImageSizeSettings()
            }
        }
    }
    
    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.settings.collect { settings ->
                binding.hostnameEditText.setText(settings.hostname)
                binding.portEditText.setText(settings.port.toString())
                binding.usernameEditText.setText(settings.username)
                binding.remoteDirectoryEditText.setText(settings.remoteDirectory)
                binding.urlPrefixEditText.setText(settings.urlPrefix)
                
                if (settings.useOriginalSize) {
                    binding.originalSizeRadioButton.isChecked = true
                } else {
                    binding.customWidthRadioButton.isChecked = true
                }
                
                binding.customWidthEditText.setText(settings.customWidth.toString())
                
                binding.customWidthLayout.visibility = if (settings.useOriginalSize) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
            }
        }
    }
    
    private fun startService() {
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            
            if (settings.hostname.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.enter_hostname), Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            if (settings.username.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.enter_username), Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            if (settings.remoteDirectory.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.enter_remote_directory), Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            isServiceRunning = true
            updateServiceStatus()
            Toast.makeText(this@MainActivity, getString(R.string.service_started), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopService() {
        isServiceRunning = false
        updateServiceStatus()
        Toast.makeText(this, getString(R.string.service_stopped_msg), Toast.LENGTH_SHORT).show()
    }
    
    private fun updateServiceStatus() {
        if (isServiceRunning) {
            binding.serviceStatusText.text = getString(R.string.service_running)
            binding.startStopServiceButton.text = getString(R.string.stop_service)
        } else {
            binding.serviceStatusText.text = getString(R.string.service_stopped)
            binding.startStopServiceButton.text = getString(R.string.start_service)
        }
    }
    
    private fun generateSshKey() {
        lifecycleScope.launch {
            try {
                val (privateKey, publicKey) = sshKeyManager.generateKeyPair()
                settingsRepository.updateSshKeys(privateKey, publicKey)
                Toast.makeText(this@MainActivity, getString(R.string.ssh_key_generated), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error generating SSH key: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun copyPublicKey() {
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.publicKey.isEmpty()) {
                Toast.makeText(this@MainActivity, "Please generate SSH key first", Toast.LENGTH_SHORT).show()
                return@launch
            }
            sshKeyManager.copyPublicKeyToClipboard(settings.publicKey)
        }
    }
    
    private fun testConnection() {
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            
            if (settings.hostname.isEmpty() || settings.username.isEmpty() || settings.privateKey.isEmpty()) {
                Toast.makeText(this@MainActivity, "Please configure hostname, username, and generate SSH key", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            try {
                val result = sshKeyManager.testConnection(
                    settings.hostname,
                    settings.port,
                    settings.username,
                    settings.privateKey
                )
                
                result.fold(
                    onSuccess = { message ->
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        val errorMessage = exception.message ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "Connection failed: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Connection test failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveHostname() {
        lifecycleScope.launch {
            settingsRepository.updateHostname(binding.hostnameEditText.text.toString())
        }
    }
    
    private fun savePort() {
        lifecycleScope.launch {
            try {
                val port = binding.portEditText.text.toString().toInt()
                settingsRepository.updatePort(port)
            } catch (e: NumberFormatException) {
                Toast.makeText(this@MainActivity, getString(R.string.invalid_port), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveUsername() {
        lifecycleScope.launch {
            settingsRepository.updateUsername(binding.usernameEditText.text.toString())
        }
    }
    
    private fun saveRemoteDirectory() {
        lifecycleScope.launch {
            settingsRepository.updateRemoteDirectory(binding.remoteDirectoryEditText.text.toString())
        }
    }
    
    private fun saveUrlPrefix() {
        lifecycleScope.launch {
            settingsRepository.updateUrlPrefix(binding.urlPrefixEditText.text.toString())
        }
    }
    
    private fun saveImageSizeSettings() {
        lifecycleScope.launch {
            val useOriginalSize = binding.originalSizeRadioButton.isChecked
            val customWidth = try {
                binding.customWidthEditText.text.toString().toInt()
            } catch (e: NumberFormatException) {
                1920
            }
            settingsRepository.updateImageSizeSettings(useOriginalSize, customWidth)
        }
    }
    
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Check and request READ_MEDIA_IMAGES permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // For older versions, request READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        // Check and request POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // All permissions already granted
            Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
        }
    }
}
