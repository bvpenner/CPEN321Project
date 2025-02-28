package com.example.cpen321app

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.FirebaseApp

class GeoTask : Application() {
    val taskViewModel: TaskViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory(this).create(TaskViewModel::class.java)
    }

    override fun onCreate() {
        super.onCreate()
//        Log.d("FirebaseInit", "Checking google-services.json...")
//        val googleAppId = getString(R.string.google_app_id)
//        Log.d("FirebaseInit", "Google App ID: $googleAppId")

        FirebaseApp.initializeApp(this)

        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.e("FirebaseInit", "FirebaseApp is NOT initialized!")
        } else {
            Log.d("FirebaseInit", "FirebaseApp initialized successfully!")
        }
    }
}