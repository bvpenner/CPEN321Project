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
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                Log.d(TAG, "Firebase initialized successfully")
                retrieveAndUpdateFCMToken()
            } else {
                Log.e(TAG, "Firebase initialization failed")
                // Consider implementing a retry mechanism or fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
            // Handle initialization error (e.g., missing google-services.json)
        }
    }

    private fun retrieveAndUpdateFCMToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                val token = task.result
                Log.d(TAG, "FCM Token: $token")
                // TODO: Send token to server
                applicationScope.launch(Dispatchers.IO) {
                    try {
                        // Implement token upload to server
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending FCM token to server", e)
                    }
                }
            })
    }

    private fun initializeWorkManager() {
        setupLocationTracking()
        setupRouteOptimization()
    }

    private fun setupLocationTracking() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LOCATION_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            locationWorkRequest
        )
    }

    private fun setupRouteOptimization() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val routeWorkRequest = PeriodicWorkRequestBuilder<RouteWorker>(
            30, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ROUTE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            routeWorkRequest
        )
    }

    private fun setupLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App moved to foreground")
        // Refresh data when app comes to foreground
        taskViewModel.refreshTaskList()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "App moved to background")
        // Perform cleanup or background task optimization
    }

    override fun onTerminate() {
        super.onTerminate()
        // Cleanup resources
        WorkManager.getInstance(this).cancelUniqueWork(LOCATION_WORK_NAME)
        WorkManager.getInstance(this).cancelUniqueWork(ROUTE_WORK_NAME)
    }
}