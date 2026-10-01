package com.example.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.model.ToolCallResult

class SanaPhoneTools(private val context: Context) {

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    /**
     * Opens an installed application by common name or package.
     */
    fun openApp(appName: String): ToolCallResult {
        if (appName.isBlank()) {
            return ToolCallResult("openApp", appName, false, "Please specify which app to open, Boss.")
        }

        // Direct common shortcuts
        val lower = appName.trim().lowercase()
        val directPackage = when {
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("map") -> "com.google.android.apps.maps"
            lower.contains("spotify") -> "com.spotify.music"
            lower.contains("gmail") -> "com.google.android.gm"
            lower.contains("message") || lower.contains("sms") -> "com.google.android.apps.messaging"
            lower.contains("clock") || lower.contains("alarm") -> "com.google.android.deskclock"
            lower.contains("calc") -> "com.google.android.calculator"
            lower.contains("camera") -> return openCamera()
            lower.contains("gallery") || lower.contains("photo") -> return openGallery()
            lower.contains("setting") -> return openSettings("general")
            else -> null
        }

        if (directPackage != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(directPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ToolCallResult("openApp", appName, true, "Opened $appName for you, Boss.")
            }
        }

        // Search installed applications
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            var matchedPackage: String? = null
            var matchedLabel: String? = null

            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.equals(appName, ignoreCase = true) || label.lowercase().contains(lower)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        matchedPackage = app.packageName
                        matchedLabel = label
                        break
                    }
                }
            }

            if (matchedPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matchedPackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return ToolCallResult("openApp", matchedLabel ?: appName, true, "Opened $matchedLabel for you, Boss.")
                }
            }
        } catch (e: Exception) {
            // Handled below
        }

        // If not found installed, fall back to searching Play Store or web
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            ToolCallResult("openApp", appName, true, "I couldn't find $appName installed, Boss. Opening Play Store to install it.")
        } catch (e: Exception) {
            ToolCallResult("openApp", appName, false, "Could not open $appName. The app might not be installed, Boss.")
        }
    }

    /**
     * Launches the default system camera app.
     */
    fun openCamera(): ToolCallResult {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("openCamera", "Camera", true, "Camera is open, Boss.")
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ToolCallResult("openCamera", "Camera", true, "Camera launched, Boss.")
            } catch (ex: Exception) {
                ToolCallResult("openCamera", "Camera", false, "Could not open camera: ${ex.message}")
            }
        }
    }

    /**
     * Opens the device photo gallery.
     */
    fun openGallery(): ToolCallResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("openGallery", "Gallery", true, "Gallery is open, Boss.")
        } catch (e: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                ToolCallResult("openGallery", "Gallery", true, "Gallery launched, Boss.")
            } catch (ex: Exception) {
                ToolCallResult("openGallery", "Gallery", false, "Could not open gallery: ${ex.message}")
            }
        }
    }

    /**
     * Opens Google Maps for a destination or general map view.
     */
    fun openMaps(destination: String = ""): ToolCallResult {
        return try {
            val uri = if (destination.isNotBlank()) {
                Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            } else {
                Uri.parse("geo:0,0?q=")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/${Uri.encode(destination)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
            ToolCallResult("openMaps", destination.ifBlank { "Maps" }, true, "Map opened for ${if (destination.isNotBlank()) destination else "your location"}, Boss.")
        } catch (e: Exception) {
            ToolCallResult("openMaps", destination, false, "Could not open Maps: ${e.message}")
        }
    }

    /**
     * Starts turn-by-turn navigation to a destination.
     */
    fun startNavigation(destination: String): ToolCallResult {
        if (destination.isBlank()) {
            return ToolCallResult("startNavigation", "", false, "Boss, please tell me where you want to navigate to.")
        }
        return try {
            val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(destination)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
            ToolCallResult("startNavigation", destination, true, "Starting navigation to $destination, Boss. Drive safely! ❤️")
        } catch (e: Exception) {
            ToolCallResult("startNavigation", destination, false, "Failed to start navigation: ${e.message}")
        }
    }

    /**
     * Launches a URL in the browser.
     */
    fun launchUrl(url: String): ToolCallResult {
        return try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("launchUrl", formatted, true, "Opened $formatted, Boss.")
        } catch (e: Exception) {
            ToolCallResult("launchUrl", url, false, "Failed to launch URL: ${e.message}")
        }
    }

    /**
     * Reads battery status and charging state.
     */
    fun getBatteryStatus(): ToolCallResult {
        return try {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct: Float = level * 100 / (scale.toFloat())
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val message = if (isCharging) {
                "Your battery is at ${batteryPct.toInt()}%, Boss, and it is currently charging! ⚡"
            } else {
                "Your battery is at ${batteryPct.toInt()}%, Boss. ${if (batteryPct < 20) "You might want to plug it in soon!" else "Plenty of power left."}"
            }
            ToolCallResult("getBatteryStatus", "${batteryPct.toInt()}%", true, message)
        } catch (e: Exception) {
            ToolCallResult("getBatteryStatus", "Battery", false, "Could not read battery status: ${e.message}")
        }
    }

    /**
     * Retrieves hardware and OS information.
     */
    fun getDeviceInformation(): ToolCallResult {
        return try {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            val androidVersion = Build.VERSION.RELEASE
            val sdkInt = Build.VERSION.SDK_INT

            val stat = StatFs(Environment.getDataDirectory().path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            val gigaBytesAvailable = bytesAvailable / (1024 * 1024 * 1024)

            val info = "Boss, your device is a $manufacturer $model running Android $androidVersion (API $sdkInt). You have approximately $gigaBytesAvailable GB of free storage."
            ToolCallResult("getDeviceInformation", "$manufacturer $model", true, info)
        } catch (e: Exception) {
            ToolCallResult("getDeviceInformation", "Device", false, "Could not fetch device details: ${e.message}")
        }
    }

    /**
     * Opens system settings or specific settings panels.
     */
    fun openSettings(settingType: String = "general"): ToolCallResult {
        return try {
            val action = when (settingType.lowercase()) {
                "wifi", "wireless" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "notifications" -> Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
                "date", "time" -> Settings.ACTION_DATE_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("openSettings", settingType, true, "Opened $settingType settings for you, Boss.")
        } catch (e: Exception) {
            ToolCallResult("openSettings", settingType, false, "Could not open $settingType settings: ${e.message}")
        }
    }

    /**
     * Controls supported device settings such as Flashlight/Torch and Volume.
     */
    fun setSupportedDeviceSetting(setting: String, value: String): ToolCallResult {
        return try {
            when (setting.lowercase()) {
                "flashlight", "torch" -> {
                    val enable = value.contains("on", ignoreCase = true) ||
                            value.contains("enable", ignoreCase = true) ||
                            value.contains("true", ignoreCase = true)
                    val cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                        val chars = cameraManager?.getCameraCharacteristics(id)
                        chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (cameraId != null) {
                        cameraManager?.setTorchMode(cameraId, enable)
                        ToolCallResult("setSupportedDeviceSetting", setting, true, "Flashlight is now ${if (enable) "ON" else "OFF"}, Boss! 💡")
                    } else {
                        ToolCallResult("setSupportedDeviceSetting", setting, false, "No flashlight hardware found on this device, Boss.")
                    }
                }
                "volume" -> {
                    val percent = value.filter { it.isDigit() }.toIntOrNull()
                    if (percent != null && audioManager != null) {
                        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                        val targetVol = (percent * maxVol / 100).coerceIn(0, maxVol)
                        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                        ToolCallResult("setSupportedDeviceSetting", "volume", true, "Media volume set to $percent%, Boss! 🔊")
                    } else {
                        openSettings("sound")
                    }
                }
                else -> {
                    openSettings(setting)
                }
            }
        } catch (e: Exception) {
            ToolCallResult("setSupportedDeviceSetting", setting, false, "Could not change $setting: ${e.message}")
        }
    }
}
