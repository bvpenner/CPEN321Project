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

        val buttonCreate = findViewById<Button>(R.id.button_taskCreate)

        buttonCreate.setOnClickListener {

            val id: String = "Placeholder"
            val name: String = findViewById<EditText>(R.id.taskName).text.toString()
            val start: String = findViewById<EditText>(R.id.taskStart).text.toString()
            val end: Int = findViewById<EditText>(R.id.taskEnd).text.toString().toInt()
            val location_lat: Double = findViewById<EditText>(R.id.taskLat).text.toString().toDouble()
            val location_lng: Double = findViewById<EditText>(R.id.taskLng).text.toString().toDouble()
            val priority: Int = findViewById<EditText>(R.id.taskPriority).text.toString().toInt()
            val description: String = findViewById<EditText>(R.id.taskDescription).text.toString()

            val newTask = Task(id, name, start, end, location_lat, location_lng, priority, description)

            // Send to backend and then update the list with the new task including the id.

            // temp code to add to list
//            taskViewModel = ViewModelProvider(this).get(TaskViewModel::class.java)
//
//            taskViewModel.addTask(newTask)

            finish()
        }

    }
}