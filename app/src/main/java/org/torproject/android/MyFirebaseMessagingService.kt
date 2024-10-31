package org.torproject.android

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.torproject.android.circumvention.SettingsResponse
import org.torproject.android.service.util.Prefs
import java.io.IOException

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Generated new FCM token: $token")
        sendRegistrationToServer(Prefs.getCountry(), token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            val settingsResponse = Gson().fromJson(
                remoteMessage.data.getOrDefault("payload", "{}"), SettingsResponse::class.java)
            onMessageCallback?.invoke(settingsResponse)
        }
    }

    companion object {
        private const val TAG = "push-notification"
        var onMessageCallback: ((SettingsResponse)->Unit)? = null

        fun sendRegistrationToServer(
            country: String,
            token: String,
            callbackIfSuccess: (() -> Unit)? = null,
            callbackIfFail: (() -> Unit)? = null
        ) {
            val url = "http://138.197.154.104:8888/fcm/register"
            val jsonString = """{ "token": "$token", "country": "$country" }"""
            val requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonString)
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
        }
    }
}
