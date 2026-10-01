package com.example.tools

import android.content.Context
import android.util.Log
import com.example.data.model.ToolCallResult
import org.json.JSONObject

enum class ConfirmationLevel {
    SAFE,       // Auto-execute safe read-only and open actions
    CONFIRM,    // Confirm calls, messages, and state changes
    STRICT      // Confirm all external actions
}

data class PendingConfirmation(
    val action: String,
    val description: String,
    val execute: () -> ToolCallResult
)

data class ToolExecutionOutcome(
    val cleanSpeech: String,
    val result: ToolCallResult?,
    val pendingConfirmation: PendingConfirmation? = null
)

class SanaToolRouter(private val context: Context) {

    val phoneTools = SanaPhoneTools(context)
    val callTools = SanaCallTools(context)
    val whatsAppTools = SanaWhatsAppTools(context, callTools)
    val notificationTools = SanaNotificationTools(context)
    val mediaTools = SanaMediaTools(context)

    var confirmationEnabled: Boolean = true
    var confirmationLevel: ConfirmationLevel = ConfirmationLevel.CONFIRM

    /**
     * Parses JSON-based tool call returned by SanaBrain or Gemini response.
     */
    fun routeCommand(rawResponse: String): ToolExecutionOutcome {
        val toolRegex = Regex("""\{[\s\S]*?"action"\s*:\s*"([^"]+)"[\s\S]*?\}""")
        val match = toolRegex.find(rawResponse)

        if (match != null) {
            try {
                val jsonStr = match.value
                val obj = JSONObject(jsonStr)
                val action = obj.optString("action")
                val cleanSpeech = rawResponse.replace(match.value, "").trim()

                val outcome = executeAction(action, obj)
                return ToolExecutionOutcome(
                    cleanSpeech = if (cleanSpeech.isNotBlank()) cleanSpeech else (outcome.result?.message ?: "Done, Boss."),
                    result = outcome.result,
                    pendingConfirmation = outcome.pendingConfirmation
                )
            } catch (e: Exception) {
                Log.w("SanaToolRouter", "Failed to parse tool call: ${e.message}")
            }
        }

        // Direct natural command heuristic fallback if AI responded with text only
        return fallbackHeuristicRouting(rawResponse)
    }

    private fun executeAction(action: String, args: JSONObject): ToolExecutionOutcome {
        val app = args.optString("app")
        val contact = args.optString("contact")
        val message = args.optString("message")
        val destination = args.optString("destination")
        val url = args.optString("url")
        val setting = args.optString("setting")
        val value = args.optString("value")

        return when (action.lowercase()) {
            // Safe tools
            "openapp" -> ToolExecutionOutcome("", phoneTools.openApp(app))
            "opencamera" -> ToolExecutionOutcome("", phoneTools.openCamera())
            "opengallery" -> ToolExecutionOutcome("", phoneTools.openGallery())
            "openmaps" -> ToolExecutionOutcome("", phoneTools.openMaps(destination))
            "startnavigation" -> ToolExecutionOutcome("", phoneTools.startNavigation(destination))
            "opensettings" -> ToolExecutionOutcome("", phoneTools.openSettings(setting))
            "getbatterystatus" -> ToolExecutionOutcome("", phoneTools.getBatteryStatus())
            "getdeviceinformation" -> ToolExecutionOutcome("", phoneTools.getDeviceInformation())
            "launchurl" -> ToolExecutionOutcome("", phoneTools.launchUrl(url))
            "mediaplay" -> ToolExecutionOutcome("", mediaTools.mediaPlay())
            "mediapause" -> ToolExecutionOutcome("", mediaTools.mediaPause())
            "medianext" -> ToolExecutionOutcome("", mediaTools.mediaNext())
            "mediaprevious" -> ToolExecutionOutcome("", mediaTools.mediaPrevious())
            "readsupportednotifications" -> ToolExecutionOutcome("", notificationTools.readSupportedNotifications())

            // Calls with Confirmation Logic
            "makecall" -> {
                if (confirmationEnabled && (confirmationLevel == ConfirmationLevel.CONFIRM || confirmationLevel == ConfirmationLevel.STRICT)) {
                    val pending = PendingConfirmation(
                        action = "makeCall",
                        description = "Boss, should I call $contact?"
                    ) {
                        callTools.makeCall(contact)
                    }
                    ToolExecutionOutcome("Boss, should I call $contact?", null, pending)
                } else {
                    ToolExecutionOutcome("", callTools.makeCall(contact))
                }
            }
            "findcontact" -> {
                val res = callTools.findContact(contact)
                ToolExecutionOutcome("", ToolCallResult("findContact", contact, res.found, res.message))
            }
            "answercall" -> ToolExecutionOutcome("", callTools.answerCall())
            "endcall" -> ToolExecutionOutcome("", callTools.endCall())

            // WhatsApp Tools
            "openwhatsapp" -> ToolExecutionOutcome("", whatsAppTools.openWhatsApp())
            "openconversation" -> ToolExecutionOutcome("", whatsAppTools.openConversation(contact))
            "composemessage" -> {
                if (confirmationEnabled && confirmationLevel == ConfirmationLevel.STRICT) {
                    val pending = PendingConfirmation(
                        action = "composeMessage",
                        description = "Boss, should I prepare message to $contact: \"$message\"?"
                    ) {
                        whatsAppTools.composeMessage(contact, message)
                    }
                    ToolExecutionOutcome("Boss, should I prepare message to $contact?", null, pending)
                } else {
                    ToolExecutionOutcome("", whatsAppTools.composeMessage(contact, message))
                }
            }

            // Device Settings (e.g. Flashlight)
            "setsupporteddevicesetting" -> ToolExecutionOutcome("", phoneTools.setSupportedDeviceSetting(setting, value))

            else -> ToolExecutionOutcome("", null)
        }
    }

    private fun fallbackHeuristicRouting(text: String): ToolExecutionOutcome {
        val lower = text.lowercase().trim()
        return when {
            lower.contains("battery") -> ToolExecutionOutcome("", phoneTools.getBatteryStatus())
            lower.contains("read notification") || lower.contains("do i have any messages") || lower.contains("check notification") ->
                ToolExecutionOutcome("", notificationTools.readSupportedNotifications())
            lower.contains("open camera") -> ToolExecutionOutcome("", phoneTools.openCamera())
            lower.contains("open gallery") -> ToolExecutionOutcome("", phoneTools.openGallery())
            lower.contains("pause music") || lower.contains("pause media") -> ToolExecutionOutcome("", mediaTools.mediaPause())
            lower.contains("play music") || lower.contains("resume music") -> ToolExecutionOutcome("", mediaTools.mediaPlay())
            lower.contains("next song") || lower.contains("next track") -> ToolExecutionOutcome("", mediaTools.mediaNext())
            lower.contains("previous song") || lower.contains("previous track") -> ToolExecutionOutcome("", mediaTools.mediaPrevious())
            lower.contains("device info") || lower.contains("phone info") -> ToolExecutionOutcome("", phoneTools.getDeviceInformation())
            else -> ToolExecutionOutcome(text, null)
        }
    }
}
