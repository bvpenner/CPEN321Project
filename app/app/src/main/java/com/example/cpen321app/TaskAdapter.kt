package com.example.cpen321app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private val tasks: MutableList<Task>,
    private val longClickListener: OnItemLongClickListener,
    private val context: Context
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val selectedTasks = mutableSetOf<Task>()

    companion object {
        val _geofenceStateMap = mutableMapOf<String, Boolean>()
    }

    interface OnItemLongClickListener {
        fun onItemLongClick(task: Task): Boolean
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task)

        holder.itemView.setOnLongClickListener {
            longClickListener.onItemLongClick(task)
        }

        holder.checkBox.isChecked = selectedTasks.contains(task)
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            val animation = AlphaAnimation(0.5f, 1f).apply {
                duration = 300
                fillAfter = true
            }
            holder.itemView.startAnimation(animation)

            if (isChecked) selectedTasks.add(task) else selectedTasks.remove(task)
            updateItemBackground(holder.itemView, isChecked)
        }

        updateItemBackground(holder.itemView, holder.checkBox.isChecked)

        holder.switchGeofence.setOnCheckedChangeListener(null)
        holder.switchGeofence.isChecked = _geofenceStateMap[task.id] ?: false
        holder.switchGeofence.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            _geofenceStateMap[task.id] = isChecked
        }
    }

    fun getSelectedTasks(): List<Task> = selectedTasks.toList()

    private fun updateItemBackground(view: View, isSelected: Boolean) {
        val background = GradientDrawable().apply {
            cornerRadius = 24f
            setStroke(4, if (isSelected) Color.parseColor("#4CAF50") else Color.TRANSPARENT)
            setColor(
                if (isSelected)
                    ContextCompat.getColor(context, R.color.pressedBackground)
                else
                    ContextCompat.getColor(context, R.color.normalBackground)
            )
        }
        view.background = background
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox_select)
        val taskName: TextView = itemView.findViewById(R.id.taskName)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        val taskStart: TextView = itemView.findViewById(R.id.taskStart)
        val taskEnd: TextView = itemView.findViewById(R.id.taskEnd)
        val taskPriority: TextView = itemView.findViewById(R.id.taskPriority)
        val switchGeofence: Switch = itemView.findViewById(R.id.switch_geofence)

        fun bind(task: Task) {
            taskName.text = task.name
            taskDescription.text = task.description
            taskStart.text = "Start: ${task.start}"
            taskEnd.text = "End: ${task.end}"
            taskPriority.text = "Priority: ${task.priority}"
        }
    }
}
