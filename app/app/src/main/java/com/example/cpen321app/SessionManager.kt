package com.example.cpen321app

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREF_NAME = "GeoTaskPrefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_LAST_LOCATION = "last_location"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_SETTINGS = "user_settings"

    private lateinit var preferences: SharedPreferences
    private val gson = Gson()
    private val isInitialized = AtomicBoolean(false)

    // User data
    var u_id: String? = null
        private set
    var userEmail: String? = null
        private set
    var userName: String? = null
        private set
    var authToken: String? = null
        private set
    var fcmToken: String? = null
        private set
    
    // Location data
    var currentLocation: Location? = null
        set(value) {
            field = value
            value?.let { saveLastLocation(it) }
        }

    // Settings and state
    private var userSettings: MutableMap<String, Any> = mutableMapOf()
    private var lastSyncTimestamp: Long = 0

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) {
            return
        }

        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            preferences = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Load saved data
            loadSessionData()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SessionManager", e)
            // Fallback to regular SharedPreferences if encryption fails
            preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun setUserData(userId: String, email: String, name: String) {
        u_id = userId
        userEmail = email
        userName = name

        preferences.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun setAuthToken(token: String) {
        authToken = token
        preferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun setFCMToken(token: String) {
        fcmToken = token
        preferences.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    private fun saveLastLocation(location: Location) {
        val locationJson = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("time", location.time)
        }.toString()

        preferences.edit().putString(KEY_LAST_LOCATION, locationJson).apply()
    }

    fun updateSetting(key: String, value: Any) {
        userSettings[key] = value
        saveSettings()
    }

    fun getSetting(key: String): Any? = userSettings[key]

    fun updateLastSync() {
        lastSyncTimestamp = System.currentTimeMillis()
        preferences.edit().putLong(KEY_LAST_SYNC, lastSyncTimestamp).apply()
    }

    fun getLastSyncTime(): Long = lastSyncTimestamp

    fun clearSession() {
        u_id = null
        userEmail = null
        userName = null
        authToken = null
        currentLocation = null
        userSettings.clear()
        lastSyncTimestamp = 0

        preferences.edit().clear().apply()
    }

    private fun loadSessionData() {
        u_id = preferences.getString(KEY_USER_ID, null)
        userEmail = preferences.getString(KEY_USER_EMAIL, null)
        userName = preferences.getString(KEY_USER_NAME, null)
        authToken = preferences.getString(KEY_AUTH_TOKEN, null)
        fcmToken = preferences.getString(KEY_FCM_TOKEN, null)
        lastSyncTimestamp = preferences.getLong(KEY_LAST_SYNC, 0)

        // Load last known location
        preferences.getString(KEY_LAST_LOCATION, null)?.let { locationJson ->
            try {
                val json = JSONObject(locationJson)
                currentLocation = Location("last_known").apply {
                    latitude = json.getDouble("latitude")
                    longitude = json.getDouble("longitude")
                    accuracy = json.getFloat("accuracy")
                    time = json.getLong("time")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading last location", e)
            }
        }

        // Load settings
        preferences.getString(KEY_SETTINGS, null)?.let { settingsJson ->
            try {
                @Suppress("UNCHECKED_CAST")
                userSettings = gson.fromJson(settingsJson, Map::class.java) as MutableMap<String, Any>
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
            }
        }
    }

    private fun saveSettings() {
        preferences.edit()
            .putString(KEY_SETTINGS, gson.toJson(userSettings))
            .apply()
    }

    fun isLoggedIn(): Boolean = !u_id.isNullOrEmpty() && !authToken.isNullOrEmpty()

    fun needsSync(): Boolean = System.currentTimeMillis() - lastSyncTimestamp > SYNC_INTERVAL

    companion object {
        private const val SYNC_INTERVAL = 5 * 60 * 1000 // 5 minutes in milliseconds
    }
}
