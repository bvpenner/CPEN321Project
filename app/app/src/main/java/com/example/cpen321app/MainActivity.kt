package com.example.cpen321app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.cpen321app.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.gms.auth.api.identity.GetCredentialResponse
import com.google.android.gms.auth.api.identity.GoogleIdTokenCredential
import com.google.android.gms.auth.api.identity.CustomCredential
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var taskViewModel: TaskViewModel
    private lateinit var credentialManager: SignInClient
    private var savedCredential: GoogleIdTokenCredential? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_ONE_TAP = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        taskViewModel = (application as GeoTask).taskViewModel
        credentialManager = Identity.getSignInClient(this)
        
        startSignIn()
    }

    private fun startSignIn() {
        lifecycleScope.launch {
            try {
                val request = BeginSignInRequest.builder()
                    .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                            .setSupported(true)
                            .setServerClientId(getString(R.string.default_web_client_id))
                            .setFilterByAuthorizedAccounts(true)
                            .build()
                    )
                    .build()

                val result = credentialManager.beginSignIn(request).await()
                startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    REQ_ONE_TAP,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            } catch (e: Exception) {
                showError("Failed to start sign in: ${e.localizedMessage}")
                Log.e(TAG, "Error starting sign in", e)
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
                        val name = claims["name"] as? String ?: throw IllegalStateException("Name not found in token")
                        val email = claims["email"] as? String ?: throw IllegalStateException("Email not found in token")
                        val userId = claims["sub"] as? String ?: throw IllegalStateException("User ID not found in token")
                        
                        savedCredential = googleCredential
                        SessionManager.u_id = userId
                        SessionManager.userName = name
                        SessionManager.userEmail = email
                        
                        lifecycleScope.launch {
                            try {
                                sendLoginRequest(userId, name, email)
                                setupApp()
                                showSnackbar("Welcome, $name!")
                            } catch (e: Exception) {
                                showError("Failed to initialize: ${e.localizedMessage}")
                                Log.e(TAG, "Error during initialization", e)
                                signOut()
                            }
                        }
                    } catch (e: GoogleIdTokenParsingException) {
                        showError("Invalid Google ID token")
                        Log.e(TAG, "Error parsing Google ID token", e)
                        signOut()
                    } catch (e: IllegalStateException) {
                        showError(e.message ?: "Invalid token data")
                        Log.e(TAG, "Missing required token data", e)
                        signOut()
                    }
                } else {
                    showError("Unsupported credential type")
                    signOut()
                }
            }
            else -> {
                showError("Unexpected credential type")
                signOut()
            }
        }
    }

    private fun setupApp() {
        requestPermissions()
        initializeLocation()
        scheduleRouteWorker()
        taskViewModel.refreshTaskList()
        
        // Set up navigation
        binding.bottomNavigation.apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_tasks -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, TaskListFragment())
                            .commit()
                        true
                    }
                    R.id.navigation_map -> {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, MapViewFragment())
                            .commit()
                        true
                    }
                    else -> false
                }
            }
            selectedItemId = R.id.navigation_tasks
        }
        
        // Set up FAB
        binding.addTaskFab.apply {
            show()
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AddTask::class.java))
            }
        }
    }

    private fun signOut() {
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentials()
                SessionManager.clear()
                savedCredential = null
                startSignIn()
            } catch (e: Exception) {
                showError("Failed to sign out: ${e.localizedMessage}")
                Log.e(TAG, "Error during sign out", e)
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") {
                startSignIn()
            }
            .show()
    }

    private fun decodeIdToken(idToken: String): Map<String, Any> {
        val parts = idToken.split(".")
        if (parts.size != 3) throw IllegalArgumentException("Invalid ID token format")
        
        val payload = parts[1]
        val decodedBytes = Base64.getUrlDecoder().decode(payload)
        val decodedString = String(decodedBytes)
        
        return JSONObject(decodedString).toMap()
    }

    private fun JSONObject.toMap(): Map<String, Any> =
        keys().asSequence().associateWith { key ->
            when (val value = this[key]) {
                is JSONObject -> value.toMap()
                JSONObject.NULL -> null
                else -> value
            }
        }

    private suspend fun sendLoginRequest(userId: String, name: String, email: String) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("u_id", userId)
            put("name", name)
            put("email", email)
        }
        
        val request = Request.Builder()
            .url("${TaskViewModel.BASE_URL}/login")
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Login failed: ${response.code}")
        }
    }
}
