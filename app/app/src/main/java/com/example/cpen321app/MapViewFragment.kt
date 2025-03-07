package com.example.cpen321app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.cpen321app.databinding.FragmentMapViewBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import kotlinx.coroutines.*
import java.util.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MapViewFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapViewFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentMapViewBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var locationCallback: LocationCallback
    private var currentLocationMarker: Marker? = null
    private var taskMarkers: MutableMap<String, Marker> = mutableMapOf()
    private var currentPolyline: Polyline? = null
    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    companion object {
        private const val TAG = "MapViewFragment"
        private const val DEFAULT_ZOOM = 15f
        private const val LOCATION_UPDATE_INTERVAL = 10000L
        private const val LOCATION_FASTEST_INTERVAL = 5000L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupViewModel()
        setupMapFragment()
        setupLocationServices()
        setupBottomSheet()
        setupFilterChips()
    }

    private fun setupViewModel() {
        taskViewModel = ViewModelProvider(requireActivity())[TaskViewModel::class.java]
        
        taskViewModel.taskList.observe(viewLifecycleOwner) { tasks ->
            updateMapMarkers(tasks)
        }
    }

    private fun setupMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateCurrentLocation(location)
                }
            }
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
        
        binding.routeButton.setOnClickListener {
            calculateAndDisplayRoute()
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.apply {
            addView(createFilterChip("High Priority"))
            addView(createFilterChip("Today"))
            addView(createFilterChip("This Week"))
        }
    }

    private fun createFilterChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            setOnCheckedChangeListener { _, isChecked ->
                filterTasks()
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMap()
        startLocationUpdates()
    }

    private fun setupMap() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        try {
            map.apply {
                isMyLocationEnabled = true
                uiSettings.apply {
                    isMyLocationButtonEnabled = true
                    isZoomControlsEnabled = true
                    isCompassEnabled = true
                    isMapToolbarEnabled = true
                }
                setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
                
                setOnMarkerClickListener { marker ->
                    showTaskDetails(marker)
                    true
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error setting up map: ${e.message}")
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                requireActivity().mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Error requesting location updates: ${e.message}")
        }
    }

    private fun updateCurrentLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        
        currentLocationMarker?.remove()
        currentLocationMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("Current Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        if (SessionManager.currentLocation == null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
        }
        
        SessionManager.currentLocation = location
    }

    private fun updateMapMarkers(tasks: List<Task>) {
        // Remove old markers
        taskMarkers.values.forEach { it.remove() }
        taskMarkers.clear()
        
        tasks.forEach { task ->
            val latLng = LatLng(task.location_lat, task.location_lng)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(task.name)
                    .snippet(task.description)
                    .icon(getMarkerIcon(task.priority))
            )
            marker?.let { taskMarkers[task.id] = it }
        }
    }

    private fun getMarkerIcon(priority: Int): BitmapDescriptor {
        val hue = when (priority) {
            3 -> BitmapDescriptorFactory.HUE_RED     // High priority
            2 -> BitmapDescriptorFactory.HUE_YELLOW  // Medium priority
            else -> BitmapDescriptorFactory.HUE_GREEN // Low priority
        }
        return BitmapDescriptorFactory.defaultMarker(hue)
    }

    private fun showTaskDetails(marker: Marker) {
        val task = taskViewModel.taskList.value?.find { 
            taskMarkers[it.id]?.id == marker.id 
        } ?: return

        binding.apply {
            taskTitle.text = task.name
            taskDescription.text = task.description
            taskPriority.text = "Priority: ${task.getPriorityText()}"
            taskDuration.text = "Duration: ${task.duration} hours"
            taskTime.text = "Time: ${task.getFormattedTimeRange()}"
        }

        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun calculateAndDisplayRoute() {
        val tasks = taskViewModel.taskList.value ?: return
        if (tasks.isEmpty()) return

        currentPolyline?.remove()

        val currentLocation = SessionManager.currentLocation ?: return
        val orderedTasks = orderTasksByDistance(currentLocation, tasks)
        
        val points = mutableListOf<LatLng>()
        points.add(LatLng(currentLocation.latitude, currentLocation.longitude))
        points.addAll(orderedTasks.map { LatLng(it.location_lat, it.location_lng) })

        currentPolyline = map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(Color.BLUE)
                .width(5f)
        )

        // Zoom to show the entire route
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        val bounds = builder.build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
    }

    private fun orderTasksByDistance(location: Location, tasks: List<Task>): List<Task> {
        return tasks.sortedBy { task ->
            val taskLocation = Location("").apply {
                latitude = task.location_lat
                longitude = task.location_lng
            }
            location.distanceTo(taskLocation)
        }
    }

    private fun filterTasks() {
        val selectedFilters = binding.filterChipGroup.checkedChipIds.mapNotNull { 
            binding.filterChipGroup.findViewById<Chip>(it)?.text?.toString() 
        }
        
        taskViewModel.filterTasks(selectedFilters)
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1001
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        _binding = null
    }
}