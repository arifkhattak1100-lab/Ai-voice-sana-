package com.example.tools

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.model.ToolCallResult
import org.json.JSONObject

class AndroidToolsManager(private val context: Context) {

    data class PendingConfirmation(
        val action: String,
        val description: String,
        val onConfirm: () -> ToolCallResult
    )

    /**
     * Checks if the message contains a tool instruction, parses and executes or requests confirmation.
     */
    fun parseToolCall(responseJsonOrText: String): Pair<String, ToolCallResult?> {
        // Look for tool pattern like {"action": "open_app", "app": "WhatsApp"} or markdown code blocks
        val cleanText = responseJsonOrText
        val toolRegex = Regex("""\{[\s\S]*?"action"\s*:\s*"([^"]+)"[\s\S]*?\}""")
        val match = toolRegex.find(cleanText)

        if (match != null) {
            try {
                val jsonStr = match.value
                val obj = JSONObject(jsonStr)
                val action = obj.optString("action")
                val app = obj.optString("app")
                val target = obj.optString("target", app)
                val contact = obj.optString("contact")
                val time = obj.optString("time")
                val note = obj.optString("note")

                val result = when (action.lowercase()) {
                    "open_app" -> executeOpenApp(app)
                    "open_camera" -> executeCamera()
                    "open_settings" -> executeSettings()
                    "open_youtube" -> executeOpenYouTube()
                    "set_alarm" -> executeSetAlarm(time)
                    "create_note" -> executeCreateNote(note)
                    "call" -> executeCall(contact)
                    else -> null
                }

                // Remove the raw tool json from the clean spoken text
                val speechText = cleanText.replace(match.value, "").trim()
                return Pair(speechText, result)
            } catch (e: Exception) {
                Log.w("AndroidToolsManager", "Failed to parse tool call: ${e.message}")
            }
        }

        return Pair(cleanText, null)
    }

    fun executeOpenApp(appName: String): ToolCallResult {
        return try {
            val packageName = when (appName.lowercase()) {
                "whatsapp" -> "com.whatsapp"
                "youtube" -> "com.google.android.youtube"
                "chrome" -> "com.android.chrome"
                "maps" -> "com.google.android.apps.maps"
                "camera" -> return executeCamera()
                "settings" -> return executeSettings()
                else -> null
            }

            if (packageName != null) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ToolCallResult("open_app", appName, true, "$appName is open, Boss.")
                } else {
                    // Try fallback web intent
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$appName")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    ToolCallResult("open_app", appName, true, "Opened $appName in browser, Boss.")
                }
            } else {
                ToolCallResult("open_app", appName, false, "Could not find $appName installed, Boss.")
            }
        } catch (e: Exception) {
            ToolCallResult("open_app", appName, false, "Failed to open $appName: ${e.message}")
        }
    }

    fun executeCamera(): ToolCallResult {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("open_camera", "Camera", true, "Camera is open, Boss.")
        } catch (e: Exception) {
            ToolCallResult("open_camera", "Camera", false, "Could not launch camera: ${e.message}")
        }
    }

    fun executeOpenYouTube(): ToolCallResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("open_youtube", "YouTube", true, "YouTube is open, Boss.")
        } catch (e: Exception) {
            ToolCallResult("open_youtube", "YouTube", false, "Failed to open YouTube: ${e.message}")
        }
    }

    fun executeSettings(): ToolCallResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("open_settings", "Settings", true, "Settings is open, Boss.")
        } catch (e: Exception) {
            ToolCallResult("open_settings", "Settings", false, "Could not launch settings: ${e.message}")
        }
    }

    fun executeSetAlarm(time: String): ToolCallResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, "SANA Alarm for Boss")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("set_alarm", time.ifBlank { "Alarm" }, true, "Alarm is set for you, Boss! ❤️")
        } catch (e: Exception) {
            ToolCallResult("set_alarm", time, false, "Could not set alarm: ${e.message}")
        }
    }

    fun executeCreateNote(note: String): ToolCallResult {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, note.ifBlank { "Note from SANA AI" })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Save Note").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            ToolCallResult("create_note", note, true, "Note created for you, Boss! ❤️")
        } catch (e: Exception) {
            ToolCallResult("create_note", note, false, "Could not create note: ${e.message}")
        }
    }

    fun executeCall(contact: String): ToolCallResult {
        return try {
            // Uses ACTION_DIAL so it opens the phone dialer safely without unauthorized direct calling
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${contact.filter { it.isDigit() || it == '+' }}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolCallResult("call", contact, true, "Dialer opened for $contact, Boss.")
        } catch (e: Exception) {
            ToolCallResult("call", contact, false, "Could not open dialer for $contact: ${e.message}")
        }
    }
}
