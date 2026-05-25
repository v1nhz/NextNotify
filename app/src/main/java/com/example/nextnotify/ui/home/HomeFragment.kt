package com.example.nextnotify.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.databinding.FragmentHomeBinding
import com.example.nextnotify.telegram.TelegramSender

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var telegramSender: TelegramSender

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        settingsStore = AppSettingsStore(requireContext())
        telegramSender = TelegramSender(requireContext())

        bindCurrentConfig()
        bindActions()

        return binding.root
    }

    private fun bindCurrentConfig() {
        binding.editBotToken.setText(settingsStore.getBotToken())
        binding.editChatId.setText(settingsStore.getChatId())
        binding.textTelegramStatus.text = if (settingsStore.hasTelegramConfig()) {
            "Đã nạp cấu hình Telegram hiện tại."
        } else {
            "Chưa có cấu hình Telegram. Hãy nhập bot token và chat id."
        }
    }

    private fun bindActions() {
        binding.buttonSaveTelegram.setOnClickListener {
            val botToken = binding.editBotToken.text?.toString().orEmpty().trim()
            val chatId = binding.editChatId.text?.toString().orEmpty().trim()

            if (botToken.isBlank() || chatId.isBlank()) {
                binding.textTelegramStatus.text = "Bot token và chat id không được để trống."
                return@setOnClickListener
            }

            settingsStore.saveTelegramConfig(botToken = botToken, chatId = chatId)
            binding.textTelegramStatus.text = "Đã lưu cấu hình Telegram."
        }

        binding.buttonSendTest.setOnClickListener {
            val botToken = binding.editBotToken.text?.toString().orEmpty().trim()
            val chatId = binding.editChatId.text?.toString().orEmpty().trim()

            if (botToken.isBlank() || chatId.isBlank()) {
                binding.textTelegramStatus.text = "Hãy nhập bot token và chat id trước khi gửi thử."
                return@setOnClickListener
            }

            settingsStore.saveTelegramConfig(botToken = botToken, chatId = chatId)
            binding.buttonSendTest.isEnabled = false
            binding.textTelegramStatus.text = "Đang gửi tin nhắn thử lên Telegram..."

            telegramSender.sendConfiguredMessage("NextNotify test message") { result ->
                if (_binding == null) {
                    return@sendConfiguredMessage
                }
                binding.buttonSendTest.isEnabled = true
                binding.textTelegramStatus.text = result.fold(
                    onSuccess = { "Gửi thử thành công. Kiểm tra tin nhắn trên Telegram." },
                    onFailure = { error -> "Gửi thử thất bại: ${error.message.orEmpty()}" }
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}