package com.example.cpen321app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// Generated by Copilot, aside from the backend code
class TaskViewModel : ViewModel() {
    private val _taskList = MutableLiveData<MutableList<Task>>()
    val taskList: LiveData<MutableList<Task>> get() = _taskList

    init {
        _taskList.value = mutableListOf(
            Task("1", "Visit Canadian Tire", "15/03/2025", 4, 49.2099188, -123.1075474, 1, "Pick up new summer tires"),
            Task("2", "Grocery Shopping", "28/02/2025", 4, 49.2085271,-123.0996029, 1, "Buy Groceries"),
            Task("3", "CPEN 321 Midterm Pick Up", "28/02/2025", 4, 49.2624275, -123.2502343, 1, "Pick up my midterm 1 from Professor's office")
        )
    }

    fun addTask(task: Task) {

        // Add task to backend

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

    private fun <T> MutableLiveData<T>.notifyObservers() {
        this.value = this.value
    }

}