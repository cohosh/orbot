package org.torproject.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.torproject.android.circumvention.CircumventionApiManager
import org.torproject.android.circumvention.SettingsResponse
import org.torproject.android.service.util.Prefs
import java.io.IOException

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Generated new FCM token: $token")
        sendRegistrationToServer(Prefs.getCountry(), token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            val settingsResponse = Gson().fromJson(
                remoteMessage.data.getOrDefault("payload", "{}"), SettingsResponse::class.java)

            // (if available) use channel to notify the UI thread for connection, or display notification for user
            // TODO: can I use runBlocking instead of lifecycleScope.launch(Dispatchers.Main) here?
            runBlocking {
                launch {
                    val channel = waitingChannel
                    if (channel != null) {
                        Log.d(TAG, "channel send")
                        channel.send(settingsResponse)
                    } else {
                        Log.d(TAG, "display notification")
                        showNotification(applicationContext, NOTIFICATION_CHANNEL_ID, getString(R.string.bridges_updated), getString(R.string.restart_orbot_to_use_this_bridge_))
                    }
                }
            }
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }
    }

    companion object {
        private const val TAG = "push-notification"
        private const val NOTIFICATION_CHANNEL_ID = "orbot_channel_2"

        var waitingChannel: Channel<SettingsResponse>? = null

        fun sendRegistrationToServer(
            country: String,
            token: String,
            callbackIfSuccess: (() -> Unit)? = null,
            callbackIfFail: (() -> Unit)? = null
        ) {
            // this is the computer's address in Android Virtual Machine
            val url = "http://138.197.154.104:8888/fcm/register"
            val client = OkHttpClient()

            val jsonString = """{ "token": "$token", "country": "$country" }"""

            val requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonString)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to send token to server: ${e.message}")
                    // TODO: handle the error, e.g. display user notification?
                    callbackIfFail?.invoke()
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "Token sent to server successfully")
                    response.body()?.charStream()?.readText().let {
                        if (it != null) {
                            Log.d(TAG, it)
                        } else {
                            Log.d(TAG, response.message())
                        }
                    }
                    callbackIfSuccess?.invoke()
                }
            })
        }

        fun showNotification(context: Context, channelId: String, title: String, content: String) {
            // Create a notification channel (required for Android Oreo and above)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Channel Name", NotificationManager.IMPORTANCE_DEFAULT)
                val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }

            // Create the notification
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_onion)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            // Show the notification
            with(NotificationManagerCompat.from(context)) {
                notify(/*notificationId=*/0, builder.build())
            }
        }
    }
}
