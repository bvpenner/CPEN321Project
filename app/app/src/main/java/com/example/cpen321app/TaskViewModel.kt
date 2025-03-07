package com.example.cpen321app

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.cpen321app.MainActivity.Companion
import kotlinx.coroutines.*
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// Generated by Copilot, aside from the backend code
class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val _taskList = MutableLiveData<List<Task>>()
    val taskList: LiveData<List<Task>> = _taskList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _sortOrder = MutableLiveData(SortOrder.PRIORITY)
    val sortOrder: LiveData<SortOrder> = _sortOrder

    private val _filterCriteria = MutableLiveData<FilterCriteria>()
    val filterCriteria: LiveData<FilterCriteria> = _filterCriteria

    private val viewModelJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TaskViewModel"
        private const val SERVER_IP = "18.215.238.145:3000"
        private const val BASE_URL = "http://$SERVER_IP"
    }

    init {
        _taskList.value = emptyList()
        _isLoading.value = false
        _error.value = null
        _filterCriteria.value = FilterCriteria()
        refreshTaskList()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun refreshTaskList() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                fetchTasks()
            } catch (e: Exception) {
                _error.value = "Failed to refresh tasks: ${e.localizedMessage}"
                Log.e(TAG, "Error refreshing tasks", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addTask(task: Task) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                val newTaskId = sendAddTaskRequest(task)
                val updatedTask = task.copy(id = newTaskId)
                val currentList = _taskList.value.orEmpty().toMutableList()
                currentList.add(updatedTask)
                updateTaskListWithSort(currentList)
            } catch (e: Exception) {
                _error.value = "Failed to add task: ${e.localizedMessage}"
                Log.e(TAG, "Error adding task", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                sendDeleteTaskRequest(task)
                val currentList = _taskList.value.orEmpty().toMutableList()
                currentList.remove(task)
                _taskList.value = currentList
            } catch (e: Exception) {
                _error.value = "Failed to delete task: ${e.localizedMessage}"
                Log.e(TAG, "Error deleting task", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                sendUpdateTaskRequest(task)
                val currentList = _taskList.value.orEmpty().toMutableList()
                val index = currentList.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    currentList[index] = task
                    updateTaskListWithSort(currentList)
                }
            } catch (e: Exception) {
                _error.value = "Failed to update task: ${e.localizedMessage}"
                Log.e(TAG, "Error updating task", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        updateTaskListWithSort(_taskList.value.orEmpty())
    }

    fun setFilterCriteria(criteria: FilterCriteria) {
        _filterCriteria.value = criteria
        updateTaskListWithSort(_taskList.value.orEmpty())
    }

    private fun updateTaskListWithSort(tasks: List<Task>) {
        val filteredTasks = tasks.filter { task ->
            val criteria = _filterCriteria.value ?: return@filter true
            
            val matchesPriority = criteria.priority?.let { task.priority == it } ?: true
            val matchesCompleted = criteria.showCompleted || !task.isCompleted
            val matchesOverdue = criteria.showOverdue || !task.isOverdue
            val matchesSearch = criteria.searchQuery?.let { query ->
                task.name.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true)
            } ?: true

            matchesPriority && matchesCompleted && matchesOverdue && matchesSearch
        }

        val sortedTasks = when (_sortOrder.value) {
            SortOrder.PRIORITY -> filteredTasks.sortedByDescending { it.priority }
            SortOrder.DUE_DATE -> filteredTasks.sortedBy { it.endDate }
            SortOrder.START_TIME -> filteredTasks.sortedBy { it.startDate }
            SortOrder.NAME -> filteredTasks.sortedBy { it.name }
            else -> filteredTasks
        }

        _taskList.value = sortedTasks
    }

    private suspend fun fetchTasks() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/getAllTasks?u_id=${SessionManager.u_id}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            
            val jsonResponse = response.body?.string() ?: throw IOException("Empty response body")
            val resultJson = JSONObject(jsonResponse)
            val taskListJsonArray = resultJson.getJSONArray("task_list")

            val tasks = mutableListOf<Task>()
            for (i in 0 until taskListJsonArray.length()) {
                val taskJson = taskListJsonArray.getJSONObject(i)
                tasks.add(Task(
                    id = taskJson.getString("_id"),
                    name = taskJson.getString("name"),
                    start = taskJson.getString("start"),
                    end = taskJson.getString("end"),
                    duration = taskJson.getDouble("duration"),
                    location_lat = taskJson.getDouble("location_lat"),
                    location_lng = taskJson.getDouble("location_lng"),
                    priority = taskJson.getInt("priority"),
                    description = taskJson.getString("description"),
                    isCompleted = taskJson.optBoolean("isCompleted", false),
                    lastModified = taskJson.optLong("lastModified", System.currentTimeMillis())
                ))
            }
            
            withContext(Dispatchers.Main) {
                updateTaskListWithSort(tasks)
            }
        }
    }

    private suspend fun sendAddTaskRequest(task: Task): String = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("name", task.name)
            put("start_time", task.start)
            put("end_time", task.end)
            put("duration", task.duration)
            put("location_lat", task.location_lat)
            put("location_lng", task.location_lng)
            put("priority", task.priority)
            put("description", task.description)
            put("isCompleted", task.isCompleted)
            put("lastModified", task.lastModified)
        }

        val request = Request.Builder()
            .url("$BASE_URL/addTask")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            
            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            JSONObject(responseBody).getString("new_task_id")
        }
    }

    private suspend fun sendDeleteTaskRequest(task: Task) = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("_id", task.id)
        }

        val request = Request.Builder()
            .url("$BASE_URL/deleteTask")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
        }
    }

    private suspend fun sendUpdateTaskRequest(task: Task) = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("owner_id", SessionManager.u_id)
            put("_id", task.id)
            put("name", task.name)
            put("start_time", task.start)
            put("end_time", task.end)
            put("duration", task.duration)
            put("location_lat", task.location_lat)
            put("location_lng", task.location_lng)
            put("priority", task.priority)
            put("description", task.description)
            put("isCompleted", task.isCompleted)
            put("lastModified", task.lastModified)
        }

        val request = Request.Builder()
            .url("$BASE_URL/updateTask")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
        }
    }

    enum class SortOrder {
        PRIORITY, DUE_DATE, START_TIME, NAME
    }

    data class FilterCriteria(
        val priority: Int? = null,
        val showCompleted: Boolean = true,
        val showOverdue: Boolean = true,
        val searchQuery: String? = null
    )
}
