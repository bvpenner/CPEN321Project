package com.example.cpen321app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import kotlin.coroutines.*
import kotlin.math.*

/**
 * RouteWorker fetches the current location, retrieves tasks from the backend,
 * orders them using a simple nearest-neighbor algorithm, builds a Google Maps URL,
 * and displays a notification that launches Google Maps when tapped.
 */
class RouteWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "RouteWorker"
        private const val SERVER_URL = "http://18.215.238.145:3000"
        private const val CHANNEL_ID = "route_optimization_channel"
        private const val NOTIFICATION_ID = 2001
        private const val MAX_WAYPOINTS = 23  // Google Maps limit is 23 waypoints
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermission()) {
                Log.e(TAG, "Location permissions not granted")
                return@withContext Result.failure()
            }

            val location = getCurrentLocation()
            if (location != null) {
                val tasks = fetchUserTasks()
                if (tasks.isNotEmpty()) {
                    val optimizedRoute = optimizeRoute(location, tasks)
                    val mapsUrl = buildGoogleMapsUrl(location, optimizedRoute)
                    showRouteNotification(mapsUrl, optimizedRoute)
                    Result.success()
                } else {
                    Log.d(TAG, "No tasks available for route optimization")
                    Result.success()
                }
            } else {
                Log.w(TAG, "Could not get current location")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Route optimization failed", e)
            Result.failure()
        }
    }

    private suspend fun getCurrentLocation(): Location? = suspendCoroutine { continuation ->
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMaxUpdateDelayMillis(15000)
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

                // Set a timeout
                GlobalScope.launch {
                    delay(15000)
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

    private suspend fun fetchUserTasks(): List<Task> = suspendCoroutine { continuation ->
        val request = Request.Builder()
            .url("$SERVER_URL/getAllTasks")
            .post(
                JSONObject().put("u_id", SessionManager.u_id)
                    .toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch tasks", e)
                continuation.resume(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }

                    val tasks = parseTasksFromResponse(response.body?.string() ?: "{}")
                    continuation.resume(tasks)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing tasks", e)
                    continuation.resume(emptyList())
                } finally {
                    response.close()
                }
            }
        })
    }

    private fun parseTasksFromResponse(jsonResponse: String): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            val json = JSONObject(jsonResponse)
            val taskArray = json.getJSONArray("task_list")
            
            for (i in 0 until taskArray.length()) {
                val taskJson = taskArray.getJSONObject(i)
                tasks.add(
                    Task(
                        id = taskJson.getString("_id"),
                        name = taskJson.getString("name"),
                        description = taskJson.getString("description"),
                        start = taskJson.getString("start"),
                        end = taskJson.getString("end"),
                        duration = taskJson.getDouble("duration"),
                        location_lat = taskJson.getDouble("location_lat"),
                        location_lng = taskJson.getDouble("location_lng"),
                        priority = taskJson.getInt("priority")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON response", e)
        }
        return tasks
    }

    private fun optimizeRoute(startLocation: Location, tasks: List<Task>): List<Task> {
        // Sort tasks by priority and time constraints
        val prioritizedTasks = tasks.sortedWith(
            compareByDescending<Task> { it.priority }
                .thenBy { it.getStartTime() }
        )

        // Use nearest neighbor with time windows
        val route = mutableListOf<Task>()
        val remaining = prioritizedTasks.toMutableList()
        var currentLocation = startLocation
        var currentTime = System.currentTimeMillis()

        while (remaining.isNotEmpty() && route.size < MAX_WAYPOINTS) {
            val nextTask = remaining.minByOrNull { task ->
                val taskLocation = Location("").apply {
                    latitude = task.location_lat
                    longitude = task.location_lng
                }
                
                // Calculate score based on distance, priority, and time window
                val distance = currentLocation.distanceTo(taskLocation)
                val timeToReach = (distance / 13.4).toInt() // Assuming average speed of 13.4 m/s (30 mph)
                val arrivalTime = currentTime + timeToReach * 1000
                
                val timeWindowPenalty = if (arrivalTime > task.getEndTime()) {
                    (arrivalTime - task.getEndTime()) / 1000 // Convert to seconds
                } else 0

                // Score = distance - (priority * 1000) + timeWindowPenalty
                distance - (task.priority * 1000) + timeWindowPenalty
            } ?: break

            route.add(nextTask)
            remaining.remove(nextTask)
            
            currentLocation = Location("").apply {
                latitude = nextTask.location_lat
                longitude = nextTask.location_lng
            }
            currentTime += (nextTask.duration * 3600 * 1000).toLong() // Convert hours to milliseconds
        }

        return route
    }

    private fun buildGoogleMapsUrl(startLocation: Location, tasks: List<Task>): String {
        if (tasks.isEmpty()) return ""

        val origin = "${startLocation.latitude},${startLocation.longitude}"
        val destination = with(tasks.last()) { "$location_lat,$location_lng" }
        
        val waypoints = tasks.dropLast(1)
            .joinToString("|") { "${it.location_lat},${it.location_lng}" }
            .let { if (it.isNotEmpty()) "&waypoints=$it" else "" }

        return "https://www.google.com/maps/dir/?api=1" +
                "&origin=$origin" +
                "&destination=$destination" +
                waypoints +
                "&travelmode=driving"
    }

    private fun showRouteNotification(mapsUrl: String, tasks: List<Task>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
            setPackage("com.google.android.apps.maps")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Optimized Route Available")
            .setContentText("Route with ${tasks.size} tasks has been optimized")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                buildRouteDescription(tasks)
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_navigation,
                "Start Navigation",
                pendingIntent
            )
            .build()

        val notificationManager = ContextCompat.getSystemService(
            applicationContext,
            NotificationManager::class.java
        )
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildRouteDescription(tasks: List<Task>): String {
        return buildString {
            appendLine("Optimized route with ${tasks.size} tasks:")
            tasks.forEachIndexed { index, task ->
                appendLine("${index + 1}. ${task.name} (${task.getPriorityText()})")
            }
            appendLine("\nTap to view and start navigation.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Route Optimization",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for optimized task routes"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = ContextCompat.getSystemService(
                applicationContext,
                NotificationManager::class.java
            )
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
