package com.example.cpen321app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider

class AddTask : AppCompatActivity() {

    private lateinit var taskViewModel: TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_task)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val editTextName = findViewById<EditText>(R.id.editTextName)
        val editTextStart = findViewById<EditText>(R.id.editText_taskStart)
        val editTextEnd = findViewById<EditText>(R.id.editText_taskEnd)
        val editTextLocation_lat = findViewById<EditText>(R.id.editText_taskLat)
        val editTextLocation_lng= findViewById<EditText>(R.id.editText_taskLng)
        val editTextPriority = findViewById<EditText>(R.id.editText_taskPrio)
        val editTextDescription = findViewById<EditText>(R.id.editText_description)


        taskViewModel = (application as GeoTask).taskViewModel

        val buttonCreate = findViewById<Button>(R.id.button_taskCreate)

        buttonCreate.setOnClickListener {

            val id: String = "Placeholder"
            val name: String = editTextName.text.toString()
            val start: String = editTextStart.text.toString()
            val end: Int = editTextEnd.text.toString().toInt()
            val location_lat: Double = editTextLocation_lat.text.toString().toDouble()
            val location_lng: Double = editTextLocation_lng.text.toString().toDouble()
            val priority: Int = editTextPriority.text.toString().toInt()
            val description: String = editTextDescription.text.toString()

//            val name: String = "Name"
//            val start: String = "Start"
//            val end: Int = 3
//            val location_lat: Double = 147.0
//            val location_lng: Double = 106.2
//            val priority: Int = 1
//            val description: String = "Description"

            val newTask = Task(id, name, start, end, location_lat, location_lng, priority, description)

            // Send to backend and then update the list with the new task including the id.

            // temp code to add to list
//            taskViewModel = ViewModelProvider(this)[TaskViewModel::class.java]

            taskViewModel.addTask(newTask)

            taskViewModel.logAllTasks()

            finish()
        }

    }
}