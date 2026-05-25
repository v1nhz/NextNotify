package com.example.nextnotify.telegram

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.nextnotify.data.AppSettingsStore
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class TelegramSender(context: Context) {

    private val appContext = context.applicationContext
    private val settingsStore = AppSettingsStore(appContext)
    private val executor = EXECUTOR
    private val mainHandler = Handler(Looper.getMainLooper())

    fun sendConfiguredMessage(
        message: String,
        callback: ((Result<Unit>) -> Unit)? = null
    ) {
        val botToken = settingsStore.getBotToken()
        val chatId = settingsStore.getChatId()

        if (botToken.isBlank() || chatId.isBlank()) {
            callback?.invoke(Result.failure(IllegalStateException("Telegram token/chat id chưa được cấu hình.")))
            return
        }

        executor.execute {
            val result = runCatching {
                sendBlocking(botToken = botToken, chatId = chatId, message = message)
            }
            if (callback != null) {
                mainHandler.post { callback.invoke(result) }
            }
        }
    }

    private fun sendBlocking(botToken: String, chatId: String, message: String) {
        val endpoint = URL("https://api.telegram.org/bot$botToken/sendMessage")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }

        val body = buildString {
            append("chat_id=")
            append(URLEncoder.encode(chatId, Charsets.UTF_8.name()))
            append("&text=")
            append(URLEncoder.encode(message, Charsets.UTF_8.name()))
        }

        try {
            BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(
                    "Telegram API trả về lỗi $responseCode${if (errorBody.isNotBlank()) ": $errorBody" else ""}"
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
