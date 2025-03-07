package com.example.cpen321app

import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import com.example.cpen321app.databinding.ActivityAddTaskBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.*

class AddTask : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var taskViewModel: TaskViewModel
    private var map: GoogleMap? = null
    private var selectedLocation: LatLng? = null
    private var startDate: Calendar = Calendar.getInstance()
    private var endDate: Calendar = Calendar.getInstance()
    
    companion object {
        private const val DEFAULT_ZOOM = 15f
        private const val DEFAULT_DURATION = 60 // minutes
        private const val MIN_DESCRIPTION_LENGTH = 10
        private const val MAX_TASK_DURATION = 24 * 60 // 24 hours in minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupToolbar()
        setupMapFragment()
        setupInputFields()
        setupDateTimePickers()
        setupPriorityChips()
        setupSaveButton()
        setupLocationSearch()
    }

    private fun setupViewModel() {
        taskViewModel = (application as GeoTask).taskViewModel
    }

    private fun setupToolbar() {
        binding.toolbar.apply {
            setNavigationOnClickListener {
                if (hasUnsavedChanges()) {
                    showDiscardDialog()
                } else {
                    finish()
                }
            }
        }
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapPreview) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupInputFields() {
        // Task name validation
        binding.taskNameInput.doAfterTextChanged { text ->
            binding.taskNameLayout.error = when {
                text.isNullOrBlank() -> "Task name is required"
                text.length < 3 -> "Task name must be at least 3 characters"
                else -> null
            }
        }

        // Description validation
        binding.taskDescriptionInput.doAfterTextChanged { text ->
            binding.taskDescriptionLayout.error = when {
                text.isNullOrBlank() -> "Description is required"
                text.length < MIN_DESCRIPTION_LENGTH -> "Description must be at least $MIN_DESCRIPTION_LENGTH characters"
                else -> null
            }
        }

        // Duration validation and auto-calculation
        binding.durationInput.apply {
            setText(DEFAULT_DURATION.toString())
            doAfterTextChanged { text ->
                if (!text.isNullOrBlank()) {
                    try {
                        val duration = text.toString().toInt()
                        binding.durationLayout.error = when {
                            duration <= 0 -> "Duration must be positive"
                            duration > MAX_TASK_DURATION -> "Duration cannot exceed ${MAX_TASK_DURATION / 60} hours"
                            else -> null
                        }
                        if (binding.durationLayout.error == null) {
                            updateEndTime()
                        }
                    } catch (e: NumberFormatException) {
                        binding.durationLayout.error = "Invalid duration"
                    }
                }
            }
        }
    }

    private fun setupLocationSearch() {
        binding.locationSearchButton.setOnClickListener {
            val searchFragment = PlaceSearchFragment()
            searchFragment.onPlaceSelected = { place ->
                place.latLng?.let { latLng ->
                    updateSelectedLocation(latLng)
                }
            }
            searchFragment.show(supportFragmentManager, "place_search")
        }
    }

    private fun updateSelectedLocation(latLng: LatLng) {
        selectedLocation = latLng
        map?.apply {
            clear()
            addMarker(MarkerOptions().position(latLng))
            animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
        }
        binding.locationText.text = "Selected: ${String.format("%.6f, %.6f", latLng.latitude, latLng.longitude)}"
    }

    private fun hasUnsavedChanges(): Boolean {
        return binding.taskNameInput.text?.isNotBlank() == true ||
            binding.taskDescriptionInput.text?.isNotBlank() == true ||
            binding.startTimeInput.text?.isNotBlank() == true ||
            binding.endTimeInput.text?.isNotBlank() == true ||
            binding.durationInput.text.toString() != DEFAULT_DURATION.toString() ||
            selectedLocation != null
    }

    private fun showDiscardDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes?")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep Editing", null)
            .show()
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate task name
        if (binding.taskNameInput.text.isNullOrBlank() || binding.taskNameInput.text?.length ?: 0 < 3) {
            binding.taskNameLayout.error = "Task name must be at least 3 characters"
            isValid = false
        }

        // Validate description
        if (binding.taskDescriptionInput.text.isNullOrBlank() || 
            binding.taskDescriptionInput.text?.length ?: 0 < MIN_DESCRIPTION_LENGTH) {
            binding.taskDescriptionLayout.error = "Description must be at least $MIN_DESCRIPTION_LENGTH characters"
            isValid = false
        }

        // Validate times
        if (binding.startTimeInput.text.isNullOrBlank()) {
            binding.startTimeLayout.error = "Start time is required"
            isValid = false
        }

        if (binding.endTimeInput.text.isNullOrBlank()) {
            binding.endTimeLayout.error = "End time is required"
            isValid = false
        }

        // Validate duration
        try {
            val duration = binding.durationInput.text.toString().toInt()
            when {
                duration <= 0 -> {
                    binding.durationLayout.error = "Duration must be positive"
                    isValid = false
                }
                duration > MAX_TASK_DURATION -> {
                    binding.durationLayout.error = "Duration cannot exceed ${MAX_TASK_DURATION / 60} hours"
                    isValid = false
                }
            }
        } catch (e: NumberFormatException) {
            binding.durationLayout.error = "Invalid duration"
            isValid = false
        }

        // Validate time range
        if (startDate.after(endDate)) {
            Snackbar.make(binding.root, "End time must be after start time", Snackbar.LENGTH_LONG).show()
            isValid = false
        }

        // Validate location
        if (selectedLocation == null) {
            Snackbar.make(binding.root, "Please select a location on the map", Snackbar.LENGTH_LONG).show()
            isValid = false
        }

        return isValid
    }

    private fun setupDateTimePickers() {
        binding.startTimeInput.setOnClickListener {
            showDateTimePicker(true)
        }

        binding.endTimeInput.setOnClickListener {
            showDateTimePicker(false)
        }
    }

    private fun showDateTimePicker(isStartTime: Boolean) {
        val calendar = if (isStartTime) startDate else endDate

        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStartTime) "Select Start Date" else "Select End Date")
            .setSelection(calendar.timeInMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            calendar.timeInMillis = selection
            
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(Calendar.MINUTE))
                .setTitleText(if (isStartTime) "Select Start Time" else "Select End Time")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                if (isStartTime) {
                    binding.startTimeInput.setText(dateFormat.format(calendar.time))
                    updateEndTime()
                } else {
                    binding.endTimeInput.setText(dateFormat.format(calendar.time))
                }
            }

            timePicker.show(supportFragmentManager, "time_picker")
        }

        datePicker.show(supportFragmentManager, "date_picker")
    }

    private fun updateEndTime() {
        try {
            val duration = binding.durationInput.text.toString().toInt()
            endDate.timeInMillis = startDate.timeInMillis
            endDate.add(Calendar.MINUTE, duration)
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            binding.endTimeInput.setText(dateFormat.format(endDate.time))
        } catch (e: NumberFormatException) {
            // Ignore invalid duration
        }
    }

    private fun setupPriorityChips() {
        binding.priorityChipGroup.check(R.id.chipMedium)
    }

    private fun setupSaveButton() {
        binding.saveFab.setOnClickListener {
            if (validateInputs()) {
                createTask()
            }
        }
    }

    private fun createTask() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        
        val priority = when (binding.priorityChipGroup.checkedChipId) {
            R.id.chipHigh -> Task.PRIORITY_HIGH
            R.id.chipMedium -> Task.PRIORITY_MEDIUM
            R.id.chipLow -> Task.PRIORITY_LOW
            else -> Task.PRIORITY_MEDIUM
        }

        val task = Task(
            id = "", // Will be set by the server
            name = binding.taskNameInput.text.toString(),
            start = dateFormat.format(startDate.time),
            end = dateFormat.format(endDate.time),
            duration = binding.durationInput.text.toString().toDouble(),
            location_lat = selectedLocation?.latitude ?: 0.0,
            location_lng = selectedLocation?.longitude ?: 0.0,
            priority = priority,
            description = binding.taskDescriptionInput.text.toString()
        )

        taskViewModel.addTask(task)
        animateSaveAndFinish()
    }

    private fun animateSaveAndFinish() {
        ValueAnimator.ofFloat(1f, 0.8f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                binding.saveFab.scaleX = animator.animatedValue as Float
                binding.saveFab.scaleY = animator.animatedValue as Float
            }
            doOnEnd {
                finish()
            }
            start()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        
        // Set default location to user's current location or a default position
        val defaultLocation = SessionManager.currentLocation?.let {
            LatLng(it.latitude, it.longitude)
        } ?: LatLng(49.2827, -123.1207) // Default to Vancouver

        map?.apply {
            moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, DEFAULT_ZOOM))
            
            setOnMapClickListener { latLng ->
                clear()
                selectedLocation = latLng
                addMarker(MarkerOptions().position(latLng))
                binding.locationText.text = "Selected: ${String.format("%.6f, %.6f", latLng.latitude, latLng.longitude)}"
            }
        }
    }

    override fun onBackPressed() {
        if (hasUnsavedChanges()) {
            showDiscardDialog()
        } else {
            super.onBackPressed()
        }
    }
}
