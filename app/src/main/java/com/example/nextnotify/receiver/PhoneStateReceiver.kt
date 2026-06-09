package com.example.nextnotify.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import android.telephony.SubscriptionManager
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
        if (
            intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED &&
            intent.action != ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED
        ) {
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
        val simInfo = getSimInfoFromIntent(context, intent)

        if (incomingNumberFromBroadcast != null) {
            lastIncomingNumber = incomingNumberFromBroadcast
        }
        if (simInfo != null) {
            lastIncomingSimInfo = simInfo
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
                            lastIncomingSimInfo?.let { appendLine("SIM: $it") }
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
                            lastIncomingSimInfo?.let { appendLine("SIM: $it") }
                            append("Thời gian: ${formatNow()}")
                        }
                    )
                }
                lastState = state
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val recentCompletedCall = if (
                    lastState == TelephonyManager.EXTRA_STATE_OFFHOOK &&
                    settingsStore.isEndedCallForwardingEnabled()
                ) {
                    getRecentCall(
                        context = context,
                        callTypes = listOf(
                            CallLog.Calls.INCOMING_TYPE,
                            CallLog.Calls.OUTGOING_TYPE
                        )
                    )
                } else {
                    null
                }

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
                            lastIncomingSimInfo?.let { appendLine("SIM: $it") }
                            append("Thời gian: ${formatNow()}")
                        }
                    )
                }

                if (recentCompletedCall != null) {
                    val completedCallKey = buildCompletedCallKey(recentCompletedCall)
                    if (lastCompletedCallNotificationKey != completedCallKey) {
                        lastCompletedCallNotificationKey = completedCallKey
                        TelegramSender(context).sendConfiguredMessage(
                            buildString {
                                appendLine("NextNotify")
                                appendLine("Loại: Cuộc gọi kết thúc")
                                appendLine("Hướng: ${getCallDirectionLabel(recentCompletedCall.type)}")
                                appendLine(
                                    "Số điện thoại: ${
                                        recentCompletedCall.number
                                            ?: lastIncomingNumber
                                            ?: "Không xác định"
                                    }"
                                )
                                if (recentCompletedCall.type == CallLog.Calls.INCOMING_TYPE) {
                                    lastIncomingSimInfo?.let { appendLine("SIM: $it") }
                                }
                                appendLine("Thời lượng: ${formatDuration(recentCompletedCall.durationSeconds)}")
                                append("Kết thúc lúc: ${formatNow()}")
                            }
                        )
                    }
                }

                lastState = state
                lastIncomingNumber = null
                lastIncomingSimInfo = null
                hasSentIncomingCallNotification = false
            }
        }
    }

    private fun getSimInfoFromIntent(context: Context, intent: Intent): String? {
        val subscriptionId = getSubscriptionIdFromIntent(intent)
        val slotIndex = getSlotIndexFromIntent(intent)

        if (
            subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
            slotIndex == SubscriptionManager.INVALID_SIM_SLOT_INDEX
        ) {
            return null
        }

        val fallbackSlotIndex = when {
            slotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX -> slotIndex
            subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID ->
                SubscriptionManager.getSlotIndex(subscriptionId)
            else -> SubscriptionManager.INVALID_SIM_SLOT_INDEX
        }
        val defaultSimLabel = fallbackSlotIndex
            .takeIf { it >= 0 }
            ?.let { "SIM ${it + 1}" }
            ?: "SIM không xác định"

        if (!hasPhoneStatePermission(context)) {
            return defaultSimLabel
        }

        return runCatching {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val subscriptionInfo = when {
                subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID ->
                    subscriptionManager?.getActiveSubscriptionInfo(subscriptionId)
                fallbackSlotIndex != SubscriptionManager.INVALID_SIM_SLOT_INDEX ->
                    subscriptionManager?.getActiveSubscriptionInfoForSimSlotIndex(fallbackSlotIndex)
                else -> null
            }
            val resolvedSlotIndex = subscriptionInfo?.simSlotIndex
                ?.takeIf { it >= 0 }
                ?: fallbackSlotIndex
            val slotLabel = resolvedSlotIndex
                .takeIf { it >= 0 }
                ?.let { "SIM ${it + 1}" }
                .orEmpty()
            val displayName = subscriptionInfo?.displayName?.toString()?.trim().orEmpty()
            val carrierName = subscriptionInfo?.carrierName?.toString()?.trim().orEmpty()
            val phoneNumber = getSimPhoneNumber(
                context = context,
                subscriptionManager = subscriptionManager,
                subscriptionId = subscriptionId,
                subscriptionInfoNumber = subscriptionInfo?.number
            )

            listOf(
                displayName,
                carrierName,
                slotLabel,
                phoneNumber?.let { "Số SIM: $it" }.orEmpty()
            )
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" - ")
                .ifBlank { defaultSimLabel }
        }.getOrDefault(defaultSimLabel)
    }

    private fun getSubscriptionIdFromIntent(intent: Intent): Int {
        val extras = intent.extras ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val candidateKeys = listOf(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            "subscription",
            "subscription_id",
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subId",
            "sub_id"
        )

        for (key in candidateKeys) {
            val value = readIntExtra(extras, key)
            if (value != null && value != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return value
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun getSlotIndexFromIntent(intent: Intent): Int {
        val extras = intent.extras ?: return SubscriptionManager.INVALID_SIM_SLOT_INDEX
        val candidateKeys = listOf(
            SubscriptionManager.EXTRA_SLOT_INDEX,
            "slot",
            "slotId",
            "slot_id",
            "slotIdx",
            "simId",
            "simSlot",
            "simnum",
            "phone",
            "phone_id"
        )

        for (key in candidateKeys) {
            val value = readIntExtra(extras, key)
            if (value != null && value >= 0) {
                return value
            }
        }

        for (key in extras.keySet()) {
            if (!key.contains("slot", ignoreCase = true) && !key.contains("sim", ignoreCase = true)) {
                continue
            }
            val value = readIntExtra(extras, key)
            if (value != null && value >= 0) {
                return value
            }
        }

        return SubscriptionManager.INVALID_SIM_SLOT_INDEX
    }

    private fun readIntExtra(extras: android.os.Bundle, key: String): Int? {
        if (!extras.containsKey(key)) {
            return null
        }

        return when (val value = extras.get(key)) {
            is Int -> value
            is Long -> value.toInt()
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun hasPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasPhoneNumbersPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getSimPhoneNumber(
        context: Context,
        subscriptionManager: SubscriptionManager?,
        subscriptionId: Int,
        subscriptionInfoNumber: String?
    ): String? {
        if (
            subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ||
            !hasPhoneNumbersPermission(context)
        ) {
            return null
        }

        val normalizedSubscriptionInfoNumber = subscriptionInfoNumber
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val numberFromManager = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    subscriptionManager?.getPhoneNumber(subscriptionId)
                else -> {
                    val telephonyManager = context.getSystemService(TelephonyManager::class.java)
                    telephonyManager?.createForSubscriptionId(subscriptionId)?.line1Number
                }
            }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return numberFromManager ?: normalizedSubscriptionInfoNumber
    }

    private fun getRecentCall(context: Context, callTypes: List<Int>): RecentCall? {
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
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        ) ?: return null

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }
            return RecentCall(
                number = it.getString(0)?.trim()?.takeIf { value -> value.isNotBlank() },
                type = it.getInt(1),
                date = it.getLong(2),
                durationSeconds = it.getLong(3)
            )
        }
    }

    private fun getRecentCallNumber(context: Context, callTypes: List<Int>): String? {
        return getRecentCall(context, callTypes)?.number
    }

    private fun getCallDirectionLabel(callType: Int): String {
        return when (callType) {
            CallLog.Calls.INCOMING_TYPE -> "Gọi đến"
            CallLog.Calls.OUTGOING_TYPE -> "Gọi đi"
            else -> "Không xác định"
        }
    }

    private fun formatDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return when {
            minutes > 0 -> "%d phút %02d giây".format(Locale.getDefault(), minutes, seconds)
            else -> "%d giây".format(Locale.getDefault(), seconds)
        }
    }

    private fun buildCompletedCallKey(call: RecentCall): String {
        return listOf(
            call.type.toString(),
            call.number.orEmpty(),
            call.date.toString(),
            call.durationSeconds.toString()
        ).joinToString("|")
    }

    private fun formatNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private data class RecentCall(
        val number: String?,
        val type: Int,
        val date: Long,
        val durationSeconds: Long
    )

    companion object {
        private const val ACTION_SUBSCRIPTION_PHONE_STATE_CHANGED =
            "android.intent.action.SUBSCRIPTION_PHONE_STATE"
        private const val RECENT_CALL_WINDOW_MS = 2 * 60 * 1000L
        private var lastState: String = TelephonyManager.EXTRA_STATE_IDLE
        private var lastIncomingNumber: String? = null
        private var lastIncomingSimInfo: String? = null
        private var hasSentIncomingCallNotification: Boolean = false
        private var lastCompletedCallNotificationKey: String? = null
    }
}
