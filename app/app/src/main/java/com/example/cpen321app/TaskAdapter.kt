package com.example.cpen321app

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val tasks: List<Task>,
    private val listener: OnItemLongClickListener,
    private val context: Context,
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    companion object {
        val _geofenceStateMap: MutableMap<String, Boolean> = mutableMapOf()
    }

    // Track the IDs of selected tasks.
    private val selectedTaskIds = mutableSetOf<String>()

    interface OnItemLongClickListener {
        fun onItemLongClick(task: Task): Boolean
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnLongClickListener {
        val taskId: TextView = itemView.findViewById(R.id.taskId)
        val taskName: TextView = itemView.findViewById(R.id.taskName)
        val taskStart: TextView = itemView.findViewById(R.id.taskStart)
        val taskEnd: TextView = itemView.findViewById(R.id.taskEnd)
        val taskLat: TextView = itemView.findViewById(R.id.taskLat)
        val taskLng: TextView = itemView.findViewById(R.id.taskLng)
        val taskPriority: TextView = itemView.findViewById(R.id.taskPriority)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        val checkBoxSelect: CheckBox = itemView.findViewById(R.id.checkBox_select)
        val geofenceSwitch: Switch = itemView.findViewById(R.id.switch_geofence)

        init {
            itemView.setOnLongClickListener(this)
            checkBoxSelect.setOnCheckedChangeListener { _, isChecked ->
                val task = tasks[adapterPosition]
                if (isChecked) {
                    selectedTaskIds.add(task.id)
                } else {
                    selectedTaskIds.remove(task.id)
                }
            }

            geofenceSwitch.setOnCheckedChangeListener { _, isChecked ->
                val task = tasks[adapterPosition]
                _geofenceStateMap[task.id] = isChecked
                Log.d("TaskAdapter", "Task ${task.id} geofence enabled: $isChecked")
            }
        }

        override fun onLongClick(v: View?): Boolean {
            return listener.onItemLongClick(tasks[adapterPosition])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskId.text = task.id
        holder.taskName.text = task.name
        holder.taskStart.text = "Start: ${task.start}"
        holder.taskEnd.text = "End: ${task.end}"
        holder.taskLat.text = task.location_lat.toString()
        holder.taskLng.text = task.location_lng.toString()
        holder.taskPriority.text = "Priority: ${task.priority}"
        holder.taskDescription.text = task.description
        // Set checkbox state based on whether the task is selected.
        holder.checkBoxSelect.isChecked = selectedTaskIds.contains(task.id)
        holder.geofenceSwitch.setOnCheckedChangeListener(null)
        holder.geofenceSwitch.isChecked = _geofenceStateMap[task.id] ?: task.isGeofenceEnabled
        holder.geofenceSwitch.setOnCheckedChangeListener { _, isChecked ->
            _geofenceStateMap[task.id] = isChecked
            // Log.d("TaskAdapter", "Task ${task.id} geofence enabled: $isChecked")
            val activeGeofences = _geofenceStateMap.filterValues { it }
            Log.d("TaskAdapter", "Active Geofences: $activeGeofences")
        }
    }

    override fun getItemCount(): Int = tasks.size

    // Return the list of tasks that are currently selected.
    fun getSelectedTasks(): List<Task> {
        return tasks.filter { selectedTaskIds.contains(it.id) }
    }
}
