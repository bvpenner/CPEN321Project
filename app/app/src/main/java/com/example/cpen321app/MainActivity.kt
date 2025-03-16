package com.example.cpen321app

import FirebaseMessagingService
import android.app.Application
import com.example.cpen321app.TaskListFragment
import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.example.cpen321app.SessionManager
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var savedCredential: GoogleIdTokenCredential? = null
    private var screen: String = "map"
    private lateinit var taskViewModel: TaskViewModel
    private var server_ip = "13.216.143.65:3000"

    private lateinit var securePreferences: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        taskViewModel = (application as GeoTask).taskViewModel

        securePreferences = SecurePreferences(applicationContext)
        
        // Get Login Data
        val name = securePreferences.getSecureValue("name")
        val email = securePreferences.getSecureValue("email")
        val u_id = securePreferences.getSecureValue("u_id")

        if(name == null || email == null || u_id == null) {
            val credentialManager = CredentialManager.create(this)
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }
            Log.d(TAG, "WEB_CLIENT_ID: ${BuildConfig.WEB_CLIENT_ID}")


            val signInWithGoogleOption: GetSignInWithGoogleOption = GetSignInWithGoogleOption
                .Builder(BuildConfig.WEB_CLIENT_ID)
                .setNonce(hashedNonce)
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            activityScope.launch {
                try {
                    val result = credentialManager.getCredential(request = request, context = this@MainActivity)
                    handleSignIn(result)
                } catch (e: GetCredentialException) {
                    handleFailure(e)
                }
            }
        } else {
            signInToBackend(u_id, name, email)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "savedCredential: $savedCredential")
    }

    private fun handleFailure(e: GetCredentialException) {
        Log.e(TAG, "Log In Failed", e)
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        Log.d(TAG, "Log In Succeeded")
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        Log.d(TAG, credential.data.toString())
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val claims = decodeIdToken(googleIdTokenCredential.idToken)
                        val name = claims["name"] as? String ?: "Unknown"
                        val email = claims["email"] as? String ?: "Unknown"
                        val u_id = claims["sub"] as? String ?: "Unknown"

                        securePreferences.saveSecureValue("name", name)
                        securePreferences.saveSecureValue("email", email)
                        securePreferences.saveSecureValue("u_id", u_id)

                        signInToBackend(u_id, name, email)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid Google ID token", e)
                    }
                } else {
                    Log.e(TAG, "Unexpected type of credential")
                }
            }
            else -> Log.e(TAG, "Unexpected credential")
        }
    }

    private fun signInToBackend(u_id: String, name: String, email: String) {
        SessionManager.u_id = u_id
        sendLoginRequestToServer(u_id, name, email)
        sendGetAllTasksToServer(u_id)
        signInSuccess()
    }

    private fun signInSuccess() {

        // Fetch the initial location and store it in SessionManager.
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                SessionManager.currentLocation = location
                Log.d(TAG, "Initial location obtained: ${location.latitude}, ${location.longitude}")
            } else {
                Log.d(TAG, "Initial location is null; will try again later.")
            }
        }

        // Check location permissions.
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !coarseGranted) {
            requestLocationPermissions()
        }

        // Request POST_NOTIFICATIONS permission on Android 13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }

        // Schedule the RouteWorker.
        scheduleRouteWorker()

        // For testing: update the task list (you can replace this in production).
        // taskViewModel.updateTaskListTesting()

        // Set up UI components.
        setUpUIComponents()
    }

    private fun setUpUIComponents() {
        val frameLayout = FrameLayout(this).apply { id = View.generateViewId() }
        val bottomNavigationView = BottomNavigationView(this).apply {
            id = View.generateViewId()
            inflateMenu(R.menu.bottom_nav_menu)
        }
        val constraintLayout = findViewById<ConstraintLayout>(R.id.main)
        val frameLayoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        )
        frameLayout.layoutParams = frameLayoutParams
        val bottomNavLayoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        bottomNavigationView.layoutParams = bottomNavLayoutParams
        setUpConstraintLayout(constraintLayout, frameLayout, bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.list_view_button -> {
                    supportFragmentManager.beginTransaction()
                        .replace(frameLayout.id, TaskListFragment()).commit()
                    true
                }

                R.id.map_view_button -> {
                    supportFragmentManager.beginTransaction()
                        .replace(frameLayout.id, MapsFragment()).commit()
                    true
                }
                else -> false
            }
        }
        supportFragmentManager.beginTransaction().replace(frameLayout.id, MapsFragment()).commit()
        val addTaskButton = Button(this).apply {
            text = "+"
            textSize = 24f
            layoutParams = ConstraintLayout.LayoutParams(150, 150)
            id = View.generateViewId()
            background =
                AppCompatResources.getDrawable(this@MainActivity, R.drawable.add_task_button)
        }
        constraintLayout.addView(addTaskButton)
        val addTaskButtonConstraintSet = ConstraintSet()
        addTaskButtonConstraintSet.clone(constraintLayout)
        addTaskButtonConstraintSet.connect(
            addTaskButton.id,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END,
            60
        )
        addTaskButtonConstraintSet.connect(
            addTaskButton.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP,
            60
        )
        addTaskButtonConstraintSet.applyTo(constraintLayout)
        addTaskButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, AddTask::class.java))
        }

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        constraintLayout.removeView(progressBar)
    }
    
    private fun setUpConstraintLayout(
        constraintLayout: ConstraintLayout,
        frameLayout: FrameLayout,
        bottomNavigationView: BottomNavigationView
    ) {
        constraintLayout.addView(frameLayout)
        constraintLayout.addView(bottomNavigationView)
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        constraintSet.connect(
            frameLayout.id,
            ConstraintSet.TOP,
            ConstraintSet.PARENT_ID,
            ConstraintSet.TOP
        )
        constraintSet.connect(
            frameLayout.id,
            ConstraintSet.BOTTOM,
            bottomNavigationView.id,
            ConstraintSet.TOP
        )
        constraintSet.connect(
            frameLayout.id,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START
        )
        constraintSet.connect(
            frameLayout.id,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END
        )
        constraintSet.connect(
            bottomNavigationView.id,
            ConstraintSet.BOTTOM,
            ConstraintSet.PARENT_ID,
            ConstraintSet.BOTTOM
        )
        constraintSet.connect(
            bottomNavigationView.id,
            ConstraintSet.START,
            ConstraintSet.PARENT_ID,
            ConstraintSet.START
        )
        constraintSet.connect(
            bottomNavigationView.id,
            ConstraintSet.END,
            ConstraintSet.PARENT_ID,
            ConstraintSet.END
        )
        constraintSet.applyTo(constraintLayout)
    }

    private fun scheduleRouteWorker() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted && coarseGranted) {
            val routeWorkRequest = PeriodicWorkRequestBuilder<RouteWorker>(30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "RouteWorker",
                ExistingPeriodicWorkPolicy.REPLACE,
                routeWorkRequest
            )
            Log.d(TAG, "RouteWorker scheduled.")
        } else {
            Log.d(TAG, "RouteWorker not scheduled: Location permissions not granted.")
        }
    }

    private fun requestLocationPermissions() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 0)
    }

    private fun showLocationPermissionRationale() {
        AlertDialog.Builder(this)
            .setMessage("Location permission is required for task notifications.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                requestLocationPermissions()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Please grant the location permissions", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun sendLoginRequestToServer(u_id: String, name: String, email: String) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/login"

        // Build JSON body
        val jsonBody = JSONObject().apply {
            put("u_id", u_id)
            put("name", name)
            put("email", email)
        }

        Log.d(TAG, jsonBody.toString());
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Failed to send login request: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received login response: $jsonResponse")
                    val resultJson = JSONObject(jsonResponse)
                    val is_new_user = resultJson.getString("is_new").toInt()

                    if (is_new_user == 1){
                        taskViewModel.addExampleTask()
                    }
                }
            }
        })
    }

