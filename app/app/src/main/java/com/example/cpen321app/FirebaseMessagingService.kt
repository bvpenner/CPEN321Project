import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.cpen321app.MainActivity

//        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
//            if (task.isSuccessful) {
//                firebase_token = task.result
//                Log.d(TAG, "FCM Token: $firebase_token")
//            }
//        }

class FirebaseMessagingService {

    fun sendNotification(context: Context, messageBody: String) {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "default_channel"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FCM Message")
            .setContentText(messageBody.take(50)) // Shorten preview text if needed
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody)) // Expand for longer text
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)


        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "Local Notifications", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        Log.d("Notification", "Sending notification: $messageBody")
        notificationManager.notify(0, notificationBuilder.build())
    }

    fun sendPersistentNotification(context: Context, title: String, messageBody: String) {
        val channelId = "persistent_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Persistent Notifications",
            NotificationManager.IMPORTANCE_LOW // Won't pop, but stays in tray
        )
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(messageBody.take(50))
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
            .setOngoing(true) // <- Makes it persistent
            .setAutoCancel(false) // <- Don't remove on click
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(1, notificationBuilder.build())
    }

}
