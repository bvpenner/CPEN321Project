package com.example.cpen321app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import java.io.IOException

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    val taskList: LiveData<MutableList<Task>> get() = _taskList

    private var server_ip = "18.215.238.145:3000"

    companion object {
        private const val TAG = "TaskViewModel"
        val _taskList = MutableLiveData<MutableList<Task>>()
    }

    init {
        _taskList.value = mutableListOf()
    }

    fun addTask(task: Task) {
        // Add task to backend.
        sendAddTaskRequest(task)
        _taskList.value?.add(task)
        _taskList.notifyObservers()
    }

    fun deleteTask(task: Task) {
        // Remove task from backend.
        _taskList.value?.remove(task)
        _taskList.notifyObservers()
    }

    fun updateTaskListTesting() {
        val sampleTasks = listOf(
            Task(
                id = "1",
                name = "Visit Googleplex",
                start = "10:00",
                end = "11:00",
                duration = 60.0,
                location_lat = 37.422,
                location_lng = -122.084,
                priority = 1,
                description = "Explore the Google campus and see the Android statue."
            ),
            Task(
                id = "2",
                name = "Explore Computer History Museum",
                start = "11:30",
                end = "13:00",
                duration = 90.0,
                location_lat = 37.414,
                location_lng = -122.078,
                priority = 2,
                description = "Discover the history of computing."
            ),
            Task(
                id = "3",
                name = "Lunch on Castro Street",
                start = "13:30",
                end = "14:30",
                duration = 60.0,
                location_lat = 37.394,
                location_lng = -122.080,
                priority = 3,
                description = "Enjoy a meal at a local cafe on Castro Street."
            )
        )
        _taskList.value?.clear()
        _taskList.value?.addAll(sampleTasks)
        _taskList.notifyObservers()
    }


    fun logAllTasks() {
        _taskList.value?.forEach { Log.d(TAG, it.name) }
    }

    private fun <T> MutableLiveData<T>.notifyObservers() {
        this.value = this.value
    }

    private fun sendAddTaskRequest(newTask: Task) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/addTask"

        // Build JSON body.
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("name", newTask.name)
            put("start_time", newTask.start)
            put("end_time", newTask.end)
            put("duration", newTask.duration)
            put("location_lat", newTask.location_lat)
            put("location_lng", newTask.location_lng)
            put("priority", newTask.priority)
            put("description", newTask.description)
        }

        Log.d(TAG, jsonBody.toString())
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send task: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")
                    val resultJson = JSONObject(jsonResponse)
                    val newTaskId = resultJson.getString("new_task_id")
                    Log.d(TAG, "Received new_task_id: $newTaskId")
                }
            }
        })
    }
}
