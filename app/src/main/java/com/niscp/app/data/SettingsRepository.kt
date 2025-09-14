package com.niscp.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private val hostnameKey = stringPreferencesKey("hostname")
    private val portKey = intPreferencesKey("port")
    private val usernameKey = stringPreferencesKey("username")
    private val remoteDirectoryKey = stringPreferencesKey("remote_directory")
    private val urlPrefixKey = stringPreferencesKey("url_prefix")
    private val useOriginalSizeKey = stringPreferencesKey("use_original_size")
    private val customWidthKey = intPreferencesKey("custom_width")
    private val privateKeyKey = stringPreferencesKey("private_key")
    private val publicKeyKey = stringPreferencesKey("public_key")

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            hostname = preferences[hostnameKey] ?: "",
            port = preferences[portKey] ?: 22,
            username = preferences[usernameKey] ?: "",
            remoteDirectory = preferences[remoteDirectoryKey] ?: "",
            urlPrefix = preferences[urlPrefixKey] ?: "",
            useOriginalSize = preferences[useOriginalSizeKey]?.toBoolean() ?: true,
            customWidth = preferences[customWidthKey] ?: 1920,
            privateKey = preferences[privateKeyKey] ?: "",
            publicKey = preferences[publicKeyKey] ?: ""
        )
    }

    suspend fun updateHostname(hostname: String) {
        context.dataStore.edit { preferences ->
            preferences[hostnameKey] = hostname
        }
    }

    suspend fun updatePort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[portKey] = port
        }
    }

    suspend fun updateUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[usernameKey] = username
        }
    }

    suspend fun updateRemoteDirectory(remoteDirectory: String) {
        context.dataStore.edit { preferences ->
            preferences[remoteDirectoryKey] = remoteDirectory
        }
    }

    suspend fun updateUrlPrefix(urlPrefix: String) {
        context.dataStore.edit { preferences ->
            preferences[urlPrefixKey] = urlPrefix
        }
    }

    suspend fun updateImageSizeSettings(useOriginalSize: Boolean, customWidth: Int) {
        context.dataStore.edit { preferences ->
            preferences[useOriginalSizeKey] = useOriginalSize.toString()
            preferences[customWidthKey] = customWidth
        }
    }

    suspend fun updateSshKeys(privateKey: String, publicKey: String) {
        context.dataStore.edit { preferences ->
            preferences[privateKeyKey] = privateKey
            preferences[publicKeyKey] = publicKey
        }
    }
}
