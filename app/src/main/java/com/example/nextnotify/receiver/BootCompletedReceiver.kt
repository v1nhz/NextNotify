package com.example.nextnotify.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import com.example.nextnotify.NextNotifyService
import com.example.nextnotify.service.NotificationForwardingService

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                NextNotifyService.start(context)
                requestNotificationListenerRebind(context)
            }
        }
    }

    private fun requestNotificationListenerRebind(context: Context) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationForwardingService::class.java)
            )
        } catch (_: Exception) {
        }
    }
}
