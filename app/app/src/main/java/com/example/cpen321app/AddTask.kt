package com.example.cpen321app

import android.app.Activity
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.NoSuchElementException

class AddTask : AppCompatActivity() {

    private lateinit var taskViewModel: TaskViewModel
    private lateinit var locationPickerLauncher: ActivityResultLauncher<android.content.Intent>
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var updateTaskMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)
        val rootView = findViewById<ScrollView>(R.id.scrollView_addTask)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val id = intent.getStringExtra("id")

        if(id != null) {
            initializeForUpdateTask()
        }

        taskViewModel = (application as GeoTask).taskViewModel

        // Initialize the location picker launcher.
        locationPickerLauncher()

        // When the user taps the "Pick Location" button, launch the location search activity.
        findViewById<Button>(R.id.button_pick_location).setOnClickListener {
            val intent = android.content.Intent(this, LocationSearchActivity::class.java)
            locationPickerLauncher.launch(intent)
        }

        // Set up TimePickerDialogs for Start and End fields.
        val editTextStart = findViewById<EditText>(R.id.editText_taskStart)
        val editTextEnd = findViewById<EditText>(R.id.editText_taskEnd)
        // Make time fields non-editable and clickable.
        editTextStart.isFocusable = false
        editTextStart.isClickable = true
        editTextEnd.isFocusable = false
        editTextEnd.isClickable = true

        editTextStart.setOnClickListener { showTimePickerDialog(editTextStart) }
        editTextEnd.setOnClickListener { showTimePickerDialog(editTextEnd) }

        // Create task on button press.
        createTaskOnClickListenerSetup(id, editTextStart, editTextEnd)
    }

    private fun createTaskOnClickListenerSetup(
        id: String?,
        editTextStart: EditText,
        editTextEnd: EditText
    ) {
        findViewById<Button>(R.id.button_taskCreate).setOnClickListener {
            var idToSend = "Placeholder"
            if (updateTaskMode) idToSend = id!!
            val name = findViewById<EditText>(R.id.editTextName).text.toString().trim()
            val start = editTextStart.text.toString().trim()
            val end = editTextEnd.text.toString().trim()
            val duration = findViewById<EditText>(R.id.editText_duration).text.toString().trim().toIntOrNull() ?: 0
            val latitude = findViewById<EditText>(R.id.editText_taskLat).text.toString().trim().toDoubleOrNull() ?: 0.0
            val longitude = findViewById<EditText>(R.id.editText_taskLng).text.toString().trim().toDoubleOrNull() ?: 0.0
            val priority = findViewById<EditText>(R.id.editText_taskPrio).text.toString().trim().toIntOrNull() ?: 1
            val description = findViewById<EditText>(R.id.editText_description).text.toString().trim()

            if (latitude !in -90.0..90.0) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid Latitude Required: Between -90 and 90 degrees", Snackbar.LENGTH_SHORT).show()
            } else if (longitude !in -180.0..180.0) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid Longitude Required: Between -180 and 180 degrees", Snackbar.LENGTH_SHORT).show()
            } else if (name.trim() == "") {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid name required", Snackbar.LENGTH_SHORT).show()
            } else if (start.trim() == "" || !start.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d\$"))) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid start required", Snackbar.LENGTH_SHORT).show()
            } else if (end.trim() == "" || !end.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d\$"))) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid end required", Snackbar.LENGTH_SHORT).show()
            } else if (!isTimeAfterOrEqual(start, end)) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "End must be after or equal to the start", Snackbar.LENGTH_SHORT).show()
            } else if (duration <= 0) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Duration must be greater than 0.", Snackbar.LENGTH_SHORT).show()
            } else if (priority > 3 || priority < 1) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Priority must be 1-3.", Snackbar.LENGTH_SHORT).show()
            } else {
                val newTask = Task(
                    id = idToSend,
                    name = name,
                    start = start,
                    end = end,
                    duration = duration,
                    location_lat = latitude,
                    location_lng = longitude,
                    priority = priority,
                    description = description,
                    isGeofenceEnabled = false
                )
                if (updateTaskMode) {
                    taskViewModel.updateTask(newTask)
                } else {
                    taskViewModel.addTask(newTask)
                }
                taskViewModel.logAllTasks()
                taskViewModel.refreshTasklist()
                finish()
            }
        }
    }

    private fun initializeForUpdateTask() {

        updateTaskMode = true

        val id = intent.getStringExtra("id") ?: throw NoSuchElementException("ID should exist")

        val name = intent.getStringExtra("name") ?: throw NoSuchElementException("Name should exist")
        val start = intent.getStringExtra("start") ?: throw NoSuchElementException("Start should exist")
        val end = intent.getStringExtra("end") ?: throw NoSuchElementException("Start should exist")
        val duration = intent.getIntExtra("duration", -1)
        val location_lat = intent.getDoubleExtra("location_lat", 1000.0)
        val location_lng = intent.getDoubleExtra("location_lng", 1000.0)
        val priority = intent.getIntExtra("priority", -1)
        val description = intent.getStringExtra("description") ?: throw NoSuchElementException("description should exist")
        var isGeofenceEnabled = intent.getBooleanExtra("geofence", false)

        if(location_lat > 500 || location_lng > 500) throw NoSuchElementException("Variable not found")
        if(priority == -1 || duration == -1) throw NoSuchElementException("Variable not found")

        val nameField = findViewById<EditText>(R.id.editTextName)
        val startField = findViewById<EditText>(R.id.editText_taskStart)
        val endField = findViewById<EditText>(R.id.editText_taskEnd)
        val durationField = findViewById<EditText>(R.id.editText_duration)
        val locationLatField = findViewById<EditText>(R.id.editText_taskLat)
        val locationLngField = findViewById<EditText>(R.id.editText_taskLng)
        val priorityField = findViewById<EditText>(R.id.editText_taskPrio)
        val descriptionField = findViewById<EditText>(R.id.editText_description)

        nameField.setText(name)
        startField.setText(start)
        endField.setText(end)
        durationField.setText(duration.toString())
        locationLatField.setText(location_lat.toString())
        locationLngField.setText(location_lng.toString())
        priorityField.setText(priority.toString())
        descriptionField.setText(description)

        // Change create task button text
        val createTaskButton = findViewById<MaterialButton>(R.id.button_taskCreate)
        createTaskButton.setText("Update Task")
    }

    private fun locationPickerLauncher() {
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val latitude = data?.getDoubleExtra("latitude", 0.0)
                val longitude = data?.getDoubleExtra("longitude", 0.0)
                if (latitude != null && longitude != null) {
                    findViewById<EditText>(R.id.editText_taskLat).setText(latitude.toString())
                    findViewById<EditText>(R.id.editText_taskLng).setText(longitude.toString())
                    Toast.makeText(
                        this,
                        "Location Selected: ($latitude, $longitude)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showTimePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                editText.setText(formattedTime)
                updateDurationIfPossible()
            },
            hour,
            minute,
            true
        )
        timePickerDialog.show()
    }

    private fun updateDurationIfPossible() {
        val editTextStart = findViewById<EditText>(R.id.editText_taskStart)
        val editTextEnd = findViewById<EditText>(R.id.editText_taskEnd)
        val editTextDuration = findViewById<EditText>(R.id.editText_duration)

        val startText = editTextStart.text.toString()
        val endText = editTextEnd.text.toString()

        if (startText.isNotEmpty() && endText.isNotEmpty()) {
            val startDate = timeFormat.parse(startText)
            val endDate = timeFormat.parse(endText)
            if (startDate != null && endDate != null) {
                var diff = endDate.time - startDate.time
                if (diff < 0) {
                    diff += 24 * 60 * 60 * 1000
                }
                val durationMinutes = diff / (60 * 1000)
                editTextDuration.setText(durationMinutes.toString())
            }
        }
    }

    private fun isTimeAfterOrEqual(earlierTime: String, laterTime: String): Boolean {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val firstTime = LocalTime.parse(laterTime, formatter)
        val secondTime = LocalTime.parse(earlierTime, formatter)

        return firstTime == secondTime || firstTime.isAfter(secondTime)
    }
}
