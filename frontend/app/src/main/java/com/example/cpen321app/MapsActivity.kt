package com.example.cpen321app

import FirebaseMessagingService
import android.Manifest
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import com.example.cpen321app.BuildConfig.MAPS_API_KEY
import com.example.cpen321app.MainActivity.Companion.getOkHttpClientWithCustomCert
import com.example.cpen321app.TaskAdapter.Companion._geofenceStateMap
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.example.cpen321app.TaskViewModel.Companion.server_ip
import com.example.cpen321app.databinding.FragmentMapsBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.atan2

open class MapsFragment : Fragment(), OnMapReadyCallback {
    private lateinit var locationManager: LocationManager
    private val TAG = "MapsFragment"
    lateinit var mMap: GoogleMap
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper()) // Runs on main thread
    private lateinit var geofenceupdateRunnable: Runnable
    private lateinit var geofencealertRunnable: Runnable
    internal val polygonMap = mutableMapOf<String, Polygon>()

    companion object {
        var User_Lat: Double = 0.0
        var User_Lng: Double = 0.0
    }

    private var coordListToDraw: ArrayList<LatLng>? = null

    // Holds geofence coordinate lists keyed by task ID.
    protected val geofence_Map = MutableLiveData<MutableMap<String, MutableList<LatLng>>>()

    // Lazy initialization of fusedLocationClient.
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startGlobalLocationUpdates(requireContext())
        coordListToDraw = arguments?.getParcelableArrayList("coordList")
        Log.d("coordListToDraw", coordListToDraw.toString())
    }

    private val periodicLocationHandler = Handler(Looper.getMainLooper())
    private val periodicLocationRunnable = object : Runnable {
        override fun run() {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    User_Lat = location.latitude
                    User_Lng = location.longitude
                    Log.d("PeriodicUpdate", "Updated: $User_Lat, $User_Lng")
                }
            }
            periodicLocationHandler.postDelayed(this, 10000) // every 10 seconds
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            MapsFragment.User_Lat = location.latitude
            MapsFragment.User_Lng = location.longitude
            Log.d("MainActivity", "Updated location: ${location.latitude}, ${location.longitude}")
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun startGlobalLocationUpdates(context: Context) {
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val provider = when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                LocationManager.GPS_PROVIDER
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider != null) {
            locationManager.requestLocationUpdates(
                provider,
                2000L, // every 2 seconds
                1f,    // every 1 meter
                locationListener,
                Looper.getMainLooper()
            )
        } else {
            Log.e("MapsFragment", "Location permission not granted.")
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)

        geofenceupdateRunnable = object : Runnable {
            override fun run() {
                val activeGeofences = _geofenceStateMap.filterValues { it }
                val pendingRequests = activeGeofences.size
                var completedRequests = 0

                // Remove polygons that are no longer active.
                polygonMap.keys.toList().forEach { key ->
                    if (key !in activeGeofences.keys) {
                        polygonMap[key]?.remove()
                        polygonMap.remove(key)
                    }
                }
                if (pendingRequests > 0) {
                    for (taskid in activeGeofences.keys) {
                        val task = _taskList.value?.find { it.id == taskid }
                        val taskLocation = task?.let { LatLng(it.location_lat, task.location_lng) }
                        if (taskLocation != null) {
                            sendFetchGeofencesRequest(taskLocation, taskLocation, task.id, task.name) {
                                Log.d(TAG, "Task ${task.id} completed.")
                                completedRequests++
                                if (completedRequests == pendingRequests) {
                                    Log.d(TAG, "All geofence requests completed. Scheduling next run.")
                                    handler.postDelayed(this, 300000)
                                }
                            }
                        }
                    }
                } else {
                    handler.postDelayed(this, 300000)
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
                                val task = _taskList.value?.find { it.id == task_id }
                                if (task != null) {
                                    messagingService.sendPersistentNotification(requireContext(),
                                        "GeoFence Alert",
                                        "Task [${task.name}] is close"
                                    )
                                }
                            }
                        }
                    }
                }
                handler.postDelayed(this, 10000)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//        val mapFragment =
