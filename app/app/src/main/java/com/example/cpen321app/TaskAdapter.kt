package com.example.cpen321app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

//    Copilot generated
class TaskAdapter(private val tasks: List<Task>) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val taskId: TextView = itemView.findViewById(R.id.taskId)
        val taskName: TextView = itemView.findViewById(R.id.taskName)
        val taskStart: TextView = itemView.findViewById(R.id.taskStart)
        val taskEnd: TextView = itemView.findViewById(R.id.taskEnd)
        val taskLat: TextView = itemView.findViewById(R.id.taskLat)
        val taskLng: TextView = itemView.findViewById(R.id.taskLng)
        val taskPriority: TextView = itemView.findViewById(R.id.taskPriority)
        val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.taskId.text = task.id
        holder.taskName.text = task.name
        holder.taskStart.text = "Start: " + task.start
        holder.taskEnd.text = "End: " + task.end.toString()
        holder.taskLat.text = task.location_lat.toString()
        holder.taskLng.text = task.location_lng.toString()
        holder.taskPriority.text = "Priority: " + task.priority.toString()
        holder.taskDescription.text = task.description
    }

    override fun getItemCount(): Int {
        return tasks.size
    }
}