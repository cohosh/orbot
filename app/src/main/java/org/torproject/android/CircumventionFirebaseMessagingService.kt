package org.torproject.android

import android.content.Intent
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.PublicKeyVerify
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.PredefinedHybridParameters
import com.google.crypto.tink.signature.SignatureConfig
import com.google.firebase.messaging.FirebaseMessaging
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
import org.torproject.android.circumvention.PushSettingsResponse
import org.torproject.android.service.OrbotConstants
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import java.io.IOException
import java.util.Base64


class CircumventionFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Generated new FCM token: $token")
        sendRegistrationToServer()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val msg = decryptMessage(remoteMessage.data.getOrDefault("payload", "{}"))
            if (msg.size == 0) {
                return
            }
            val response = Gson().fromJson(String(msg, Charsets.UTF_8), PushSettingsResponse::class.java)
            if (!verifySignature(response.signature, response.settings)) {
                Log.d(TAG, "Failed to verify signature from distributor")
                return
            }
            Log.d(TAG, "Successfully decrypted and verified settings: ${response.settings}")
            val intent = Intent(OrbotConstants.PUSH_NOTIFICATION)
                .putExtra("SETTINGS", response.settings)
            applicationContext.sendBroadcast(intent)
        }
    }

    companion object {
        private const val TAG = "push-notification"

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
                rotateKey()
                Log.d(TAG, "Loaded keyset: "+ Prefs.getPrefPushKey())
                val handle = TinkJsonProtoKeysetFormat.parseKeyset(Prefs.getPrefPushKey(), InsecureSecretKeyAccess.get())
                val pubkey = TinkJsonProtoKeysetFormat.serializeKeyset(handle.publicKeysetHandle, InsecureSecretKeyAccess.get())
                val country = Prefs.getCountry()
                val url = OrbotService.getCdnFront("push-distributor-url") + "/fcm/register"
                val jsonString = """{ "token": "${task.result}", "country": "cn", "key": ${pubkey} }"""
                Log.d(TAG, "Sent string: "+jsonString)
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

        private fun rotateKey() {
            HybridConfig.register()
            val handle: KeysetHandle = KeysetHandle.generateNew(PredefinedHybridParameters.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
            val serializedKeyset =
                TinkJsonProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get())
            Prefs.setPrefPushKey(serializedKeyset)
            Log.d(TAG, "Generated new keyset: "+ serializedKeyset)
        }

        private fun decryptMessage(payload: String): ByteArray {
            val encryptedSettings = Base64.getDecoder().decode(payload)
            val handle = TinkJsonProtoKeysetFormat.parseKeyset(Prefs.getPrefPushKey(), InsecureSecretKeyAccess.get())
            val decryptor: HybridDecrypt = handle.getPrimitive(HybridDecrypt::class.java)
            var msg = ByteArray(0)
            try {
                msg = decryptor.decrypt(encryptedSettings, "circumvention settings".toByteArray())
            } catch(e: java.security.GeneralSecurityException) {
                Log.d(TAG, "Failed to decrypt settings from message")
                return ByteArray(0)
            }
            return msg
        }

        private fun verifySignature(sig: String, msg: String): Boolean {
            val signature = Base64.getDecoder().decode(sig)
            SignatureConfig.register();
            val sigHandle =
                TinkJsonProtoKeysetFormat.parseKeyset(
                    OrbotService.getPubKey("push-notification-distributor"), InsecureSecretKeyAccess.get()
                )
            try {
                val verifier: PublicKeyVerify = sigHandle.getPrimitive(PublicKeyVerify::class.java)
                verifier.verify(signature, msg.toByteArray())
            } catch(e: java.security.GeneralSecurityException) {
                return false
            }
            return true
        }
    }

}
