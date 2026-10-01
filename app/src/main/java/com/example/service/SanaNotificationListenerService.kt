package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SanaNotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val isMessaging: Boolean
)

class SanaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { addOrUpdateNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val key = it.key ?: "${it.packageName}_${it.id}"
            val current = _activeNotifications.value.toMutableList()
            current.removeAll { item -> item.id == key }
            _activeNotifications.value = current
        }
    }

    private fun addOrUpdateNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isBlank() && text.isBlank()) return

        val pkg = sbn.packageName ?: ""
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }

        val isMessaging = pkg.contains("whatsapp", ignoreCase = true) ||
                pkg.contains("message", ignoreCase = true) ||
                pkg.contains("telegram", ignoreCase = true) ||
                pkg.contains("signal", ignoreCase = true)

        val item = SanaNotificationItem(
            id = sbn.key ?: "${pkg}_${sbn.id}",
            packageName = pkg,
            appName = appName,
            title = title,
            text = text,
            timestamp = sbn.postTime,
            isMessaging = isMessaging
        )

        val current = _activeNotifications.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        // Keep max 20 latest notifications
        _activeNotifications.value = current.take(20)
    }

    private fun refreshNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = mutableListOf<SanaNotificationItem>()
            for (sbn in active) {
                val notification = sbn.notification ?: continue
                val extras = notification.extras ?: continue
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isBlank() && text.isBlank()) continue

                val pkg = sbn.packageName ?: ""
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg
                }

                val isMessaging = pkg.contains("whatsapp", ignoreCase = true) ||
                        pkg.contains("message", ignoreCase = true)

                list.add(
                    SanaNotificationItem(
                        id = sbn.key ?: "${pkg}_${sbn.id}",
                        packageName = pkg,
                        appName = appName,
                        title = title,
                        text = text,
                        timestamp = sbn.postTime,
                        isMessaging = isMessaging
                    )
                )
            }
            _activeNotifications.value = list.take(20)
        } catch (e: Exception) {
            // Ignore if security restricted
        }
    }

    companion object {
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _activeNotifications = MutableStateFlow<List<SanaNotificationItem>>(emptyList())
        val activeNotificationsList: StateFlow<List<SanaNotificationItem>> = _activeNotifications.asStateFlow()

        fun isNotificationAccessGranted(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
            return false
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
