package com.example.cpen321app

import FirebaseMessagingService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cpen321app.MapsFragment.Companion.User_Lat
import com.example.cpen321app.MapsFragment.Companion.User_Lng
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.example.cpen321app.TaskViewModel.Companion.server_ip
import com.example.cpen321app.databinding.FragmentTaskListBinding
import com.google.android.gms.maps.model.LatLng
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class TaskListFragment : Fragment(), TaskAdapter.OnItemLongClickListener {
    private var TAG = "TaskListFragment"
    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Obtain the ViewModel from your Application.
        taskViewModel = (activity?.application as GeoTask).taskViewModel

        // Set up RecyclerView.
        binding.recyclerView.layoutManager = LinearLayoutManager(activity)
        taskAdapter = TaskAdapter(taskViewModel.taskList.value ?: mutableListOf(), this, requireContext())
        binding.recyclerView.adapter = taskAdapter

        // Observe changes in the task list.
        taskViewModel.taskList.observe(viewLifecycleOwner) {
            taskAdapter.notifyDataSetChanged()
        }

        taskViewModel.logAllTasks()

        // Set up the "Plan Route" button.
        binding.buttonPlanRoute.setOnClickListener {
            val selectedTasks = taskAdapter.getSelectedTasks()
            if (selectedTasks.isEmpty()) {
                Toast.makeText(requireContext(), "No tasks selected.", Toast.LENGTH_SHORT).show()
            } else {
                // Get the current location from SessionManager.
                val origin = SessionManager.currentLocation?.let {
                    LatLng(it.latitude, it.longitude)
                }
                if (origin == null) {
                    Toast.makeText(requireContext(), "Current location not available.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // generateRouteForSelectedTasks(origin, selectedTasks)
                sendGetOptimalRouteServer(selectedTasks)
            }
        }
    }

    // Generates the Google Maps route URL and launches Google Maps.
    private fun generateRouteForSelectedTasks(origin: LatLng, selectedTasks: List<Task>) {
        // Use the last task as destination; other tasks become waypoints.
        val destination = selectedTasks.last()
        val waypoints = selectedTasks.dropLast(1).joinToString(separator = "|") {
            "${it.location_lat},${it.location_lng}"
        }
        val mapsUrl = "https://www.google.com/maps/dir/?api=1" +
                "&origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.location_lat},${destination.location_lng}" +
                if (waypoints.isNotEmpty()) "&waypoints=optimize:true|$waypoints" else ""
        // Launch Google Maps.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl)).apply {
            setPackage("com.google.android.apps.maps")
        }
        startActivity(intent)
    }

    override fun onItemLongClick(task: Task): Boolean {
        // Use a PopupMenu for update/delete actions.
        val popupMenu = PopupMenu(requireContext(), requireView())
        popupMenu.menuInflater.inflate(R.menu.popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.update_button -> {
                    Toast.makeText(requireContext(), "Update action for: ${task.name}", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.delete_button -> {
                    taskViewModel.deleteTask(task)
                    Toast.makeText(requireContext(), "Task deleted: ${task.name}", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
        taskViewModel.logAllTasks()
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendGetOptimalRouteServer(selectedTasks: List<Task>) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/fetchOptimalRoute"
        val taskIds: List<String> = selectedTasks.map { it.id }

        Log.d(TAG, "taskIds: ${taskIds}")
        val jsonBody = JSONObject().apply {
            put("allTasksID", JSONArray(taskIds))
            put("userLocation", JSONObject().apply {
                put("latitude", User_Lat)
                put("longitude", User_Lng)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Failed to get tasks: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")

                    val orderedtaskIds = JSONObject(jsonResponse).getJSONArray("taskIds")
                    val taskIdList = mutableListOf<String>()

                    for (i in 0 until orderedtaskIds.length()) {
                        taskIdList.add(orderedtaskIds.getString(i))
                    }


                    val orderedTaskNames = taskIdList.mapNotNull { id ->
                        selectedTasks.find { it.id == id }?.name
                    }

                    var message = ""
                    if (orderedTaskNames.isNotEmpty()) {
                        message = "Optimal Task Order Found:\n" + orderedTaskNames.joinToString(" → ") // ✅ Format as a sequence
                    } else {
                        message = "No valid tasks found in the order."
                    }

                    val messagingService = FirebaseMessagingService()
                    messagingService.sendNotification(requireContext(), message)

                }
            }
        })
    }
}
