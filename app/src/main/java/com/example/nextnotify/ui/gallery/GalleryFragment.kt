package com.example.nextnotify.ui.gallery

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nextnotify.data.AppSettingsStore
import com.example.nextnotify.databinding.FragmentGalleryBinding
import java.util.concurrent.Executors

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var adapter: AppSelectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        settingsStore = AppSettingsStore(requireContext())
        adapter = AppSelectionAdapter { item, enabled ->
            settingsStore.setPackageEnabled(item.packageName, enabled)
            adapter.updateSelection(settingsStore.getSelectedPackages())
        }

        setupViews()
        loadInstalledApps()

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAccessStatus()
        if (_binding != null) {
            adapter.updateSelection(settingsStore.getSelectedPackages())
        }
    }

    private fun setupViews() {
        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter
        binding.buttonOpenNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        updateNotificationAccessStatus()
    }

    private fun updateNotificationAccessStatus() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)

        binding.textNotificationAccessStatus.text = if (enabled) {
            "Notification Access đã được bật. Chỉ những app được bật trong danh sách bên dưới mới được gửi."
        } else {
            "Notification Access chưa được bật. Hãy cấp quyền để app đọc thông báo từ các ứng dụng đã chọn."
        }
    }

    private fun loadInstalledApps() {
        binding.progressApps.visibility = View.VISIBLE

        EXECUTOR.execute {
            val context = context ?: return@execute
            val packageManager = context.packageManager
            val installedApps = getLaunchableActivities(packageManager)
                .map { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    InstalledAppItem(
                        packageName = packageName,
                        appName = resolveInfo.loadLabel(packageManager)?.toString().orEmpty().ifBlank {
                            packageName
                        },
                        icon = resolveInfo.loadIcon(packageManager)
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }

            MAIN_HANDLER.post {
                if (_binding == null) {
                    return@post
                }
                binding.progressApps.visibility = View.GONE
                adapter.submitItems(installedApps, settingsStore.getSelectedPackages())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLaunchableActivities(packageManager: android.content.pm.PackageManager): List<ResolveInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                android.content.pm.PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }
    }

    @Suppress("unused")
    private fun getInstalledApplications(packageManager: android.content.pm.PackageManager) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                android.content.pm.PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            packageManager.getInstalledApplications(0)
        }

    companion object {
        private val EXECUTOR = Executors.newSingleThreadExecutor()
        private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}