//            childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
//        mapFragment?.getMapAsync(this)
        super.onViewCreated(view, savedInstanceState)

        val existingMapFragment = childFragmentManager.findFragmentByTag("map_tag")
                as? SupportMapFragment

        val mapFragment = existingMapFragment ?: SupportMapFragment.newInstance().also {
            childFragmentManager.beginTransaction()
                .replace(R.id.map, it, "map_tag")
                .commit()
        }



        mapFragment.getMapAsync(this)
    }

    var onMapReadyCallback: (() -> Unit)? = null

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnMapLoadedCallback {
            coordListToDraw?.let {
                Log.d("MapsFragment", "Drawing route with ${it.size} points")
                drawRouteOnMap(it)
            }
        }

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isZoomGesturesEnabled = true
        locationManager = requireContext().getSystemService(LocationManager::class.java)
        startLocationUpdates()
        if (hasLocationPermission()) {
            startLocationUpdates()
            try {
                mMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    Log.d(TAG, "userLatLng: $userLatLng")

//                    val routePoints = listOf(
//                        LatLng(49.2827, -123.1207), // Vancouver
//                        LatLng(48.6062, -122.3321),
//                        LatLng(47.6062, -122.3321)  // Seattle
//                    )
//
//                    fetchAndDrawRouteFromPoints(routePoints)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                }
            }
        } else {
            showDefaultLocation()
        }

        // Add markers for each task.
        val taskViewModel = (requireActivity().application as GeoTask).taskViewModel
        taskViewModel.taskList.value?.forEach { task ->
            if (task.location_lat != 0.0 && task.location_lng != 0.0) {
                val taskLatLng = LatLng(task.location_lat, task.location_lng)
                mMap.addMarker(
                    MarkerOptions()
                        .position(taskLatLng)
                        .title(task.name)
                )
            }
        }

        onMapReadyCallback?.invoke()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Fragment Resumed - Starting background tasks")
        handler.post(geofenceupdateRunnable)
        handler.post(geofencealertRunnable)
        periodicLocationHandler.post(periodicLocationRunnable)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Fragment Paused - Stopping background tasks")
        handler.removeCallbacks(geofenceupdateRunnable)
        handler.removeCallbacks(geofencealertRunnable)
        periodicLocationHandler.removeCallbacks(periodicLocationRunnable)
    }

    private fun startLocationUpdates() {
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

    private fun sendFetchGeofencesRequest(
        origin: LatLng,
        destination: LatLng,
        taskid: String,
        taskname: String,
        onComplete: () -> Unit
    ) {
        // val client = OkHttpClient()
        val client = getOkHttpClientWithCustomCert(requireContext())
        val url = "https://${server_ip}/fetchGeofences" // Replace with your server URL
        Log.d(TAG, "sendFetchGeofencesRequest")
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
                val retryDelay = 2000L
                Log.d(TAG, "Retrying fetchRoute request in $retryDelay ms... ")
                Handler(Looper.getMainLooper()).postDelayed({
                    sendFetchGeofencesRequest(origin, destination, taskid, taskname, onComplete)
                }, retryDelay)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    val resultJson = JSONObject(jsonResponse)
                    val polygonJson = resultJson.getJSONArray("polygon")
                    val polygonPoints = mutableListOf<LatLng>()
                    for (i in 0 until polygonJson.length()) {
                        val pointObj = polygonJson.getJSONObject(i)
                        val lat = pointObj.getDouble("latitude")
                        val lng = pointObj.getDouble("longitude")
                        polygonPoints.add(LatLng(lat, lng))
                    }
                    Log.d(TAG, "polygonPoints response: ${polygonPoints.size}")
                    if (isAdded && isVisible) {
                        requireActivity().runOnUiThread {
                            if (polygonMap.containsKey(taskid)) {
                                var polygon = polygonMap[taskid]
                                polygon?.remove()
                                polygonMap.remove(taskid)
                                polygon = drawPolygon(polygonPoints)
                                if (polygon != null) {
                                    polygonMap[taskid] = polygon
                                }
                            } else {
                                val polygon = drawPolygon(polygonPoints)
                                if (polygon != null) {
                                    polygonMap[taskid] = polygon
                                }
                            }
                        }
                    }
                    onComplete()
                }
            }
        })
    }

    fun addGeofence(taskId: String, taskname: String, pointsList: MutableList<LatLng>) {
        val updatedMap = geofence_Map.value ?: mutableMapOf()
        updatedMap[taskId] = pointsList
        geofence_Map.postValue(updatedMap)
    }

    private val usedColors = mutableSetOf<Int>()

    private fun drawRoute(encodedPolyline: String, fiveMinPoint: LatLng) {
        val routeColor = getRandomColor()
        val polylineOptions = PolylineOptions()
            .addAll(PolyUtil.decode(encodedPolyline))
            .width(8f)
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

    private fun update_taskListMarkers() {
        _taskList.value?.forEach { task ->
            drawMarker(LatLng(task.location_lat, task.location_lng), task.name)
        }
    }

    private fun drawMarker(point: LatLng, markerName: String) {
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

    // Updated drawPolygon function: Do not add the duplicate first point.
    open fun drawPolygon(points: List<LatLng>): Polygon? {
        Log.d(TAG, "drawPolygon: ${points.size}")
        if (points.size < 3) return null
        val center = computeCentroid(points)
        val sortedPoints = points.sortedWith(
            compareBy { atan2(it.latitude - center.latitude, it.longitude - center.longitude) }
        )
        // Instead of adding a duplicate first point, we assume the polygon is automatically closed.
        val polygonOptions = PolygonOptions()
            .addAll(sortedPoints)
            .strokeColor(Color.RED)
            .fillColor(0x33FF0000)
            .strokeWidth(5f)
        return mMap.addPolygon(polygonOptions)
    }

    protected open fun isPointInsideGeofence(point: LatLng, geofencePolygon: List<LatLng>): Boolean {
        return PolyUtil.containsLocation(point, geofencePolygon, true)
    }

    fun drawRouteOnMap(points: List<LatLng>, color: Int = Color.BLUE, width: Float = 8f) {
        if (!::mMap.isInitialized || points.size < 2) {
            Log.w("drawRouteOnMap", "Map not ready or insufficient points.")
            return
        }
        Log.d("drawRouteOnMap", points.toString())
        val polylineOptions = PolylineOptions()
            .addAll(points)
            .color(color)
            .width(width)
            .geodesic(true)

        mMap.addPolyline(polylineOptions)

        // Optionally move camera to start of route
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
    }

    fun fetchAndDrawRouteFromPoints(points: List<LatLng>, retryCount: Int = 3) {
        if (points.size < 2) {
            Log.w("fetchAndDrawRoute", "At least 2 points are required.")
            return
        }

        val origin = points.first()
        val destination = points.last()
        val waypoints = points.subList(1, points.size - 1)

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        val waypointsStr = waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }

        val urlBuilder = StringBuilder("https://maps.googleapis.com/maps/api/directions/json?")
            .append("origin=$originStr")
            .append("&destination=$destStr")
            .append("&key=$MAPS_API_KEY")

        if (waypoints.isNotEmpty()) {
            val encodedWaypoints = URLEncoder.encode(waypointsStr, "UTF-8")
            urlBuilder.append("&waypoints=$encodedWaypoints")
        }

        val url = urlBuilder.toString()
        Log.d("DirectionsAPI", "Request URL: $url")

        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Connection", "close") // Force connection reset if needed
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DirectionsAPI", "Failed to fetch route: ${e.message}")
                if (retryCount > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        fetchAndDrawRouteFromPoints(points, retryCount - 1)
                    }, 10000) // Retry faster
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    Log.e("DirectionsAPI", "Empty or bad response")
                    return
                }

                try {
                    val json = JSONObject(body)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val overviewPolyline = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points")

                        val decodedPoints = PolyUtil.decode(overviewPolyline)

                        requireActivity().runOnUiThread {
                            Log.d("DirectionsAPI", "Decoded points: $decodedPoints")
                            drawRouteOnMap(decodedPoints)
                        }
                    } else {
                        Log.e("DirectionsAPI", "No route found.")
                    }
                } catch (e: JSONException) {
                    Log.e("DirectionsAPI", "Parsing error: ${e.message}")
                } catch (e: IllegalStateException) {
                    Log.e("DirectionsAPI", "Parsing error: ${e.message}")
                }
            }
        })
    }



}
