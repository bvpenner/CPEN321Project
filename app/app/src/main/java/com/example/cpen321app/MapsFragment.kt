package com.example.cpen321app

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.cpen321app.databinding.FragmentMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MapsFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var map: GoogleMap
    private lateinit var clusterManager: ClusterManager<TaskMarkerItem>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var taskViewModel: TaskViewModel
    
    private var currentLocation: Location? = null
    private var currentGeofencePolygons = mutableMapOf<String, Polygon>()
    private var isTrackingLocation = false
    
    private val geofenceUpdateJob = Job()
    private val geofenceScope = CoroutineScope(Dispatchers.Main + geofenceUpdateJob)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "MapsFragment"
        private const val BASE_URL = "http://18.215.238.145:3000"
        private const val CAMERA_ZOOM = 15f
        private const val GEOFENCE_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val LOCATION_UPDATE_INTERVAL = 10 * 1000L // 10 seconds
        
        var currentUserLocation: LatLng? = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewModel()
        setupMapFragment()
        setupUI()
    }

    private fun setupViewModel() {
        taskViewModel = (requireActivity().application as GeoTask).taskViewModel
    }

    private fun setupMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
    }

    private fun setupUI() {
        setupBottomSheet()
        setupMapTypeChips()
        setupLocationButton()
        observeViewModel()
    }

    private fun setupBottomSheet() {
        BottomSheetBehavior.from(binding.bottomSheet).apply {
            peekHeight = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            state = BottomSheetBehavior.STATE_COLLAPSED
            
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            binding.expandIcon.animate()
                                .rotation(180f)
                                .setDuration(200)
                                .start()
                        }
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            binding.expandIcon.animate()
                                .rotation(0f)
                                .setDuration(200)
                                .start()
                        }
                        else -> { /* no-op */ }
                    }
                }
                
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    binding.expandIcon.rotation = slideOffset * 180
                }
            })
        }
    }

    private fun setupMapTypeChips() {
        binding.mapTypeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipNormal -> map.mapType = GoogleMap.MAP_TYPE_NORMAL
                R.id.chipSatellite -> map.mapType = GoogleMap.MAP_TYPE_SATELLITE
                R.id.chipTerrain -> map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            }
        }
    }

    private fun setupLocationButton() {
        binding.myLocationButton.setOnClickListener {
            animateToCurrentLocation()
        }
    }

    private fun observeViewModel() {
        taskViewModel.taskList.observe(viewLifecycleOwner) { tasks ->
            updateMapMarkers(tasks)
        }
        
        taskViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        taskViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { showError(it) }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
        setupClusterManager()
        startLocationUpdates()
        startGeofenceUpdates()
    }

    private fun setupMap() {
        map.apply {
            uiSettings.apply {
                isZoomControlsEnabled = true
                isZoomGesturesEnabled = true
                isCompassEnabled = true
                isMapToolbarEnabled = true
            }
            
            setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
            
            if (hasLocationPermission()) {
                isMyLocationEnabled = true
                uiSettings.isMyLocationButtonEnabled = false
            }
        }
    }

    private fun setupClusterManager() {
        clusterManager = ClusterManager<TaskMarkerItem>(requireContext(), map)
        clusterManager.renderer = TaskMarkerRenderer(requireContext(), map, clusterManager)
        
        map.setOnCameraIdleListener(clusterManager)
        map.setOnMarkerClickListener(clusterManager)
        
        clusterManager.setOnClusterItemClickListener { item ->
            showTaskDetails(item.task)
            true
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        
        try {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = LOCATION_UPDATE_INTERVAL
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                { location ->
                    currentLocation = location
                    currentUserLocation = LatLng(location.latitude, location.longitude)
                    if (!isTrackingLocation) {
                        animateToCurrentLocation()
                        isTrackingLocation = true
                    }
                },
                { error ->
                    Log.e(TAG, "Location update error: ${error.message}")
                    showError("Failed to get location updates")
                }
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates", e)
            showError("Location permission required")
        }
    }

    private fun startGeofenceUpdates() {
        geofenceScope.launch {
            while (isActive) {
                try {
                    updateGeofences()
                    delay(GEOFENCE_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating geofences", e)
                }
            }
        }
    }

    private suspend fun updateGeofences() = withContext(Dispatchers.IO) {
        val currentLocation = currentUserLocation ?: return@withContext
        
        val request = Request.Builder()
            .url("$BASE_URL/fetchGeofences")
            .post(JSONObject().apply {
                put("latitude", currentLocation.latitude)
                put("longitude", currentLocation.longitude)
            }.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
                
                val geofences = JSONObject(response.body?.string() ?: throw IOException("Empty response"))
                    .getJSONArray("geofences")
                
                withContext(Dispatchers.Main) {
                    updateGeofencePolygons(geofences)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update geofences", e)
        }
    }

    private fun updateGeofencePolygons(geofences: JSONArray) {
        // Remove old polygons
        currentGeofencePolygons.values.forEach { it.remove() }
        currentGeofencePolygons.clear()

        // Add new polygons
        for (i in 0 until geofences.length()) {
            val geofence = geofences.getJSONObject(i)
            val coordinates = geofence.getJSONArray("coordinates")
            val points = mutableListOf<LatLng>()
            
            for (j in 0 until coordinates.length()) {
                val point = coordinates.getJSONArray(j)
                points.add(LatLng(point.getDouble(0), point.getDouble(1)))
            }

            val polygon = map.addPolygon(PolygonOptions()
                .addAll(points)
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(70, 0, 0, 255)))
            
            currentGeofencePolygons[geofence.getString("id")] = polygon
        }
    }

    private fun updateMapMarkers(tasks: List<Task>) {
        clusterManager.clearItems()
        
        tasks.forEach { task ->
            clusterManager.addItem(TaskMarkerItem(task))
        }
        
        clusterManager.cluster()
    }

    private fun animateToCurrentLocation() {
        val location = currentLocation ?: return
        val latLng = LatLng(location.latitude, location.longitude)
        
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, CAMERA_ZOOM)
        map.animateCamera(cameraUpdate)
        
        // Animate location button
        binding.myLocationButton.apply {
            ValueAnimator.ofFloat(1f, 0.8f, 1f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    scaleX = animator.animatedValue as Float
                    scaleY = animator.animatedValue as Float
                }
                start()
            }
        }
    }

    private fun showTaskDetails(task: Task) {
        BottomSheetBehavior.from(binding.bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        
        with(binding) {
            taskTitle.text = task.name
            taskDescription.text = task.description
            taskTime.text = "${task.start} - ${task.end}"
            taskDuration.text = task.formatDuration()
            
            // Set priority chip style
            taskPriority.apply {
                text = task.priorityText
                setChipBackgroundColorResource(when (task.priority) {
                    Task.PRIORITY_HIGH -> R.color.priority_high
                    Task.PRIORITY_MEDIUM -> R.color.priority_medium
                    Task.PRIORITY_LOW -> R.color.priority_low
                    else -> R.color.priority_default
                })
            }
            
            navigateButton.setOnClickListener {
                launchNavigation(task)
            }
        }
    }

    private fun launchNavigation(task: Task) {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(
                "google.navigation:q=${task.location_lat},${task.location_lng}"
            )
        )
        startActivity(intent)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                taskViewModel.refreshTaskList()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        geofenceUpdateJob.cancel()
        _binding = null
    }
    
    private inner class TaskMarkerItem(
        val task: Task
    ) : com.google.maps.android.clustering.ClusterItem {
        override fun getPosition(): LatLng = task.location
        override fun getTitle(): String = task.name
        override fun getSnippet(): String = task.description
    }
    
    private inner class TaskMarkerRenderer(
        context: android.content.Context,
        map: GoogleMap,
        clusterManager: ClusterManager<TaskMarkerItem>
    ) : DefaultClusterRenderer<TaskMarkerItem>(context, map, clusterManager) {
        
        override fun onBeforeClusterItemRendered(item: TaskMarkerItem, markerOptions: MarkerOptions) {
            markerOptions.apply {
                icon(BitmapDescriptorFactory.defaultMarker(when (item.task.priority) {
                    Task.PRIORITY_HIGH -> BitmapDescriptorFactory.HUE_RED
                    Task.PRIORITY_MEDIUM -> BitmapDescriptorFactory.HUE_ORANGE
                    Task.PRIORITY_LOW -> BitmapDescriptorFactory.HUE_GREEN
                    else -> BitmapDescriptorFactory.HUE_AZURE
                }))
                alpha(if (item.task.isCompleted) 0.6f else 1.0f)
            }
        }
    }
}