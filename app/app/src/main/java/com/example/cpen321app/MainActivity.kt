package com.example.cpen321app

import TaskListFragment
import android.app.Application
import android.content.Intent
import android.os.Bundle
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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val activityScope = CoroutineScope(Dispatchers.Main)
    private var savedCredential: GoogleIdTokenCredential? = null
    private var firstLogIn = 1
    private var screen: String = "map"
    private lateinit var taskViewModel: TaskViewModel

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

        val signInWithGoogleOption: GetSignInWithGoogleOption = GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).setNonce(hashedNonce).build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder().addCredentialOption(signInWithGoogleOption).build()

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

        Log.d(TAG, savedCredential.toString())

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

        when(credential) {
            is CustomCredential -> {
                if(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

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
                        .replace(frameLayout.id, MapViewFragment())
                        .commit()
                    true
                }

                else -> {
                    false
                }

            }
        }

        // Select the map-view to start with.
        supportFragmentManager.beginTransaction().replace(frameLayout.id, MapViewFragment()).commit()

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

}