package org.torproject.android

import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import java.io.IOException

class CircumventionFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Generated new FCM token: $token")
        sendRegistrationToServer()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            showNotification(applicationContext, NOTIFICATION_CHANNEL_ID,
                remoteMessage.data.getOrDefault("payload", "{}")
            )
        }
    }

    companion object {
        private const val TAG = "push-notification"
        private const val NOTIFICATION_CHANNEL_ID = "orbot_channel_1"
        private const val NOTIFICATION_ID = 11

        fun sendRegistrationToServer(
            callbackIfSuccess: (() -> Unit)? = null,
            callbackIfFail: (() -> Unit)? = null
        ) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    callbackIfFail?.invoke()
                    return@OnCompleteListener
                }
                val country = Prefs.getCountry()
                val url = OrbotService.getCdnFront("push-distributor-url") + "/fcm/register"
                val jsonString = """{ "token": "${task.result}", "country": "$country" }"""
                val requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    jsonString
                )
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                OkHttpClient().newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Failed to send token to server: ${e.message}")
                        callbackIfFail?.invoke()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "Token sent to server successfully")
                        callbackIfSuccess?.invoke()
                    }
                })
            })
        }
    }
}
