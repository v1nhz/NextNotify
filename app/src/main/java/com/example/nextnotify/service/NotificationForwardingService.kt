package com.example.nextnotify.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.telegram.TelegramSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationForwardingService : NotificationListenerService() {

    private lateinit var settingsStore: AppSettingsStore
    private lateinit var telegramSender: TelegramSender

    override fun onCreate() {
        super.onCreate()
        settingsStore = AppSettingsStore(this)
        telegramSender = TelegramSender(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!settingsStore.hasTelegramConfig()) {
            return
        }

        val packageName = sbn.packageName
        if (packageName == packageNameForOwnApp()) {
            return
        }

        if (!settingsStore.isPackageEnabled(packageName)) {
            return
        }

        val extras = sbn.notification.extras
        val title = extractNotificationTitle(sbn.notification).trim()
        val text = extractNotificationText(sbn.notification).trim()

        if (title.isBlank() && text.isBlank()) {
            return
        }

        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

        val message = buildString {
            appendLine("NextNotify")
            appendLine("Loại: Notification ứng dụng")
            appendLine("Ứng dụng: $appLabel")
            appendLine("Package: $packageName")
            if (title.isNotBlank()) {
                appendLine("Tiêu đề: $title")
            }
            if (text.isNotBlank()) {
                appendLine("Nội dung: $text")
            }
            append("Thời gian: ${formatTimestamp(sbn.postTime)}")
        }

        telegramSender.sendConfiguredMessage(message)
    }

    private fun extractNotificationTitle(notification: Notification): String {
        val extras = notification.extras ?: return notification.tickerText?.toString().orEmpty()

        val candidates = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_CONVERSATION_TITLE,
            Notification.EXTRA_SUB_TEXT
        )

        return candidates.firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty().ifBlank { notification.tickerText?.toString().orEmpty().trim() }
    }

    private fun extractNotificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val candidates = listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_SUB_TEXT
        )

        candidates.firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }?.let { return it }

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString().trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (lines.isNotEmpty()) {
            return lines.joinToString(separator = " | ")
        }

        val messages = getParcelableMessages(extras, Notification.EXTRA_MESSAGES)
            ?.mapNotNull { it as? Bundle }
            ?.mapNotNull(::formatMessagingStyleBundle)
            .orEmpty()
        if (messages.isNotEmpty()) {
            return messages.joinToString(separator = " | ")
        }

        return notification.tickerText?.toString().orEmpty().trim()
    }

    private fun formatMessagingStyleBundle(bundle: Bundle): String? {
        val messageText = bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
        val sender = bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()

        return when {
            sender.isNotBlank() && messageText.isNotBlank() -> "$sender: $messageText"
            messageText.isNotBlank() -> messageText
            sender.isNotBlank() -> sender
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun getParcelableMessages(extras: Bundle, key: String): Array<Parcelable>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(key, Parcelable::class.java)
        } else {
            extras.getParcelableArray(key)
        }
    }

    private fun packageNameForOwnApp(): String = applicationContext.packageName

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}
