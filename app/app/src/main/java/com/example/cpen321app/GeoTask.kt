package com.example.cpen321app

import android.app.Application
import androidx.lifecycle.ViewModelProvider

class GeoTask : Application() {
    val taskViewModel: TaskViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory(this).create(TaskViewModel::class.java)
    }


}