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
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class AddTask : AppCompatActivity() {

    private lateinit var taskViewModel: TaskViewModel
    private lateinit var locationPickerLauncher: ActivityResultLauncher<android.content.Intent>
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)
        val rootView = findViewById<ScrollView>(R.id.scrollView_addTask)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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
        findViewById<Button>(R.id.button_taskCreate).setOnClickListener {
            val id = "Placeholder"
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
            } else if(name.trim() == "") {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid name required", Snackbar.LENGTH_SHORT).show()
            } else if(start.trim() == "" || !start.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d\$"))) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid start required", Snackbar.LENGTH_SHORT).show()
            } else if(end.trim() == "" || !end.matches(Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d\$"))) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Valid end required", Snackbar.LENGTH_SHORT).show()
            } else if(!isTimeAfterOrEqual(start, end)) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "End must be after or equal to the start", Snackbar.LENGTH_SHORT).show()
            } else if(duration <= 0) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Duration must be greater than 0.", Snackbar.LENGTH_SHORT).show()
            } else if(priority > 3 || priority < 1) {
                Snackbar.make(findViewById(R.id.scrollView_addTask), "Priority must be 1-3.", Snackbar.LENGTH_SHORT).show()
            } else {
                val newTask = Task(
                    id = id,
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
                taskViewModel.addTask(newTask)
                taskViewModel.logAllTasks()
                taskViewModel.refreshTasklist()
                finish()
            }
        }
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
