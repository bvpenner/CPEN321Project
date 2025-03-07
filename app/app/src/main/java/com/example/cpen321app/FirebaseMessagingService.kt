package com.example.cpen321app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class TaskNotificationService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "TaskNotificationService"
        private const val CHANNEL_ID_TASKS = "task_notifications"
        private const val CHANNEL_ID_ROUTES = "route_notifications"
        private const val CHANNEL_ID_REMINDERS = "reminder_notifications"
        
        private const val NOTIFICATION_ID_TASK = 1001
        private const val NOTIFICATION_ID_ROUTE = 1002
        private const val NOTIFICATION_ID_REMINDER = 1003
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Send token to server
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("u_id", SessionManager.u_id)
                    put("fcm_token", token)
                }
                
                val request = Request.Builder()
                    .url("${TaskViewModel.BASE_URL}/updateFcmToken")
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Failed to update FCM token: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating FCM token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")

        message.data.let { data ->
            when (data["type"]) {
                "task" -> handleTaskNotification(data)
                "route" -> handleRouteNotification(data)
                "reminder" -> handleReminderNotification(data)
                else -> handleDefaultNotification(message)
            }
        }
    }

    private fun handleTaskNotification(data: Map<String, String>) {
        val title = data["title"] ?: "New Task"
        val message = data["message"] ?: "You have a new task"
        val taskId = data["taskId"]
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("taskId", taskId)
            putExtra("action", "view_task")
        }
        
        showNotification(
            channelId = CHANNEL_ID_TASKS,
            title = title,
            message = message,
            intent = intent,
            notificationId = NOTIFICATION_ID_TASK,
            color = ContextCompat.getColor(this, R.color.task_notification),
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun handleRouteNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Route Available"
        val message = data["message"] ?: "A new optimized route is available"
        val routeUrl = data["routeUrl"]
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(routeUrl)
            setPackage("com.google.android.apps.maps")
        }
        
        showNotification(
            channelId = CHANNEL_ID_ROUTES,
            title = title,
            message = message,
            intent = intent,
            notificationId = NOTIFICATION_ID_ROUTE,
            color = ContextCompat.getColor(this, R.color.route_notification),
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    private fun handleReminderNotification(data: Map<String, String>) {
        val title = data["title"] ?: "Task Reminder"
        val message = data["message"] ?: "Don't forget about your upcoming task"
        val taskId = data["taskId"]
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("taskId", taskId)
            putExtra("action", "view_task")
        }
        
        showNotification(
            channelId = CHANNEL_ID_REMINDERS,
            title = title,
            message = message,
            intent = intent,
            notificationId = NOTIFICATION_ID_REMINDER,
            color = ContextCompat.getColor(this, R.color.reminder_notification),
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun handleDefaultNotification(message: RemoteMessage) {
        message.notification?.let { notification ->
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            showNotification(
                channelId = CHANNEL_ID_TASKS,
                title = notification.title ?: "New Notification",
                message = notification.body ?: "",
                intent = intent,
                notificationId = NOTIFICATION_ID_TASK,
                color = ContextCompat.getColor(this, R.color.task_notification),
                priority = NotificationCompat.PRIORITY_DEFAULT
            )
        }
    }

    private fun showNotification(
        channelId: String,
        title: String,
        message: String,
        intent: Intent,
        notificationId: Int,
        color: Int,
        priority: Int
    ) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setColor(color)
            .setPriority(priority)
            .setCategory(when (channelId) {
                CHANNEL_ID_TASKS -> NotificationCompat.CATEGORY_EVENT
                CHANNEL_ID_ROUTES -> NotificationCompat.CATEGORY_NAVIGATION
                CHANNEL_ID_REMINDERS -> NotificationCompat.CATEGORY_REMINDER
                else -> NotificationCompat.CATEGORY_MESSAGE
            })

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channels for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ID_TASKS,
                    "Task Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new and updated tasks"
                    enableLights(true)
                    lightColor = color
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ID_ROUTES,
                    "Route Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for optimized routes"
                    enableLights(true)
                    lightColor = color
                },
                NotificationChannel(
                    CHANNEL_ID_REMINDERS,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders for upcoming tasks"
                    enableLights(true)
                    lightColor = color
                    enableVibration(true)
                    setSound(
                        defaultSoundUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
