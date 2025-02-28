package com.example.cpen321app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.net.HttpURLConnection
import java.net.URL


class LocationWorker(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "LocationWorker"
    }

    override fun doWork(): Result {
        return try {
            Log.d(TAG, "Doing Work")

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

            if (ActivityCompat.checkSelfPermission(this.applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this.applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Worker Failed Location Permissions Check")
                return Result.failure()
            }

            val location = Tasks.await(fusedLocationClient.lastLocation)

            if(location != null) {
                Log.d(TAG, "Location: Latitude: ${location.latitude}, Longitude: ${location.longitude}")
//                sendLocationToServer(location)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun sendLocationToServer(location: Location) {
        // Replace with your server URL
        val url = URL("https://your-backend-server.com/api/location")

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "POST"
            doOutput = true
            val outputStream = outputStream
            outputStream.write("latitude=${location.latitude}&longitude=${location.longitude}".toByteArray())
            outputStream.flush()
            outputStream.close()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = inputStream.bufferedReader().readText()
                // Handle the response from the server
            }
        }
    }

}