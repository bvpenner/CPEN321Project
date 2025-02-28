package com.example.cpen321app

import FirebaseMessagingService
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.example.cpen321app.databinding.FragmentMapsBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.math.atan2
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData

import com.example.cpen321app.TaskViewModel.Companion.server_ip
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.google.android.gms.maps.model.Polygon
import java.util.concurrent.Executors

class MapsFragment : Fragment(), OnMapReadyCallback {
    private lateinit var locationManager: LocationManager
    private var TAG = "MapsFragment"
    private lateinit var mMap: GoogleMap
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper()) // Runs on main thread
    private lateinit var geofenceupdateRunnable: Runnable
    private lateinit var geofencealertRunnable: Runnable

    companion object {
        var User_Lat: Double = 0.0
        var User_Lng: Double = 0.0
    }

//    private val geofence_Map = MutableLiveData<MutableMap<String, Geofence_Container>>()
    private val geofence_Map = MutableLiveData<MutableMap<String, MutableList<LatLng>>>()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }


    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)

        geofenceupdateRunnable = object : Runnable {
            override fun run() {
                val firstTask = _taskList.value?.firstOrNull()
                if (firstTask != null) {
                    val firstTaskLocation = LatLng(firstTask.location_lat, firstTask.location_lng)

                    sendFetchGeofencesRequest(firstTaskLocation, firstTaskLocation, firstTask.id, firstTask.name) {
                        handler.post {
                            mMap.addMarker(MarkerOptions().position(firstTaskLocation).title(firstTask.name))
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstTaskLocation, 14f))
                        }
                        handler.postDelayed(this, 30000) // ✅ Reschedule
                    }
                } else {
                    handler.postDelayed(this, 20000) // ✅ Reschedule
                }
            }
        }

        geofencealertRunnable = object : Runnable {
            override fun run() {
                val geofenceMap = geofence_Map.value
                if (geofenceMap != null) {
                    geofenceMap.forEach { (task_id, geofenceContainer) ->
                        val user_current_loc = LatLng(User_Lat, User_Lng)
                        val in_fence = isPointInsideGeofence(user_current_loc, geofenceContainer)

                        if (in_fence) {
                            handler.post {
                                val messagingService = FirebaseMessagingService()
                                messagingService.sendNotification(requireContext(), "Task [${task_id}] is close")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Geofence map is null")
                }
                handler.postDelayed(this, 10000)
            }
        }


        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        locationManager = requireContext().getSystemService(LocationManager::class.java)

        // Enable MyLocation layer if permissions are granted.
        if (hasLocationPermission()) {
            startLocationUpdates()
            try {
                mMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            // Optionally, move the camera to the user's last known location.
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    Log.d(TAG,"userLatLng: $userLatLng")
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        } else {
            // If permissions not granted, fall back to a default location.
            showDefaultLocation()
        }

        // Retrieve the TaskViewModel from the Application (GeoTask) and add markers for each task.
        val taskViewModel = (requireActivity().application as GeoTask).taskViewModel
        taskViewModel.taskList.value?.forEach { task ->
            // Only add a marker if the task has a valid location.
            if (task.location_lat != 0.0 && task.location_lng != 0.0) {
                val taskLatLng = LatLng(task.location_lat, task.location_lng)
                mMap.addMarker(
                    MarkerOptions()
                        .position(taskLatLng)
                        .title(task.name)
                )
            }
        }

    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Fragment Resumed - Starting background tasks")
        handler.post(geofenceupdateRunnable)
        handler.post(geofencealertRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Fragment Paused - Stopping background tasks")
        handler.removeCallbacks(geofenceupdateRunnable)
        handler.removeCallbacks(geofencealertRunnable)
    }



    private fun startLocationUpdates() {
        try {
            val locationProvider = when {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> LocationManager.GPS_PROVIDER

                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> LocationManager.NETWORK_PROVIDER

                else -> null
            }

            if (locationProvider != null) {
                locationManager.requestLocationUpdates(
                    locationProvider,
                    0L,
                    0f,
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            User_Lat = location.latitude
                            User_Lng = location.longitude
                        }

                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    },
                    Looper.getMainLooper()
                )
            } else {
                Log.e("MainActivity", "No location permission granted.")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error fetching location updates: ${e.message}")
        }
    }


    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showDefaultLocation() {
        val defaultLocation = LatLng(-34.0, 151.0)
        mMap.clear()
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        update_taskListMarkers()

        val firstTask = _taskList.value?.firstOrNull()
        firstTask?.let {
            val firstTasklocation = LatLng(firstTask.location_lat, firstTask.location_lng)
            mMap.addMarker(MarkerOptions().position(firstTasklocation).title(firstTask.name))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(firstTasklocation, 14f))
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendFetchGeofencesRequest(origin: LatLng, destination: LatLng, taskid: String, taskname: String, onComplete: () -> Unit) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/fetchGeofences" // Replace with your server URL
        Log.d(TAG,"sendFetchGeofencesRequest")
        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("origin", JSONObject().apply {
                put("latitude", origin.latitude)
                put("longitude", origin.longitude)
            })
            put("destination", JSONObject().apply {
                put("latitude", destination.latitude)
                put("longitude", destination.longitude)
            })
        }
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send fetchRoute request: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
//                    Log.d(TAG, "Received response: $jsonResponse")
                    // Parse jsonResponse here and extract routes and polygon coordinates.
                    // For example:
                    val resultJson = JSONObject(jsonResponse)
                    // Log.d(TAG, "Received response: $resultJson")
                    val polygonJson = resultJson.getJSONArray("polygon")

                    val polygonPoints = mutableListOf<LatLng>()
                    for (i in 0 until polygonJson.length()) {
                        val pointObj = polygonJson.getJSONObject(i)
                        val lat = pointObj.getDouble("latitude")
                        val lng = pointObj.getDouble("longitude")
                        polygonPoints.add(LatLng(lat, lng))
                    }
                    Log.d(TAG, "polygonPoints response: ${polygonPoints.size}")


                    addGeofence(taskid, taskname, polygonPoints)
                    requireActivity().runOnUiThread {
                        var polygon = drawPolygon(polygonPoints)
                    }


                    onComplete()
                }
            }
        })
    }

    fun addGeofence(taskId: String, taskname: String, pointsList: MutableList<LatLng>) {
        val updatedMap = geofence_Map.value ?: mutableMapOf()

//        if (updatedMap.containsKey(taskId)) {
//            Log.d(TAG, "Updating existing geofence for Task ID: $taskId")
//            updatedMap[taskId]?.currentPolygon?.remove()
//        }

        updatedMap[taskId] = pointsList
        geofence_Map.postValue(updatedMap)
    }

    private val usedColors = mutableSetOf<Int>()

    private fun drawRoute(encodedPolyline: String, fiveMinPoint: LatLng) {
        val routeColor = getRandomColor()
        val polylineOptions = PolylineOptions()
            .addAll(PolyUtil.decode(encodedPolyline)) // Decode the polyline string
            .width(8f) // Set line width
            .color(routeColor)
            .geodesic(true)

        mMap.addPolyline(polylineOptions)

        val markerHue = getMarkerHue(routeColor)
        mMap.addMarker(
            MarkerOptions()
                .position(fiveMinPoint)
                .title("5-Minute Away Point")
                .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
        )
    }

    private fun update_taskListMarkers(){
        for (item in _taskList.value!!) {
            drawMarker(LatLng(item.location_lat, item.location_lng), item.name)
        }
    }

    private fun drawMarker(point: LatLng, markerName: String){
        mMap.addMarker(
            MarkerOptions()
                .position(point)
                .title(markerName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun getMarkerHue(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[0]
    }

    private fun getRandomColor(): Int {
        val colorList = listOf(
            Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
            Color.MAGENTA, Color.BLACK, Color.DKGRAY
        )

        if (usedColors.size >= colorList.size) {
            usedColors.clear()
        }

        val availableColors = colorList.filter { it !in usedColors }

        if (availableColors.isEmpty()) {
            return Color.BLACK
        }

        val newColor = availableColors.random()
        usedColors.add(newColor)
        return newColor
    }

    private fun computeCentroid(points: List<LatLng>): LatLng {
        val latSum = points.sumOf { it.latitude }
        val lngSum = points.sumOf { it.longitude }
        return LatLng(latSum / points.size, lngSum / points.size)
    }

    private fun drawPolygon(points: List<LatLng>): Polygon? {
        Log.d(TAG, "drawPolygon: ${points.size}")
        if (points.size < 3) return null

        val center = computeCentroid(points)

        val sortedPoints = points.sortedWith(
            compareBy { atan2(it.latitude - center.latitude, it.longitude - center.longitude) }
        )

        val closedPolygon = sortedPoints + sortedPoints.first()

        val polygonOptions = PolygonOptions()
            .addAll(closedPolygon)
            .strokeColor(Color.RED)
            .fillColor(0x33FF0000)
            .strokeWidth(5f)

        var currentPolygon = mMap.addPolygon(polygonOptions)
        return currentPolygon
    }

    private fun isPointInsideGeofence(point: LatLng, geofencePolygon: List<LatLng>): Boolean {
        return PolyUtil.containsLocation(point, geofencePolygon, true)
    }

}
