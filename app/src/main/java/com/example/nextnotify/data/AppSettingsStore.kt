package com.example.nextnotify.data

import android.content.Context

class AppSettingsStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveTelegramConfig(botToken: String, chatId: String) {
        preferences.edit()
            .putString(KEY_BOT_TOKEN, botToken.trim())
            .putString(KEY_CHAT_ID, chatId.trim())
            .apply()
    }

    fun getBotToken(): String = preferences.getString(KEY_BOT_TOKEN, "").orEmpty()

    fun getChatId(): String = preferences.getString(KEY_CHAT_ID, "").orEmpty()

    fun hasTelegramConfig(): Boolean = getBotToken().isNotBlank() && getChatId().isNotBlank()

    fun getSelectedPackages(): Set<String> =
        preferences.getStringSet(KEY_SELECTED_PACKAGES, emptySet())?.toSet().orEmpty()

    fun hasSelectedPackages(): Boolean = getSelectedPackages().isNotEmpty()

    fun isPackageEnabled(packageName: String): Boolean {
        val selectedPackages = getSelectedPackages()
        return selectedPackages.isEmpty() || selectedPackages.contains(packageName)
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        val updatedPackages = getSelectedPackages().toMutableSet()
        if (enabled) {
            updatedPackages.add(packageName)
        } else {
            updatedPackages.remove(packageName)
        }
        preferences.edit().putStringSet(KEY_SELECTED_PACKAGES, updatedPackages).apply()
    }

    fun isSmsForwardingEnabled(): Boolean =
        preferences.getBoolean(KEY_FORWARD_SMS, false)

    fun setSmsForwardingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FORWARD_SMS, enabled).apply()
    }

    fun isIncomingCallForwardingEnabled(): Boolean =
        preferences.getBoolean(KEY_FORWARD_INCOMING_CALLS, false)

    fun setIncomingCallForwardingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FORWARD_INCOMING_CALLS, enabled).apply()
    }

    fun isMissedCallForwardingEnabled(): Boolean =
        preferences.getBoolean(KEY_FORWARD_MISSED_CALLS, false)

    fun setMissedCallForwardingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_FORWARD_MISSED_CALLS, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "next_notify_settings"
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_FORWARD_SMS = "forward_sms"
        private const val KEY_FORWARD_INCOMING_CALLS = "forward_incoming_calls"
        private const val KEY_FORWARD_MISSED_CALLS = "forward_missed_calls"
    }
}
