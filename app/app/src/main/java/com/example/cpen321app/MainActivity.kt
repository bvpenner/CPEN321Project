package com.example.cpen321app

import TaskListFragment
import android.app.Application
import android.content.Intent
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
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cpen321app.TaskViewModel.Companion
import com.example.cpen321app.TaskViewModel.Companion._taskList
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

object SessionManager {
    var u_id: String? = null
}

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var savedCredential: GoogleIdTokenCredential? = null
    private var firstLogIn = 1
    private var screen: String = "map"
    private lateinit var taskViewModel: TaskViewModel
    private var server_ip = "18.215.238.145:3000";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        taskViewModel = (application as GeoTask).taskViewModel

        val credentialManager = CredentialManager.create(this)

        val rawNonce = UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        Log.d(TAG, "WEB_CLIENT_ID: ${BuildConfig.WEB_CLIENT_ID}")

        val signInWithGoogleOption: GetSignInWithGoogleOption = GetSignInWithGoogleOption
            .Builder(BuildConfig.WEB_CLIENT_ID)
            .setNonce(hashedNonce).build()

        val request: GetCredentialRequest = GetCredentialRequest
            .Builder().addCredentialOption(signInWithGoogleOption).build()
        Log.d(TAG, "prepare sign in")
        activityScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                handleFailure(e)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "savedCredential:" + savedCredential.toString())

//        if(firstLogIn == 1) {
//            firstLogIn = 0
//        } else {
//            savedCredential?.let {
//                signInSuccess(it)
//
//                if(screen == "map") {
//                    findViewById<Button>(R.id.map_view_button).performClick()
//                } else {
//                    findViewById<Button>(R.id.list_view_button).performClick()
//                }
//
//            }
//        }
    }

    private fun handleFailure(e: GetCredentialException) {
        Log.e(TAG, "Log In Failed", e)
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        Log.d(TAG, "Log In Succeeded")

        val credential = result.credential

        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                        val claims = decodeIdToken(googleIdTokenCredential.idToken)
                        val name = claims["name"] as? String ?: "Unknown"
                        val email = claims["email"] as? String ?: "Unknown"
                        val u_id = claims["sub"] as? String ?: "Unknown"
                        SessionManager.u_id = u_id
                        sendLoginRequestToServer(u_id, name, email)
                        sendGetAllTasksToServer(u_id)

                        signInSuccess(googleIdTokenCredential)

                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Received an invalid google id token response", e)
                    }
                } else {
                    Log.e(TAG, "Unexpected type of credential")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected type of credential")
            }
        }
    }

    private fun signInSuccess(googleCredential: GoogleIdTokenCredential) {

        // Populate Display with buttons and register handlers. Generated by copilot
        savedCredential = googleCredential

        // Code to populate the task list with some default tasks
        taskViewModel.updateTaskListTesting()

        val frameLayout = FrameLayout(this).apply {
            id = View.generateViewId()
        }

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
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        bottomNavigationView.layoutParams = bottomNavLayoutParams

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

        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.list_view_button -> {
                    // Replace the container with TaskListFragment
                    screen = "list"
                    supportFragmentManager.beginTransaction()
                        .replace(frameLayout.id, TaskListFragment())
                        .commit()
                    true
                }

                R.id.map_view_button -> {
                    // Replace the container with MapViewFragment
                    screen = "map"
                    supportFragmentManager.beginTransaction()
                        .replace(frameLayout.id, MapsFragment())
                        .commit()
                    true
                }

                else -> {
                    false
                }

            }
        }

        // Select the map-view to start with.
        // supportFragmentManager.beginTransaction().replace(frameLayout.id, MapViewFragment()).commit()
        supportFragmentManager.beginTransaction().replace(frameLayout.id, MapsFragment()).commit()

        val addTaskButton = Button(this)
        addTaskButton.text = "+"
        addTaskButton.textSize = 24f
        addTaskButton.layoutParams = ConstraintLayout.LayoutParams(
            150,
            150
        )

        addTaskButton.id = View.generateViewId()

        addTaskButton.background = AppCompatResources.getDrawable(this, R.drawable.add_task_button)

        constraintLayout.addView(addTaskButton)

        val addTaskButtonConstraintSet = ConstraintSet()
        addTaskButtonConstraintSet.clone(constraintLayout)
        addTaskButtonConstraintSet.connect(addTaskButton.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 60)
        addTaskButtonConstraintSet.connect(addTaskButton.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 60)
        addTaskButtonConstraintSet.applyTo(constraintLayout)

        addTaskButton.setOnClickListener {
            val intent = Intent(this@MainActivity, AddTask::class.java)
            startActivity(intent)
        }



        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        constraintLayout.removeView(progressBar)

    }

    private fun sendLoginRequestToServer(u_id:String, name: String, email: String) {
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

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send fetchRoute request: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Unexpected response: ${response.message}")
                    return
                }
                response.body?.string()?.let { jsonResponse ->
                    Log.d(TAG, "Received response: $jsonResponse")

                    val resultJson = JSONObject(jsonResponse)
                    val new_user_id = resultJson.getString("new_user_id")
                    Log.d(TAG, "Login Success: Received user_id: $new_user_id")
                }
            }
        })
    }

    private fun sendGetAllTasksToServer(u_id:String) {
        val client = OkHttpClient()
        val url = "http://${server_ip}/getAllTasks"

        val jsonBody = JSONObject().apply {
            put("u_id", u_id)
        }

        Log.d(TAG, jsonBody.toString());
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send fetchRoute request: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
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
                            duration = taskJson.getDouble("duration"),
                            location_lat = taskJson.getDouble("location_lat"),
                            location_lng = taskJson.getDouble("location_lng"),
                            priority = taskJson.getInt("priority"),
                            description = taskJson.getString("description")
                        )
                        // taskList.add(task)
                        _taskList.value?.add(task)
                    }
                }
            }
        })
    }

    fun decodeIdToken(idToken: String): Map<String, Any> {
        return try {
            // Split the ID token into its parts
            val parts = idToken.split(".")
            if (parts.size == 3) {
                val payload = parts[1]
                val decodedBytes = Base64.decode(payload, Base64.URL_SAFE)
                val decodedString = String(decodedBytes, Charsets.UTF_8)
                val jsonObject = JSONObject(decodedString)

                jsonObject.toMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error decoding ID token: ${e.message}")
            emptyMap()
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