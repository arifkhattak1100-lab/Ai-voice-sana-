package com.example.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.SanaNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionStatus(
    val name: String,
    val description: String,
    val manifestPermission: String?,
    val isSpecialAccess: Boolean = false,
    val isGranted: Boolean = false
)

class SanaPermissionManager(private val context: Context) {

    private val _permissionsState = MutableStateFlow<List<PermissionStatus>>(emptyList())
    val permissionsState: StateFlow<List<PermissionStatus>> = _permissionsState.asStateFlow()

    fun refreshPermissions() {
        val list = mutableListOf<PermissionStatus>()

        // 1. Microphone
        list.add(
            PermissionStatus(
                name = "Microphone",
                description = "Required for natural voice listening and wake word.",
                manifestPermission = Manifest.permission.RECORD_AUDIO,
                isGranted = isGranted(Manifest.permission.RECORD_AUDIO)
            )
        )

        // 2. Contacts
        list.add(
            PermissionStatus(
                name = "Contacts",
                description = "Allows finding contacts to call or message.",
                manifestPermission = Manifest.permission.READ_CONTACTS,
                isGranted = isGranted(Manifest.permission.READ_CONTACTS)
            )
        )

        // 3. Phone Calls
        list.add(
            PermissionStatus(
                name = "Phone Calls",
                description = "Allows placing direct calls when requested.",
                manifestPermission = Manifest.permission.CALL_PHONE,
                isGranted = isGranted(Manifest.permission.CALL_PHONE)
            )
        )

        // 4. Answering Calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            list.add(
                PermissionStatus(
                    name = "Call Answering",
                    description = "Allows answering incoming phone calls automatically.",
                    manifestPermission = Manifest.permission.ANSWER_PHONE_CALLS,
                    isGranted = isGranted(Manifest.permission.ANSWER_PHONE_CALLS)
                )
            )
        }

        // 5. Notifications Post
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionStatus(
                    name = "Notifications",
                    description = "Required to show background assistant status.",
                    manifestPermission = Manifest.permission.POST_NOTIFICATIONS,
                    isGranted = isGranted(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        // 6. Notification Access (Special)
        list.add(
            PermissionStatus(
                name = "Notification Access",
                description = "Allows SANA to read and summarize incoming messages.",
                manifestPermission = null,
                isSpecialAccess = true,
                isGranted = SanaNotificationListenerService.isNotificationAccessGranted(context)
            )
        )

        // 7. Camera
        list.add(
            PermissionStatus(
                name = "Camera",
                description = "Required for camera control and image understanding.",
                manifestPermission = Manifest.permission.CAMERA,
                isGranted = isGranted(Manifest.permission.CAMERA)
            )
        )

        // 8. Location
        list.add(
            PermissionStatus(
                name = "Location",
                description = "Provides precise navigation and nearby queries.",
                manifestPermission = Manifest.permission.ACCESS_FINE_LOCATION,
                isGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        )

        _permissionsState.value = list
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationAccessSettings() {
        SanaNotificationListenerService.openNotificationAccessSettings(context)
    }
}