//    private fun sendGetAllTasksToServer(u_id: String) {
//        val client = OkHttpClient()
//        val url = "http://${server_ip}/getAllTasks"
//
//        val jsonBody = JSONObject().apply {
//            put("u_id", u_id)
//        }
//        Log.d(TAG, jsonBody.toString())
//        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
//        val requestBody = jsonBody.toString().toRequestBody(mediaType)
//        val request = Request.Builder()
//            .url(url)
//            .post(requestBody)
//            .build()
//        client.newCall(request).enqueue(object : okhttp3.Callback {
//            override fun onFailure(call: okhttp3.Call, e: IOException) {
//                Log.e(TAG, "Failed to get tasks: ${e.message}")
//            }
//            override fun onResponse(call: okhttp3.Call, response: Response) {
//                if (!response.isSuccessful) {
//                    Log.e(TAG, "Unexpected response: ${response.message}")
//                    return
//                }
//                response.body?.string()?.let { jsonResponse ->
//                    Log.d(TAG, "Received response: $jsonResponse")
//                    val resultJson = JSONObject(jsonResponse)
//                    val taskListJsonArray = resultJson.getJSONArray("task_list")
//
//                    val taskList = mutableListOf<Task>()
//                    for (i in 0 until taskListJsonArray.length()) {
//                        val taskJson = taskListJsonArray.getJSONObject(i)
//                        val task = Task(
//                            id = taskJson.getString("_id"),
//                            name = taskJson.getString("name"),
//                            start = taskJson.getString("start"),
//                            end = taskJson.getString("end"),
//                            duration = taskJson.getDouble("duration"),
//                            location_lat = taskJson.getDouble("location_lat"),
//                            location_lng = taskJson.getDouble("location_lng"),
//                            priority = taskJson.getInt("priority"),
//                            description = taskJson.getString("description")
//                        )
//                        // taskList.add(task)
//                        _taskList.value?.add(task)
//                    }
//                }
//            }
//        })
//    }

    private fun sendGetAllTasksToServer(u_id: String) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/getAllTasks?u_id=${u_id}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Failed to get tasks: ${e.message}")
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")
                    val resultJson = JSONObject(jsonResponse)
                    val taskListJsonArray = resultJson.getJSONArray("task_list")

                    val taskList = mutableListOf<Task>()
                    for (i in 0 until taskListJsonArray.length()) {
                        val taskJson = taskListJsonArray.getJSONObject(i)
                        val task = Task(
                            id = taskJson.getString("_id"),
                            name = taskJson.getString("name"),
                            start = taskJson.getString("start"),
                            end = taskJson.getString("end"),
                            duration = taskJson.getInt("duration"),
                            location_lat = taskJson.getDouble("location_lat"),
                            location_lng = taskJson.getDouble("location_lng"),
                            priority = taskJson.getInt("priority"),
                            description = taskJson.getString("description")
                        )
                        _taskList.value?.add(task)
                    }

                }
            }
        })
    }

    fun decodeIdToken(idToken: String): Map<String, Any> {
        val parts = idToken.split(".")
        if (parts.size == 3) {
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            val jsonObject = JSONObject(decodedString)
            return jsonObject.toMap()
        } else {
            return emptyMap()
        }
    }

    fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = get(key)
        }
        return map
    }
}
