package com.example.cpen321app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class GeoTask : Application(), DefaultLifecycleObserver {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    val taskViewModel: TaskViewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory(this).create(TaskViewModel::class.java)
    }

    companion object {
        private const val TAG = "GeoTask"
        private const val LOCATION_WORK_NAME = "location_tracking"
        private const val ROUTE_WORK_NAME = "route_optimization"
        
        private var instance: GeoTask? = null
        
        fun getInstance(): GeoTask = instance ?: throw IllegalStateException("Application not created")
        
        fun getApplicationContext(): Context = getInstance().applicationContext
    }

    override fun onCreate() {
        super<Application>.onCreate()
        instance = this
        
        initializeFirebase()
        initializeWorkManager()
        setupLifecycleObserver()
    }

    private fun initializeFirebase() {
        try {
            FirebaseApp.initializeApp(this)
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener(OnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.w(TAG, "Failed to get FCM token", task.exception)
                        return@OnCompleteListener
                    }

                    val token = task.result
                    Log.d(TAG, "FCM Token: $token")
                })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }

    private fun initializeWorkManager() {
        val locationConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(locationConstraints)
            .build()

        val routeWorkRequest = PeriodicWorkRequestBuilder<RouteWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setConstraints(locationConstraints)
            .build()

        WorkManager.getInstance(this).apply {
            enqueueUniquePeriodicWork(
                LOCATION_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                locationWorkRequest
            )
            enqueueUniquePeriodicWork(
                ROUTE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                routeWorkRequest
            )
        }
    }

    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        applicationScope.launch {
            taskViewModel.refreshTaskList()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        // Clean up any resources if needed
    }

    override fun onTerminate() {
        super.onTerminate()
        WorkManager.getInstance(this).cancelUniqueWork(LOCATION_WORK_NAME)
        WorkManager.getInstance(this).cancelUniqueWork(ROUTE_WORK_NAME)
    }
}