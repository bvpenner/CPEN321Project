package com.example.cpen321app

import FirebaseMessagingService
import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cpen321app.databinding.ActivityMainBinding
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.SurfaceColors
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskViewModel: TaskViewModel
    private var savedCredential: GoogleIdTokenCredential? = null
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                initializeLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                initializeLocation()
            }
            else -> {
                showLocationPermissionRationale()
            }
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showNotificationPermissionRationale()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val BASE_URL = "http://18.215.238.145:3000"
        private const val ROUTE_WORKER_TAG = "route_worker"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupWindowDecorations()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupNavigation()
        setupFab()
        
        if (savedCredential == null) {
            initiateSignIn()
        } else {
            setupApp()
        }
    }

    private fun setupWindowDecorations() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun setupViewModel() {
        taskViewModel = (application as GeoTask).taskViewModel
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNavigation.apply {
            setupWithNavController(navController)
            setOnItemReselectedListener { /* Prevent reselection reload */ }
        }
    }

    private fun setupFab() {
        binding.addTaskFab.apply {
            setOnClickListener {
                animateFab {
                    startActivity(Intent(this@MainActivity, AddTask::class.java))
                }
            }
        }
    }

    private fun animateFab(onAnimationEnd: () -> Unit) {
        ObjectAnimator.ofFloat(binding.addTaskFab, View.SCALE_X, 1f, 0.8f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.addTaskFab, View.SCALE_Y, 1f, 0.8f, 1f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            doOnEnd { onAnimationEnd() }
            start()
        }
    }

    private fun initiateSignIn() {
        lifecycleScope.launch {
            try {
                val credentialManager = CredentialManager.create(this@MainActivity)
                val rawNonce = UUID.randomUUID().toString()
                val hashedNonce = MessageDigest.getInstance("SHA-256")
                    .digest(rawNonce.toByteArray())
                    .fold("") { str, it -> str + "%02x".format(it) }

                val signInOption = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID)
                    .setNonce(hashedNonce)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val result = credentialManager.getCredential(request, this@MainActivity)
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                showError("Sign in failed: ${e.localizedMessage}")
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val claims = decodeIdToken(googleCredential.idToken)
                        val name = claims["name"] as? String ?: "Unknown"
                        val email = claims["email"] as? String ?: "Unknown"
                        val userId = claims["sub"] as? String ?: "Unknown"
                        
                        savedCredential = googleCredential
                        SessionManager.u_id = userId
                        
                        lifecycleScope.launch {
                            try {
                                sendLoginRequest(userId, name, email)
                                setupApp()
                            } catch (e: Exception) {
                                showError("Failed to initialize: ${e.localizedMessage}")
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        showError("Invalid Google ID token")
                    }
                }
            }
            else -> showError("Unexpected credential type")
        }
    }

    private fun setupApp() {
        requestPermissions()
        initializeLocation()
        scheduleRouteWorker()
        taskViewModel.refreshTaskList()
    }

    private fun requestPermissions() {
        // Location permissions
        if (!hasLocationPermissions()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun initializeLocation() {
        if (!hasLocationPermissions()) return
        
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                location?.let {
                    SessionManager.currentLocation = it
                    Log.d(TAG, "Location updated: ${it.latitude}, ${it.longitude}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get location", e)
            }
    }

    private fun scheduleRouteWorker() {
        val routeWorkRequest = PeriodicWorkRequestBuilder<RouteWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ROUTE_WORKER_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            routeWorkRequest
        )
    }

    private suspend fun sendLoginRequest(userId: String, name: String, email: String) = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("u_id", userId)
            put("name", name)
            put("email", email)
        }

        val request = Request.Builder()
            .url("$BASE_URL/login")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
        }
    }

    private fun showLocationPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location access to plan optimal routes for your tasks and provide location-based reminders.")
            .setPositiveButton("Grant Permission") { _, _ ->
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun showNotificationPermissionRationale() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Notification Permission Required")
            .setMessage("Enable notifications to receive important updates about your tasks and route optimizations.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                if (savedCredential == null) {
                    initiateSignIn()
                } else {
                    setupApp()
                }
            }
            .show()
    }

    private fun decodeIdToken(idToken: String): Map<String, Any> {
        val parts = idToken.split(".")
        val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
        return JSONObject(payload).toMap()
    }

    private fun JSONObject.toMap(): Map<String, Any> =
        keys().asSequence().associateWith { key ->
            when (val value = this[key]) {
                is JSONObject -> value.toMap()
                JSONObject.NULL -> null
                else -> value
            }
        }
}
