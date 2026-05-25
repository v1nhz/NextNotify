package com.example.nextnotify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.telegram.TelegramSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val settingsStore = AppSettingsStore(context)
        if (!settingsStore.hasTelegramConfig() || !settingsStore.isSmsForwardingEnabled()) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty().ifBlank { "Không xác định" }
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()

        val text = buildString {
            appendLine("NextNotify")
            appendLine("Loại: SMS")
            appendLine("Từ: $sender")
            if (body.isNotBlank()) {
                appendLine("Nội dung: $body")
            }
            append("Thời gian: ${formatNow()}")
        }

        TelegramSender(context).sendConfiguredMessage(text)
    }

    private fun formatNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
