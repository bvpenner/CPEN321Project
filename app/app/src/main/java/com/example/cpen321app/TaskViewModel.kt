package com.example.cpen321app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.idling.CountingIdlingResource
import com.example.cpen321app.MainActivity.Companion
import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// Generated by Copilot, aside from the backend code
class TaskViewModel(application: Application) : AndroidViewModel(application) {

    object IdlingResourceManager {
        val countingIdlingResource = CountingIdlingResource("Refresh Task List")
    }

    val taskList: LiveData<MutableList<Task>> get() = _taskList

    private var server_ip = "13.216.143.65:3000";

    companion object {
        private const val TAG = "TaskViewModel"
        public var server_ip = "13.216.143.65:3000";
        val _taskList = MutableLiveData<MutableList<Task>>()
        val _geofenceList = MutableLiveData<MutableList<List<LatLng>>>()
    }

    init {
        _taskList.value = mutableListOf()
    }

    fun refreshTasklist(){
//        _taskList.postValue(mutableListOf())
        sendGetAllTasksToServer()
    }

    fun addTask(task: Task) {
        // Add task to backend.
        sendAddTaskRequest(task)
        _taskList.value?.add(task)
        _taskList.notifyObservers()
    }

    fun deleteTask(task: Task) {
        // Remove task from backend.
        IdlingResourceManager.countingIdlingResource.increment()
        sendDeleteTaskRequest(task);
        _taskList.value?.let {
            it.remove(task)
            _taskList.postValue(it)
        }
        _taskList.notifyObservers()

        Log.d(TAG, "Current tasks: ${_taskList.value?.map { it.name }}")

        IdlingResourceManager.countingIdlingResource.decrement()
    }

    fun addExampleTask() {
        val exampleTask = Task(
            id = "1",
            name = "Visit Googleplex (Example Task)",
            start = "10:00",
            end = "11:00",
            duration = 60,
            location_lat = 37.422,
            location_lng = -122.084,
            priority = 1,
            description = "Explore the Google campus and see the Android statue."
        )

        addTask(exampleTask)
    }

    fun updateTaskListTesting() {
        val sampleTasks = listOf(
            Task(
                id = "1",
                name = "Visit Googleplex",
                start = "10:00",
                end = "11:00",
                duration = 60,
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
                duration = 90,
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
                duration = 60,
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
//        _taskList.value?.forEach { Log.d(TAG, it.name) }
    }

    private fun <T> MutableLiveData<T>.notifyObservers() {
//        this.value = this.value
        this.postValue(this.value)
    }

    private fun sendAddTaskRequest(newTask: Task) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/addTask"

        // Build JSON body
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


    private fun sendUpdateTaskRequest(selectedTask: Task) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/addTask"

        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("name", selectedTask.name)
            put("_id", selectedTask.id)
            put("start_time", selectedTask.start)
            put("end_time", selectedTask.end)
            put("duration", selectedTask.duration)
            put("location_lat", selectedTask.location_lat)
            put("location_lng", selectedTask.location_lng)
            put("priority", selectedTask.priority)
            put("description", selectedTask.description)
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

    private fun sendDeleteTaskRequest(task: Task) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/deleteTask"

        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("_id", task.id)
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
                }
            }
        })
    }

    private fun sendGetAllTasksToServer() {
        val client = OkHttpClient()
        val url = "http://${server_ip}/getAllTasks?u_id=${SessionManager.u_id}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        IdlingResourceManager.countingIdlingResource.increment()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Failed to get tasks: ${e.message}")
                IdlingResourceManager.countingIdlingResource.decrement()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    IdlingResourceManager.countingIdlingResource.decrement()
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")
                    val resultJson = JSONObject(jsonResponse)
                    val taskListJsonArray = resultJson.getJSONArray("task_list")

                    val taskList = mutableListOf<Task>()
                    for (i in 0 until taskListJsonArray.length()) {
                        val taskJson = taskListJsonArray.getJSONObject(i)
                        val task = Task(
                            id = taskJson.getString("_id"),
                            name = taskJson.getString("name"),
                            start = taskJson.getString("start"),
                            end = taskJson.getString("end"),
                            duration = taskJson.getInt("duration"),
                            location_lat = taskJson.getDouble("location_lat"),
                            location_lng = taskJson.getDouble("location_lng"),
                            priority = taskJson.getInt("priority"),
                            description = taskJson.getString("description")
                        )
                        taskList.add(task)
                    }
                    _taskList.postValue(mutableListOf<Task>())

                    _taskList.postValue(taskList)
                    logAllTasks()
                    Log.d(TAG, "Received response taskList: $jsonResponse")
                    _taskList.notifyObservers()
                    IdlingResourceManager.countingIdlingResource.decrement()
                }
            }
        })
    }
}
