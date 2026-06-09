package com.example.nextnotify.ui.slideshow

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.databinding.FragmentSlideshowBinding

class SlideshowFragment : Fragment() {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsStore: AppSettingsStore

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (_binding != null) {
            updatePermissionStatus()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlideshowBinding.inflate(inflater, container, false)
        settingsStore = AppSettingsStore(requireContext())

        bindState()
        bindActions()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateBackgroundAccessStatus()
    }

    private fun bindState() {
        binding.switchSms.isChecked = settingsStore.isSmsForwardingEnabled()
        binding.switchIncomingCall.isChecked = settingsStore.isIncomingCallForwardingEnabled()
        binding.switchMissedCall.isChecked = settingsStore.isMissedCallForwardingEnabled()
        binding.switchEndedCall.isChecked = settingsStore.isEndedCallForwardingEnabled()
        updatePermissionStatus()
        updateBackgroundAccessStatus()
    }

    private fun bindActions() {
        binding.buttonRequestPermissions.setOnClickListener {
            val missingPermissions = requiredPermissions().filterNot(::hasPermission)

            if (missingPermissions.isNotEmpty()) {
                permissionsLauncher.launch(missingPermissions.toTypedArray())
            } else {
                updatePermissionStatus()
            }
        }

        binding.switchSms.setOnCheckedChangeListener { _, enabled ->
            settingsStore.setSmsForwardingEnabled(enabled)
        }

        binding.switchIncomingCall.setOnCheckedChangeListener { _, enabled ->
            settingsStore.setIncomingCallForwardingEnabled(enabled)
        }

        binding.switchMissedCall.setOnCheckedChangeListener { _, enabled ->
            settingsStore.setMissedCallForwardingEnabled(enabled)
        }

        binding.switchEndedCall.setOnCheckedChangeListener { _, enabled ->
            settingsStore.setEndedCallForwardingEnabled(enabled)
        }

        binding.buttonRequestBackgroundAccess.setOnClickListener {
            openBackgroundAccessSettings()
        }
    }

    private fun updatePermissionStatus() {
        val smsGranted = hasPermission(Manifest.permission.RECEIVE_SMS)
        val phoneGranted = hasPermission(Manifest.permission.READ_PHONE_STATE)
        val phoneNumbersGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hasPermission(Manifest.permission.READ_PHONE_NUMBERS)
        } else {
            true
        }
        val callLogGranted = hasPermission(Manifest.permission.READ_CALL_LOG)

        binding.textPermissionStatus.text = buildString {
            append("RECEIVE_SMS: ")
            append(if (smsGranted) "đã cấp" else "chưa cấp")
            append('\n')
            append("READ_PHONE_STATE: ")
            append(if (phoneGranted) "đã cấp" else "chưa cấp")
            append('\n')
            append("READ_PHONE_NUMBERS: ")
            append(if (phoneNumbersGranted) "đã cấp" else "chưa cấp")
            append('\n')
            append("READ_CALL_LOG: ")
            append(if (callLogGranted) "đã cấp" else "chưa cấp")
        }
    }

    private fun requiredPermissions(): List<String> {
        return buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.READ_PHONE_NUMBERS)
            }
            add(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun updateBackgroundAccessStatus() {
        val powerManager = requireContext().getSystemService(PowerManager::class.java)
        val ignoringBatteryOptimization = powerManager?.isIgnoringBatteryOptimizations(
            requireContext().packageName
        ) == true

        binding.textBackgroundStatus.text = if (ignoringBatteryOptimization) {
            "Battery optimization: đã tắt cho NextNotify. App sẽ ổn định hơn khi chạy nền."
        } else {
            "Battery optimization: vẫn đang bật. Nên tắt để app hạn chế bị hệ thống dừng khi chạy nền."
        }
    }

    private fun openBackgroundAccessSettings() {
        val packageUri = Uri.parse("package:${requireContext().packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri
        }

        try {
            startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: SecurityException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}