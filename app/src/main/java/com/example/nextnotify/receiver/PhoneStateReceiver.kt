package com.example.nextnotify.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.telegram.TelegramSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneStateReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val settingsStore = AppSettingsStore(context)
        if (!settingsStore.hasTelegramConfig()) {
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumberFromBroadcast = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (incomingNumberFromBroadcast != null) {
            lastIncomingNumber = incomingNumberFromBroadcast
        }

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastState = state

                if (
                    settingsStore.isIncomingCallForwardingEnabled() &&
                    !hasSentIncomingCallNotification &&
                    (lastIncomingNumber != null || !hasCallLogPermission(context))
                ) {
                    hasSentIncomingCallNotification = true
                    TelegramSender(context).sendConfiguredMessage(
                        buildString {
                            appendLine("NextNotify")
                            appendLine("Loại: Cuộc gọi đến")
                            appendLine("Số điện thoại: ${lastIncomingNumber ?: "Không xác định"}")
                            append("Thời gian: ${formatNow()}")
                        }
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (
                    settingsStore.isIncomingCallForwardingEnabled() &&
                    !hasSentIncomingCallNotification &&
                    lastIncomingNumber != null
                ) {
                    hasSentIncomingCallNotification = true
                    TelegramSender(context).sendConfiguredMessage(
                        buildString {
                            appendLine("NextNotify")
                            appendLine("Loại: Cuộc gọi đến")
                            appendLine("Số điện thoại: $lastIncomingNumber")
                            append("Thời gian: ${formatNow()}")
                        }
                    )
                }
                lastState = state
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastIncomingNumber == null) {
                    lastIncomingNumber = getRecentCallNumber(
                        context = context,
                        callTypes = listOf(
                            CallLog.Calls.MISSED_TYPE,
                            CallLog.Calls.REJECTED_TYPE,
                            CallLog.Calls.INCOMING_TYPE
                        )
                    )
                }

                if (
                    lastState == TelephonyManager.EXTRA_STATE_RINGING &&
                    settingsStore.isMissedCallForwardingEnabled()
                ) {
                    TelegramSender(context).sendConfiguredMessage(
                        buildString {
                            appendLine("NextNotify")
                            appendLine("Loại: Cuộc gọi nhỡ")
                            appendLine("Số điện thoại: ${lastIncomingNumber ?: "Không xác định"}")
                            append("Thời gian: ${formatNow()}")
                        }
                    )
                }

                lastState = state
                lastIncomingNumber = null
                hasSentIncomingCallNotification = false
            }
        }
    }

    private fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getRecentCallNumber(context: Context, callTypes: List<Int>): String? {
        if (!hasCallLogPermission(context)) {
            return null
        }

        val cutoffTimestamp = System.currentTimeMillis() - RECENT_CALL_WINDOW_MS
        val selection = buildString {
            append("${CallLog.Calls.TYPE} IN (${callTypes.joinToString(",")})")
            append(" AND ${CallLog.Calls.DATE} >= ?")
        }
        val selectionArgs = arrayOf(cutoffTimestamp.toString())

        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return it.getString(0)?.trim()?.takeIf { value -> value.isNotBlank() }
        }
    }

    private fun formatNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    companion object {
        private const val RECENT_CALL_WINDOW_MS = 2 * 60 * 1000L
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private var lastIncomingNumber: String? = null
        private var hasSentIncomingCallNotification: Boolean = false
    }
}
