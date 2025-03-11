package com.example.cpen321app

import android.app.Activity
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.*

class AddTask : AppCompatActivity() {

    private lateinit var taskViewModel: TaskViewModel
    private lateinit var locationPickerLauncher: ActivityResultLauncher<android.content.Intent>
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_task)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        taskViewModel = (application as GeoTask).taskViewModel

        // Initialize the location picker launcher.
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val latitude = data?.getDoubleExtra("latitude", 0.0)
                val longitude = data?.getDoubleExtra("longitude", 0.0)
                if (latitude != null && longitude != null) {
                    findViewById<EditText>(R.id.editText_taskLat).setText(latitude.toString())
                    findViewById<EditText>(R.id.editText_taskLng).setText(longitude.toString())
                    Toast.makeText(this, "Location Selected: ($latitude, $longitude)", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
            val name = findViewById<EditText>(R.id.editTextName).text.toString()
            val start = editTextStart.text.toString()
            val end = editTextEnd.text.toString()
            val duration = findViewById<EditText>(R.id.editText_duration).text.toString().toDoubleOrNull() ?: 0.0
            val latitude = findViewById<EditText>(R.id.editText_taskLat).text.toString().toDoubleOrNull() ?: 0.0
            val longitude = findViewById<EditText>(R.id.editText_taskLng).text.toString().toDoubleOrNull() ?: 0.0
            val priority = findViewById<EditText>(R.id.editText_taskPrio).text.toString().toIntOrNull() ?: 1
            val description = findViewById<EditText>(R.id.editText_description).text.toString()

            if (latitude !in -90.0..90.0) {
                Toast.makeText(this, "Valid Latitude Required: Between -90 and 90 degrees", Toast.LENGTH_SHORT).show()
            } else if (longitude !in -180.0..180.0) {
                Toast.makeText(this, "Valid Longitude Required: Between -180 and 180 degrees", Toast.LENGTH_SHORT).show()
            } else {
                val newTask = Task(id, name, start, end, duration, latitude, longitude, priority, description)
                taskViewModel.addTask(newTask)
                taskViewModel.logAllTasks()
                taskViewModel.refreshTasklist()
                finish()
            }
        }
    }

    // Show a TimePickerDialog and update the provided EditText. After updating, attempt to compute duration.
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

    // If both start and end times are available and valid, compute duration in minutes.
    private fun updateDurationIfPossible() {
        val editTextStart = findViewById<EditText>(R.id.editText_taskStart)
        val editTextEnd = findViewById<EditText>(R.id.editText_taskEnd)
        val editTextDuration = findViewById<EditText>(R.id.editText_duration)

        val startText = editTextStart.text.toString()
        val endText = editTextEnd.text.toString()

        if (startText.isNotEmpty() && endText.isNotEmpty()) {
            try {
                val startDate = timeFormat.parse(startText)
                val endDate = timeFormat.parse(endText)
                if (startDate != null && endDate != null) {
                    var diff = endDate.time - startDate.time
                    // If diff is negative, assume the end time is on the next day.
                    if (diff < 0) {
                        diff += 24 * 60 * 60 * 1000
                    }
                    val durationMinutes = diff / (60 * 1000)
                    editTextDuration.setText(durationMinutes.toString())
                }
            } catch (e: Exception) {
                // Ignore parse errors and let the user override.
                e.printStackTrace()
            }
        }
    }
}
