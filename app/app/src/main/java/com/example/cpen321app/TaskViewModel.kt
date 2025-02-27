package com.example.cpen321app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONObject
import java.io.IOException
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody

// Generated by Copilot, aside from the backend code
class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val _taskList = MutableLiveData<MutableList<Task>>()
    val taskList: LiveData<MutableList<Task>> get() = _taskList

    private var server_ip = "18.215.238.145:3000";

    companion object {
        private const val TAG = "TaskViewModel"
    }

    init {
        _taskList.value = mutableListOf()
    }

    fun addTask(task: Task) {

        // Add task to backend
        sendAddTaskRequest(task);

        _taskList.value?.add(task)
        _taskList.notifyObservers()
    }

    fun deleteTask(task: Task) {

        // Remove task from backend

        _taskList.value?.remove(task)
        _taskList.notifyObservers()
    }

    // My code
    fun updateTaskList() {

        // Update task list from backend

    }

    fun updateTaskListTesting() {
        val cadTire = Task("1", "Visit Canadian Tire", "15/03/2025", "4", 20.0, 49.2099188, -123.1075474, 1, "Pick up new summer tires")
        val groceries = Task("2", "Grocery Shopping", "28/02/2025", "4", 10.0,49.2085271,-123.0996029, 1, "Buy Groceries")
        val midterm = Task("3", "CPEN 321 Midterm Pick Up", "28/02/2025", "4", 30.0, 49.2624275, -123.2502343, 1, "Pick up my midterm 1 from Professor's office")

        addTask(cadTire)
        addTask(groceries)
        addTask(midterm)
    }

    fun logAllTasks() {
        for (item in _taskList.value!!) {
            Log.d(TAG, item.name)
        }
    }

    private fun <T> MutableLiveData<T>.notifyObservers() {
        this.value = this.value
    }

    private fun sendAddTaskRequest(newtask: Task) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/addTask"

        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("name", newtask.name)
            put("start_time", newtask.start)
            put("end_time", newtask.end)
            put("duration", newtask.duration)
            put("location_lat", newtask.location_lat)
            put("location_lng", newtask.location_lng)
            put("priority", newtask.priority)
            put("description", newtask.description)
        }

        Log.d(TAG, jsonBody.toString());
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send fetchRoute request: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")

                    val resultJson = JSONObject(jsonResponse)
                    val new_task_id = resultJson.getString("new_task_id")
                    Log.d(TAG, "Received new_task_id: $new_task_id")
                }
            }
        })
    }
}