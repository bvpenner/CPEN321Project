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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.math.*

/**
 * RouteWorker fetches the current location, retrieves tasks from the backend,
 * orders them using a simple nearest-neighbor algorithm, builds a Google Maps URL,
 * and displays a notification that launches Google Maps when tapped.
 */
class RouteWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "RouteWorker"
        private const val SERVER_IP = "18.215.238.145:3000" // Replace with your backend server IP or domain.
    }

    override fun doWork(): Result {
        // Check location permissions.
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permissions not granted.")
            return Result.failure()
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location: Location = Tasks.await(fusedLocationClient.lastLocation)
            if (location != null) {
                // Fetch tasks from the backend.
                fetchUserTasks { tasks ->
                    if (tasks.isNotEmpty()) {
                        // Order tasks using a simple nearest-neighbor algorithm.
                        val orderedTasks = orderTasksByNearestNeighbor(location.latitude, location.longitude, tasks)
                        // Build a Google Maps URL for the route.
                        val mapsUrl = buildGoogleMapsUrl(location.latitude, location.longitude, orderedTasks)
                        if (mapsUrl.isNotEmpty()) {
                            // Show a notification with an "Accept" action that launches Google Maps.
                            showRouteNotification(applicationContext, mapsUrl)
                        } else {
                            Log.e(TAG, "Generated Google Maps URL is empty.")
                        }
                    } else {
                        Log.e(TAG, "No tasks received from backend.")
                    }
                }
                Result.success()
            } else {
                Log.d(TAG, "No location available; retrying later.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in RouteWorker: ${e.message}")
            Result.failure()
        }
    }

    // Fetch tasks from the backend using the existing /getAllTasks endpoint.
    private fun fetchUserTasks(onResult: (List<Task>) -> Unit) {
        val client = OkHttpClient()
        val url = "http://$SERVER_IP/getAllTasks"
        val jsonBody = JSONObject().apply {
            put("u_id", SessionManager.u_id) // Ensure SessionManager.u_id is set after sign-in.
        }
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch tasks: ${e.message}")
                onResult(emptyList())
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { jsonResponse ->
                        val tasks = parseTasksFromResponse(jsonResponse)
                        onResult(tasks)
                    } ?: onResult(emptyList())
                } else {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    onResult(emptyList())
                }
            }
        })
    }

    // Parse the JSON response into a list of Task objects.
    private fun parseTasksFromResponse(jsonResponse: String): List<Task> {
        val tasks = mutableListOf<Task>()
        try {
            val resultJson = JSONObject(jsonResponse)
            val taskListJsonArray = resultJson.getJSONArray("task_list")
            for (i in 0 until taskListJsonArray.length()) {
                val taskJson = taskListJsonArray.getJSONObject(i)
                val task = Task(
                    id = taskJson.getString("_id"),
                    name = taskJson.getString("name"),
                    start = taskJson.getString("start"),
                    end = taskJson.getString("end"),
                    duration = taskJson.getInt("duration"),
                    location_lat = taskJson.getDouble("location_lat"),
                    location_lng = taskJson.getDouble("location_lng"),
                    priority = taskJson.getInt("priority"),
                    description = taskJson.getString("description")
                )
                tasks.add(task)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tasks: ${e.message}")
        }
        return tasks
    }

    // Compute the distance between two points using the Haversine formula.
    private fun computeDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371e3 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    // Order tasks based on proximity from the current location using a nearest-neighbor algorithm.
    fun orderTasksByNearestNeighbor(currentLat: Double, currentLng: Double, tasks: List<Task>): List<Task> {
        val remaining = tasks.toMutableList()
        val ordered = mutableListOf<Task>()
        var currentLatVar = currentLat
        var currentLngVar = currentLng

        while (remaining.isNotEmpty()) {
            val nearest = remaining.minByOrNull { computeDistance(currentLatVar, currentLngVar, it.location_lat, it.location_lng) }!!
            ordered.add(nearest)
            remaining.remove(nearest)
            currentLatVar = nearest.location_lat
            currentLngVar = nearest.location_lng
        }
        return ordered
    }

    // Build a Google Maps URL from the origin through waypoints to the destination.
    private fun buildGoogleMapsUrl(originLat: Double, originLng: Double, orderedTasks: List<Task>): String {
        if (orderedTasks.isEmpty()) return ""
        val destination = orderedTasks.last()
        val waypoints = orderedTasks.dropLast(1).joinToString(separator = "|") {
            "${it.location_lat},${it.location_lng}"
        }
        return "https://www.google.com/maps/dir/?api=1" +
                "&origin=$originLat,$originLng" +
                "&destination=${destination.location_lat},${destination.location_lng}" +
                if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else ""
    }

    // Display a notification that launches Google Maps with the computed route.
    fun showRouteNotification(context: Context, mapsUrl: String) {
        // For Android 13+, ensure POST_NOTIFICATIONS permission is granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted.")
                return
            }
        }
        val channelId = "task_route_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Task Route Notifications", NotificationManager.IMPORTANCE_HIGH)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        // Create an intent to open Google Maps with the generated route.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
            setPackage("com.google.android.apps.maps")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your notification icon if available.
            .setContentTitle("New Route Available")
            .setContentText("Tap to view your optimized task route.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.mipmap.ic_launcher, "Accept", pendingIntent) // Replace with your accept icon if available.
            .build()
        NotificationManagerCompat.from(context).notify(1001, notification)
    }
}
