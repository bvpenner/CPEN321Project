package com.example.cpen321app

import FirebaseMessagingService
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cpen321app.BuildConfig.MAPS_API_KEY
import com.example.cpen321app.MainActivity.Companion.getOkHttpClientWithCustomCert
import com.example.cpen321app.MapsFragment.Companion.User_Lat
import com.example.cpen321app.MapsFragment.Companion.User_Lng
import com.example.cpen321app.TaskAdapter.Companion._geofenceStateMap
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.example.cpen321app.TaskViewModel.Companion.server_ip
import com.example.cpen321app.databinding.FragmentTaskListBinding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.maps.android.PolyUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        taskViewModel.refreshTasklist()

        // Set up RecyclerView.
        binding.taskRecyclerView.layoutManager = LinearLayoutManager(activity)
        taskAdapter = TaskAdapter(taskViewModel.taskList.value ?: mutableListOf(), this, requireContext())
        binding.taskRecyclerView.adapter = taskAdapter

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
                Log.d(TAG, "Origin: $origin")
                if (origin != null) {
                    sendGetOptimalRouteServer(selectedTasks)
                } else {
                    Toast.makeText(requireContext(), "Current location not available.", Toast.LENGTH_SHORT).show()
                }
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
        // Inflate the popup menu layout
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_menu, null)

        // Create the PopupWindow
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        // Set click listeners for the buttons
        val updateButton: Button = popupView.findViewById(R.id.update_button)
        val deleteButton: Button = popupView.findViewById(R.id.delete_button)

        updateButton.setOnClickListener {
            // Handle the update action

            val updateTaskIntent = Intent(requireContext(), AddTask::class.java)
            updateTaskIntent.putExtra("id", task.id)
            updateTaskIntent.putExtra("name", task.name)
            updateTaskIntent.putExtra("start", task.start)
            updateTaskIntent.putExtra("end", task.end)
            updateTaskIntent.putExtra("duration", task.duration)
            updateTaskIntent.putExtra("location_lat", task.location_lat)
            updateTaskIntent.putExtra("location_lng", task.location_lng)
            updateTaskIntent.putExtra("priority", task.priority)
            updateTaskIntent.putExtra("description", task.description)
            updateTaskIntent.putExtra("geofence", task.isGeofenceEnabled)

            popupWindow.dismiss()

            startActivity(updateTaskIntent)

//            Toast.makeText(requireContext(), "Update action", Toast.LENGTH_SHORT).show()
//            popupWindow.dismiss()
        }

        deleteButton.setOnClickListener {
            // Handle the delete action
            taskViewModel.deleteTask(task)
            Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show()
            popupWindow.dismiss()
        }

        // Show the PopupWindow
        popupWindow.showAtLocation(view, android.view.Gravity.BOTTOM, 0, 0)

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun sendGetOptimalRouteServer(selectedTasks: List<Task>) {
        //val client = getOkHttpClientWithCustomCert(requireContext())
        val client = OkHttpClient()
        val url = "http://13.216.143.65:3001/fetchOptimalRoute"
        val taskIds: List<String> = selectedTasks.map { it.id }
        val formatter = DateTimeFormatter.ofPattern("HH:mm") // 24-hour format
        val currentTime = LocalTime.now().format(formatter)

        Log.d(TAG, currentTime)
        Log.d(TAG, "taskIds: ${taskIds}")
        val jsonBody = JSONObject().apply {
            put("allTasksID", JSONArray(taskIds))
            put("userLocation", JSONObject().apply {
                put("latitude", User_Lat)
                put("longitude", User_Lng)
            })
            put("userCurrTime", currentTime)
        }

        Log.d(TAG, "Lat: $User_Lat, Long: $User_Lng")

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        Log.d(TAG, "$request, ${request.body}")

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

                    val responseJson = JSONObject(jsonResponse)
                    val orderedtaskIds = responseJson.getJSONArray("taskIds")
                    val estimated_time = responseJson.get("time_cost")
                    val taskIdList = mutableListOf<String>()
                    val coordList = mutableListOf<LatLng>()
                    coordList.add(LatLng(User_Lat, User_Lng))

                    for (i in 0 until orderedtaskIds.length()) {
                        val taskId = orderedtaskIds.getString(i)
                        taskIdList.add(taskId)

                        val task = _taskList.value?.find { it.id == taskId }
                        task?.let {
                            coordList.add(LatLng(it.location_lat, it.location_lng))
                        }
                    }

                    val orderedTaskNames = taskIdList.mapNotNull { id ->
                        selectedTasks.find { it.id == id }?.name
                    }

                    val message = if (orderedTaskNames.isNotEmpty()) {
                        "Optimal Task Order Found:\n" +
                                orderedTaskNames.joinToString(" → ") +
                                " Estimated timecost: $estimated_time mins"
                    } else {
                        "No valid tasks found in the order."
                    }

                    val messagingService = FirebaseMessagingService()
                    messagingService.sendNotification(requireContext(), message)
//                    val routePoints = listOf(
//                        LatLng(49.2827, -123.1207), // Vancouver
//                        LatLng(48.6062, -122.3321),
//                        LatLng(47.6062, -122.3321)  // Seattle
//                    )


                    fetchAndDrawRouteFromPoints(coordList, onSuccess = { decodedlist ->
                        requireActivity().runOnUiThread {
                            val bundle = Bundle().apply {
                                putParcelableArrayList("coordList", ArrayList(decodedlist))
                            }

                            val mapsFragment = MapsFragment().apply {
                                arguments = bundle
                            }

                            val activity = requireActivity() as MainActivity
                            val navView = activity.findViewById<BottomNavigationView>(activity.bottomNavId)
                            navView.selectedItemId = R.id.map_view_button

                            activity.supportFragmentManager.beginTransaction()
                                .replace(activity.fragmentContainerId, mapsFragment)
                                .addToBackStack(null)
                                .commit()
                        }
                    })
                }
            }
        })
    }

    fun fetchAndDrawRouteFromPoints(
        points: List<LatLng>,
        retryCount: Int = 3,
        onSuccess: (List<LatLng>) -> Unit = { },
        onFailure: (() -> Unit)? = null
    ) {
        if (points.size < 2) {
            Log.w("fetchAndDrawRoute", "At least 2 points are required.")
            onFailure?.invoke()
            return
        }

        val origin = points.first()
        val destination = points.last()
        val waypoints = points.subList(1, points.size - 1)

        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        val waypointsStr = waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }

        val urlBuilder = StringBuilder("https://maps.googleapis.com/maps/api/directions/json?")
            .append("origin=$originStr")
            .append("&destination=$destStr")
            .append("&key=$MAPS_API_KEY")

        if (waypoints.isNotEmpty()) {
            val encodedWaypoints = URLEncoder.encode(waypointsStr, "UTF-8")
            urlBuilder.append("&waypoints=$encodedWaypoints")
        }

        val url = urlBuilder.toString()
        Log.d("DirectionsAPI", "Request URL: $url")

        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DirectionsAPI", "Failed to fetch route: ${e.message}")
                if (retryCount > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        fetchAndDrawRouteFromPoints(points, retryCount - 1, onSuccess, onFailure)
                    }, 3000)
                } else {
                    onFailure?.invoke()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) {
                    Log.e("DirectionsAPI", "Empty or bad response")
                    onFailure?.invoke()
                    return
                }

                try {
                    val json = JSONObject(body)
                    val routes = json.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val overviewPolyline = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points")

                        val decodedPoints = PolyUtil.decode(overviewPolyline)

                        requireActivity().runOnUiThread {
                            Log.d("DirectionsAPI", "Decoded points: $decodedPoints")
                            onSuccess(decodedPoints)
                        }
                    } else {
                        Log.e("DirectionsAPI", "No route found.")
                        onFailure?.invoke()
                    }
                } catch (e: JSONException) {
                    Log.e("DirectionsAPI", "Parsing error: ${e.message}")
                    onFailure?.invoke()
                } catch (e: IllegalStateException) {
                    Log.e("DirectionsAPI", "Parsing error: ${e.message}")
                    onFailure?.invoke()
                }
            }
        })
    }

}
