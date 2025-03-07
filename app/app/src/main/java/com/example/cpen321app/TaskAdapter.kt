package com.example.cpen321app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.util.*

class TaskAdapter(
    private var tasks: List<Task>,
    private val listener: OnTaskInteractionListener,
    private val context: Context
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // Track the IDs of selected tasks.
    private val selectedTaskIds = mutableSetOf<String>()
    private var filteredTasks = tasks

    interface OnTaskInteractionListener {
        fun onItemLongClick(task: Task): Boolean
        fun onItemClick(task: Task)
        fun onTaskCompleted(task: Task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskId: TextView = itemView.findViewById(R.id.taskId)
        val taskName: TextView = itemView.findViewById(R.id.taskName)
        val taskStart: TextView = itemView.findViewById(R.id.taskStart)
        val taskEnd: TextView = itemView.findViewById(R.id.taskEnd)
        val taskLat: TextView = itemView.findViewById(R.id.taskLat)
        val taskLng: TextView = itemView.findViewById(R.id.taskLng)
        val taskPriority: Chip = itemView.findViewById(R.id.taskPriority)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        val checkBoxSelect: CheckBox = itemView.findViewById(R.id.checkBox_select)

        init {
            itemView.setOnLongClickListener {
                listener.onItemLongClick(filteredTasks[adapterPosition])
                true
            }

            itemView.setOnClickListener {
                listener.onItemClick(filteredTasks[adapterPosition])
            }

            checkBoxSelect.setOnCheckedChangeListener { _, isChecked ->
                val task = filteredTasks[adapterPosition]
                handleTaskSelection(task, isChecked)
                animateSelection(itemView, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = filteredTasks[position]
        
        with(holder) {
            taskId.text = task.id
            taskName.text = task.name
            taskStart.text = task.start
            taskEnd.text = task.end
            taskLat.text = task.location_lat.toString()
            taskLng.text = task.location_lng.toString()
            
            // Set priority chip style and text
            taskPriority.text = task.priorityText
            setPriorityChipStyle(taskPriority, task.priority)
            
            taskDescription.text = task.description
            checkBoxSelect.isChecked = selectedTaskIds.contains(task.id)

            // Apply visual states
            itemView.alpha = if (task.isCompleted) 0.6f else 1.0f
            if (task.isOverdue) {
                taskEnd.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            }

            // Show time until start if task hasn't started
            val timeUntilStart = task.timeUntilStart
            if (timeUntilStart > 0) {
                taskStart.text = formatTimeUntilStart(timeUntilStart)
            }
        }
    }

    override fun getItemCount(): Int = filteredTasks.size

    fun updateTasks(newTasks: List<Task>) {
        val diffCallback = TaskDiffCallback(filteredTasks, newTasks)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        tasks = newTasks
        filteredTasks = newTasks
        diffResult.dispatchUpdatesTo(this)
    }

    fun getSelectedTasks(): List<Task> = tasks.filter { selectedTaskIds.contains(it.id) }

    fun filterTasks(query: String) {
        filteredTasks = if (query.isEmpty()) {
            tasks
        } else {
            tasks.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun filterByPriority(priority: Int?) {
        filteredTasks = if (priority == null) {
            tasks
        } else {
            tasks.filter { it.priority == priority }
        }
        notifyDataSetChanged()
    }

    fun sortTasks(sortBy: SortOption) {
        filteredTasks = when (sortBy) {
            SortOption.PRIORITY -> filteredTasks.sortedByDescending { it.priority }
            SortOption.DUE_DATE -> filteredTasks.sortedBy { it.endDate }
            SortOption.CREATED_DATE -> filteredTasks.sortedBy { it.lastModified }
        }
        notifyDataSetChanged()
    }

    private fun handleTaskSelection(task: Task, isSelected: Boolean) {
        if (isSelected) {
            selectedTaskIds.add(task.id)
        } else {
            selectedTaskIds.remove(task.id)
        }
    }

    private fun animateSelection(view: View, isSelected: Boolean) {
        val animator = ValueAnimator.ofFloat(1f, 0.95f, 1f)
        animator.duration = 200
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            view.scaleX = animation.animatedValue as Float
            view.scaleY = animation.animatedValue as Float
        }
        animator.start()
    }

    private fun setPriorityChipStyle(chip: Chip, priority: Int) {
        val (backgroundColor, textColor) = when (priority) {
            Task.PRIORITY_HIGH -> Pair(Color.parseColor("#FFE0E0"), Color.parseColor("#C62828"))
            Task.PRIORITY_MEDIUM -> Pair(Color.parseColor("#FFF3E0"), Color.parseColor("#EF6C00"))
            Task.PRIORITY_LOW -> Pair(Color.parseColor("#E8F5E9"), Color.parseColor("#2E7D32"))
            else -> Pair(Color.LTGRAY, Color.DKGRAY)
        }
        
        chip.setChipBackgroundColorResource(android.R.color.transparent)
        chip.chipStrokeWidth = 1f
        chip.setChipStrokeColorResource(android.R.color.darker_gray)
        chip.setTextColor(textColor)
        chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(backgroundColor)
    }

    private fun formatTimeUntilStart(timeInMillis: Long): String {
        val hours = timeInMillis / (1000 * 60 * 60)
        val minutes = (timeInMillis / (1000 * 60)) % 60
        return when {
            hours > 24 -> "Starts in ${hours / 24} days"
            hours > 0 -> "Starts in ${hours}h ${minutes}m"
            minutes > 0 -> "Starts in ${minutes}m"
            else -> "Starting soon"
        }
    }

    enum class SortOption {
        PRIORITY, DUE_DATE, CREATED_DATE
    }

    private class TaskDiffCallback(
        private val oldList: List<Task>,
        private val newList: List<Task>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
