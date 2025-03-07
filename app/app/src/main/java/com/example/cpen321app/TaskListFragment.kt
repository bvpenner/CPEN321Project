package com.example.cpen321app

import FirebaseMessagingService
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.cpen321app.MapsFragment.Companion.User_Lat
import com.example.cpen321app.MapsFragment.Companion.User_Lng
import com.example.cpen321app.databinding.FragmentTaskListBinding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class TaskListFragment : Fragment(), TaskAdapter.OnTaskInteractionListener {
    private var _binding: FragmentTaskListBinding? = null
    private val binding get() = _binding!!
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var searchView: SearchView? = null
    private var filterChipGroup: ChipGroup? = null
    private var sortChip: Chip? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TaskListFragment"
        private const val BASE_URL = "http://18.215.238.145:3000"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearchView()
        setupFilterChips()
        setupSortChip()
        setupFab()
        observeViewModel()
    }

    private fun setupViewModel() {
        taskViewModel = (activity?.application as GeoTask).taskViewModel
        taskViewModel.refreshTaskList()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(activity)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        binding.buttonPlanRoute.shrink()
                    } else {
                        binding.buttonPlanRoute.extend()
                    }
                }
            })
        }
        
        taskAdapter = TaskAdapter(emptyList(), this, requireContext())
        binding.recyclerView.adapter = taskAdapter
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout = binding.swipeRefresh
        swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        )
        swipeRefreshLayout.setOnRefreshListener {
            taskViewModel.refreshTaskList()
        }
    }

    private fun setupSearchView() {
        searchView = binding.searchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            
            override fun onQueryTextChange(newText: String?): Boolean {
                taskViewModel.setFilterCriteria(
                    taskViewModel.filterCriteria.value?.copy(
                        searchQuery = newText
                    ) ?: TaskViewModel.FilterCriteria(searchQuery = newText)
                )
                return true
            }
        })
    }

    private fun setupFilterChips() {
        filterChipGroup = binding.filterChipGroup
        filterChipGroup?.setOnCheckedStateChangeListener { group, checkedIds ->
            val priority = when (checkedIds.firstOrNull()) {
                R.id.chipHigh -> Task.PRIORITY_HIGH
                R.id.chipMedium -> Task.PRIORITY_MEDIUM
                R.id.chipLow -> Task.PRIORITY_LOW
                else -> null
            }
            taskViewModel.setFilterCriteria(
                taskViewModel.filterCriteria.value?.copy(
                    priority = priority
                ) ?: TaskViewModel.FilterCriteria(priority = priority)
            )
        }
    }

    private fun setupSortChip() {
        sortChip = binding.sortChip
        sortChip?.setOnClickListener {
            showSortOptionsDialog()
        }
    }

    private fun setupFab() {
        binding.buttonPlanRoute.apply {
            setOnClickListener {
                handlePlanRoute()
            }
            ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 100f, 0f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun observeViewModel() {
        taskViewModel.taskList.observe(viewLifecycleOwner) { tasks ->
            taskAdapter.updateTasks(tasks)
            updateEmptyState(tasks.isEmpty())
        }

        taskViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            swipeRefreshLayout.isRefreshing = isLoading
        }

        taskViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { showError(it) }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateGroup.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                taskViewModel.refreshTaskList()
            }
            .show()
    }

    private fun showSortOptionsDialog() {
        val options = arrayOf("Priority", "Due Date", "Start Time", "Name")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Tasks By")
            .setItems(options) { _, which ->
                val order = when (which) {
                    0 -> TaskViewModel.SortOrder.PRIORITY
                    1 -> TaskViewModel.SortOrder.DUE_DATE
                    2 -> TaskViewModel.SortOrder.START_TIME
                    3 -> TaskViewModel.SortOrder.NAME
                    else -> TaskViewModel.SortOrder.PRIORITY
                }
                taskViewModel.setSortOrder(order)
                sortChip?.text = "Sorted by ${options[which]}"
            }
            .show()
    }

    private fun handlePlanRoute() {
        val selectedTasks = taskAdapter.getSelectedTasks()
        if (selectedTasks.isEmpty()) {
            showError("Please select tasks to plan a route")
            return
        }

        val origin = SessionManager.currentLocation?.let {
            LatLng(it.latitude, it.longitude)
        } ?: run {
            showError("Current location not available")
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                binding.buttonPlanRoute.isEnabled = false
                val response = sendGetOptimalRouteServer(selectedTasks)
                showRouteConfirmationDialog(response)
            } catch (e: Exception) {
                showError("Failed to plan route: ${e.localizedMessage}")
            } finally {
                binding.buttonPlanRoute.isEnabled = true
            }
        }
    }

    private suspend fun sendGetOptimalRouteServer(selectedTasks: List<Task>): JSONObject = withContext(Dispatchers.IO) {
        val taskIds = selectedTasks.map { it.id }
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        val jsonBody = JSONObject().apply {
            put("allTasksID", JSONArray(taskIds))
            put("userLocation", JSONObject().apply {
                put("latitude", User_Lat)
                put("longitude", User_Lng)
            })
            put("userCurrTime", currentTime)
        }

        val request = Request.Builder()
            .url("$BASE_URL/fetchOptimalRoute")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
            JSONObject(response.body?.string() ?: throw IOException("Empty response"))
        }
    }

    private fun showRouteConfirmationDialog(response: JSONObject) {
        val orderedTaskIds = response.getJSONArray("taskIds")
        val estimatedTime = response.getDouble("time_cost")
        val taskIdList = List(orderedTaskIds.length()) { orderedTaskIds.getString(it) }
        
        val orderedTasks = taskIdList.mapNotNull { id ->
            taskAdapter.getSelectedTasks().find { it.id == id }
        }

        if (orderedTasks.isEmpty()) {
            showError("No valid tasks found in the order")
            return
        }

        val message = buildString {
            append("Optimal Route Found\n\n")
            orderedTasks.forEachIndexed { index, task ->
                append("${index + 1}. ${task.name}\n")
            }
            append("\nEstimated time: $estimatedTime minutes")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Route Plan")
            .setMessage(message)
            .setPositiveButton("Open in Maps") { _, _ ->
                launchGoogleMaps(orderedTasks)
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Also send notification
        FirebaseMessagingService().sendNotification(requireContext(), message)
    }

    private fun launchGoogleMaps(tasks: List<Task>) {
        val origin = SessionManager.currentLocation?.let {
            "${it.latitude},${it.longitude}"
        } ?: return

        val destination = tasks.last()
        val waypoints = tasks.dropLast(1).joinToString("|") {
            "${it.location_lat},${it.location_lng}"
        }

        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1" +
                "&origin=$origin" +
                "&destination=${destination.location_lat},${destination.location_lng}" +
                if (waypoints.isNotEmpty()) "&waypoints=optimize:true|$waypoints" else "")

        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        })
    }

    override fun onItemClick(task: Task) {
        // Show task details in bottom sheet
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_task_details, null)
        // TODO: Populate view with task details
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    override fun onItemLongClick(task: Task): Boolean {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(task.name)
            .setItems(arrayOf("Edit", "Delete", "Share")) { _, which ->
                when (which) {
                    0 -> navigateToEditTask(task)
                    1 -> confirmDeleteTask(task)
                    2 -> shareTask(task)
                }
            }
            .show()
        return true
    }

    override fun onTaskCompleted(task: Task) {
        taskViewModel.updateTask(task.copy(isCompleted = true))
        Snackbar.make(binding.root, "Task marked as completed", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                taskViewModel.updateTask(task.copy(isCompleted = false))
            }
            .show()
    }

    private fun navigateToEditTask(task: Task) {
        // TODO: Navigate to edit task screen
    }

    private fun confirmDeleteTask(task: Task) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete '${task.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                taskViewModel.deleteTask(task)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareTask(task: Task) {
        val shareIntent = Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, """
                Task: ${task.name}
                Time: ${task.start} - ${task.end}
                Priority: ${task.priorityText}
                Description: ${task.description}
                Location: https://www.google.com/maps?q=${task.location_lat},${task.location_lng}
            """.trimIndent())
        }, "Share Task")
        startActivity(shareIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        searchView = null
        filterChipGroup = null
        sortChip = null
    }
}
