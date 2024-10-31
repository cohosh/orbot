package org.torproject.android

import IPtProxy.IPtProxy
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.torproject.android.circumvention.Bridges
import org.torproject.android.circumvention.CircumventionApiManager
import org.torproject.android.circumvention.SettingsRequest
import org.torproject.android.service.OrbotService
import org.torproject.android.service.util.Prefs
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.util.*

class ConfigConnectionBottomSheet() :
    OrbotBottomSheetDialogFragment() {

    private var callbacks: ConnectionHelperCallbacks? = null

    private lateinit var rbDirect: RadioButton
    private lateinit var rbSnowflake: RadioButton

    //  private lateinit var rbSnowflakeAmp: RadioButton
    private lateinit var rbRequestBridge: RadioButton
    private lateinit var rbCustom: RadioButton

    private lateinit var btnAction: Button
    private lateinit var btnAskTor: Button

    companion object {
        private const val TAG = "connection config bottom sheet"
        public fun newInstance(callbacks: ConnectionHelperCallbacks): ConfigConnectionBottomSheet {
            return ConfigConnectionBottomSheet().apply {
                this.callbacks = callbacks
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            this.registerPushNotifications()
        } else {
            // TODO: Inform user that that your app will not show notifications, maybe with same callback used elsewhere
            Log.d(TAG, "permission for push notifications was not granted")
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.config_connection_bottom_sheet, container, false)

        rbDirect = v.findViewById(R.id.rbDirect)
        rbSnowflake = v.findViewById(R.id.rbSnowflake)
        //    rbSnowflakeAmp = v.findViewById(R.id.rbSnowflakeAmp)
        rbRequestBridge = v.findViewById(R.id.rbRequest)
        rbCustom = v.findViewById(R.id.rbCustom)

        val tvDirectSubtitle = v.findViewById<View>(R.id.tvDirectSubtitle)
        val tvSnowflakeSubtitle = v.findViewById<View>(R.id.tvSnowflakeSubtitle)
        //   val tvSnowflakeAmpSubtitle = v.findViewById<View>(R.id.tvSnowflakeAmpSubtitle)
        val tvRequestSubtitle = v.findViewById<View>(R.id.tvRequestSubtitle)
        val tvCustomSubtitle = v.findViewById<View>(R.id.tvCustomSubtitle)

        val radios = arrayListOf(rbDirect, rbSnowflake, rbRequestBridge, rbCustom)
        val radioSubtitleMap = mapOf<CompoundButton, View>(
            rbDirect to tvDirectSubtitle,
            rbSnowflake to tvSnowflakeSubtitle,
            rbRequestBridge to tvRequestSubtitle,
            rbCustom to tvCustomSubtitle
        )
        val allSubtitles = arrayListOf(
            tvDirectSubtitle, tvSnowflakeSubtitle, tvRequestSubtitle, tvCustomSubtitle
        )
        btnAction = v.findViewById(R.id.btnAction)
        btnAskTor = v.findViewById(R.id.btnAskTor)

        btnAskTor.setOnClickListener {
            askTor()
        }

        // setup containers so radio buttons can be checked if labels are clicked on
        //   v.findViewById<View>(R.id.smartContainer).setOnClickListener {rbSmart.isChecked = true}
        v.findViewById<View>(R.id.directContainer).setOnClickListener { rbDirect.isChecked = true }
        v.findViewById<View>(R.id.snowflakeContainer)
            .setOnClickListener { rbSnowflake.isChecked = true }
        //  v.findViewById<View>(R.id.snowflakeAmpContainer).setOnClickListener {rbSnowflakeAmp.isChecked = true}
        v.findViewById<View>(R.id.requestContainer)
            .setOnClickListener { rbRequestBridge.isChecked = true }
        v.findViewById<View>(R.id.customContainer).setOnClickListener { rbCustom.isChecked = true }
        v.findViewById<View>(R.id.tvCancel).setOnClickListener { dismiss() }

        rbDirect.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
            }
        }
        rbSnowflake.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
            }
        }
        /**
        rbSnowflakeAmp.setOnCheckedChangeListener { buttonView, isChecked ->
        if (isChecked) {
        nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
        radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
        }
        }**/
        rbRequestBridge.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
                btnAction.text = getString(R.string.next)
            } else {
                btnAction.text = getString(R.string.connect)
            }
        }
        rbCustom.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                nestedRadioButtonKludgeFunction(buttonView as RadioButton, radios)
                radioSubtitleMap[buttonView]?.let { onlyShowActiveSubtitle(it, allSubtitles) }
                btnAction.text = getString(R.string.next)
            } else {
                btnAction.text = getString(R.string.connect)
            }
        }

        selectRadioButtonFromPreference()

        btnAction.setOnClickListener {
            if (rbRequestBridge.isChecked) {
                MoatBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                        rbCustom.isChecked = true
                        dismiss()
                        callbacks?.tryConnecting()
                    }
                }).show(requireActivity().supportFragmentManager, MoatBottomSheet.TAG)
            } else if (rbDirect.isChecked) {
                Prefs.putConnectionPathway(Prefs.PATHWAY_DIRECT)
                closeAndConnect()
            } else if (rbSnowflake.isChecked) {
                Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE)
                closeAndConnect()
            }
            /**else if (rbSnowflakeAmp.isChecked) {
            Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE_AMP)
            closeAndConnect()
            } **/
            else if (rbCustom.isChecked) {
                CustomBridgeBottomSheet(object : ConnectionHelperCallbacks {
                    override fun tryConnecting() {
                        Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                        callbacks?.tryConnecting()
                    }
                }).show(requireActivity().supportFragmentManager, CustomBridgeBottomSheet.TAG)
            }
        }

        return v
    }

    private fun closeAndConnect() {
        closeAllSheets()
        callbacks?.tryConnecting()
    }

    // it's 2022 and android makes you do ungodly things for mere radio button functionality
    private fun nestedRadioButtonKludgeFunction(rb: RadioButton, all: List<RadioButton>) =
        all.forEach { if (it != rb) it.isChecked = false }

    private fun onlyShowActiveSubtitle(showMe: View, all: List<View>) = all.forEach {
        if (it == showMe) it.visibility = View.VISIBLE
        else it.visibility = View.GONE
    }

    private fun selectRadioButtonFromPreference() {
        val pref = Prefs.getConnectionPathway()
        if (pref.equals(Prefs.PATHWAY_CUSTOM)) rbCustom.isChecked = true
        if (pref.equals(Prefs.PATHWAY_SNOWFLAKE)) rbSnowflake.isChecked = true
        // if (pref.equals(Prefs.PATHWAY_SNOWFLAKE_AMP)) rbSnowflakeAmp.isChecked = true
        if (pref.equals(Prefs.PATHWAY_DIRECT)) rbDirect.isChecked = true
    }

    private var circumventionApiBridges: List<Bridges?>? = null
    private var circumventionApiIndex = 0

    private fun askTor() {

        val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_faq)
        btnAskTor.text = getString(R.string.asking)
        btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)

        val fileCacheDir = File(requireActivity().cacheDir, "pt")
        if (!fileCacheDir.exists()) {
            fileCacheDir.mkdir()
        }

        IPtProxy.setStateLocation(fileCacheDir.absolutePath)
        IPtProxy.startLyrebird("DEBUG", false, false, null)
        val pUsername =
            "url=" + OrbotService.getCdnFront("moat-url") + ";front=" + OrbotService.getCdnFront("moat-front")
        val pPassword = "\u0000"

        //    Log.d(getClass().getSimpleName(), String.format("mHost=%s, mPort=%d, mUsername=%s, mPassword=%s", mHost, mPort, mUsername, mPassword))
        val authenticator: Authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                Log.d(javaClass.simpleName, "getPasswordAuthentication!")
                return PasswordAuthentication(pUsername, pPassword.toCharArray())
            }
        }

        Authenticator.setDefault(authenticator)

        val countryCodeValue: String = getDeviceCountryCode(requireContext())
        Log.d("bim", "The country code is $countryCodeValue")
        Prefs.setCountry(countryCodeValue)

        CircumventionApiManager().getSettings(SettingsRequest(countryCodeValue), {
            it?.let {
                circumventionApiBridges = it.settings
                if (circumventionApiBridges == null) {
                    Log.d("abc", "settings is null, we can assume a direct connect is fine ")
                    rbDirect.isChecked = true

                } else {

                    Log.d("abc", "settings is $circumventionApiBridges")
                    circumventionApiBridges?.forEach { b ->
                        Log.d("abc", "BRIDGE $b")
                    }

                    //got bridges, let's set them
                    setPreferenceForSmartConnect()
                }

                IPtProxy.stopLyrebird()
            }
        }, {
            // TODO what happens to the app in this case?!
            Log.e("ConfigConnectionBottomSheet", "Couldn't hit circumvention API... $it")
            Toast.makeText(requireContext(), "Ask Tor was not available", Toast.LENGTH_LONG).show()
        })

        // Set up push notifications for further updates
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.

                // For now, directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {

                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun registerPushNotifications() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                // TODO: display a toast for error?
                return@OnCompleteListener
            }
            val token = task.result
            // Log and toast
            Log.d(TAG, token)
            // Send the token to web server
            // TODO: check if has already been initialized?
            MyFirebaseMessagingService.sendRegistrationToServer(Prefs.getCountry(), token, {
                Log.d(
                    TAG,
                    "Registered with server successfully. Awaiting bridges to be posted via push notification"
                )

                // use channel to wait for push messages. before then, user cannot proceed
                MyFirebaseMessagingService.waitingChannel = Channel()
                Log.d(
                    TAG,
                    "channel set. waiting " + MyFirebaseMessagingService.waitingChannel
                )
                // TODO: why does runBlocking here result in Application Not Responding?
                // How does switching to this fix the issue?
                lifecycleScope.launch(Dispatchers.Main) {
                    launch {
                        val channel = MyFirebaseMessagingService.waitingChannel

                        if (channel == null) {
                            // TODO: error. race condition? display error toast and go back?
                            Log.w(TAG, "channel is null. race condition?")
                            return@launch
                        }

                        Log.d(TAG, "channel wait to receive")
                        val settings = channel.receive()
                        Log.d(TAG, "channel receive successful")
                        circumventionApiBridges = settings.settings
                        if (circumventionApiBridges == null) {
                            rbDirect.isChecked = true
                        } else {
                            setPreferenceForSmartConnect()
                        }
                        channel.close()
                        MyFirebaseMessagingService.waitingChannel = null
                    }
                }
            }, {
                //TODO: show a popup or have a onError callback, similar to other askTor function
                Log.d(TAG, "Error registering with push notification server")
            })
        })
    }

    private fun getDeviceCountryCode(context: Context): String {
        var countryCode: String?

        // Try to get country code from TelephonyManager service
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Query first getSimCountryIso()
        countryCode = tm.simCountryIso
        if (countryCode != null && countryCode.length == 2) return countryCode.lowercase(Locale.getDefault())

        countryCode = tm.networkCountryIso
        if (countryCode != null && countryCode.length == 2) return countryCode.lowercase(Locale.getDefault())

        countryCode = context.resources.configuration.locales[0].country

        return if (countryCode != null && countryCode.length == 2) countryCode.lowercase(Locale.getDefault()) else "us"

    }

    private fun setPreferenceForSmartConnect() {

        val dLeft = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_green_check)
        btnAskTor.setCompoundDrawablesWithIntrinsicBounds(dLeft, null, null, null)

        circumventionApiBridges?.let {
            if (it.size == circumventionApiIndex) {
                circumventionApiBridges = null
                circumventionApiIndex = 0
                rbDirect.isChecked = true
                btnAskTor.text = getString(R.string.connection_direct)

                Log.d("bim", "smart connect: Direct is chosen")

                return
            }
            val b = it[circumventionApiIndex]!!.bridges
            when (b.type) {
                CircumventionApiManager.BRIDGE_TYPE_SNOWFLAKE -> {
                    Prefs.putConnectionPathway(Prefs.PATHWAY_SNOWFLAKE)
                    rbSnowflake.isChecked = true
                    btnAskTor.text = getString(R.string.connection_snowflake)
                }

                CircumventionApiManager.BRIDGE_TYPE_OBFS4 -> {
                    rbCustom.isChecked = true
                    btnAskTor.text = getString(R.string.connection_custom)

                    var bridgeStrings = ""
                    b.bridge_strings!!.forEach { bridgeString ->
                        bridgeStrings += "$bridgeString\n"
                    }
                    Prefs.setBridgesList(bridgeStrings)
                    Prefs.putConnectionPathway(Prefs.PATHWAY_CUSTOM)
                }

                else -> {
                    rbDirect.isChecked = true
                }
            }
            circumventionApiIndex += 1
        }
    }
}
