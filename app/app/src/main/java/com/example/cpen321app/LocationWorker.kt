package com.example.cpen321app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class LocationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "LocationWorker"
        private const val SERVER_URL = "http://18.215.238.145:3000/updateLocation"
        private const val LOCATION_INTERVAL = 10000L // 10 seconds
        private const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds
        private const val LOCATION_MAX_WAIT_TIME = 15000L // 15 seconds
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permissions not granted")
                return@withContext Result.failure()
            }

            val location = getCurrentLocation()
            if (location != null) {
                Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                SessionManager.currentLocation = location
                
                try {
                    sendLocationToServer(location)
                    startGeofenceMonitoring(location)
                    Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing location", e)
                    Result.retry()
                }
            } else {
                Log.w(TAG, "No location available")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.failure()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getCurrentLocation(): Location? = suspendCoroutine { continuation ->
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .setMaxUpdateDelayMillis(LOCATION_MAX_WAIT_TIME)
                .build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    continuation.resume(result.lastLocation)
                }
            }

            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    applicationContext.mainLooper
                )

                // Set a timeout to prevent hanging
                GlobalScope.launch {
                    delay(LOCATION_MAX_WAIT_TIME)
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                    if (continuation.context.isActive) {
                        continuation.resume(null)
                    }
                }
            } else {
                continuation.resume(null)
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    private suspend fun sendLocationToServer(location: Location) {
        val jsonBody = JSONObject().apply {
            put("u_id", SessionManager.u_id)
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("accuracy", location.accuracy)
            put("speed", location.speed)
            put("timestamp", location.time)
        }

        val request = Request.Builder()
            .url(SERVER_URL)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to send location to server", e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(
                            IOException("Unexpected response ${response.code}")
                        )
                    } else {
                        continuation.resume(Unit)
                    }
                    response.close()
                }
            })
        }
    }

    private suspend fun startGeofenceMonitoring(location: Location) {
        // Fetch nearby geofences from server based on current location
        val jsonBody = JSONObject().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            put("radius", 5000) // 5km radius
        }

        val request = Request.Builder()
            .url("http://18.215.238.145:3000/getNearbyGeofences")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to fetch geofences", e)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        continuation.resumeWithException(
                            IOException("Unexpected response ${response.code}")
                        )
                        return
                    }

                    try {
                        val geofences = JSONObject(response.body?.string() ?: "{}").getJSONArray("geofences")
                        for (i in 0 until geofences.length()) {
                            val geofence = geofences.getJSONObject(i)
                            val geofenceLocation = Location("").apply {
                                latitude = geofence.getDouble("latitude")
                                longitude = geofence.getDouble("longitude")
                            }

                            if (location.distanceTo(geofenceLocation) <= geofence.getDouble("radius")) {
                                // Notify about entering geofence
                                notifyGeofenceEntered(geofence)
                            }
                        }
                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        response.close()
                    }
                }
            })
        }
    }

    private fun notifyGeofenceEntered(geofence: JSONObject) {
        val taskId = geofence.getString("taskId")
        val notificationService = TaskNotificationService()
        notificationService.sendNotification(
            applicationContext,
            mapOf(
                "type" to "reminder",
                "title" to "Task Nearby",
                "message" to "You are near the location of your task: ${geofence.getString("taskName")}",
                "taskId" to taskId
            )
        )
    }
}