package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.ToolCallResult

class SanaWhatsAppTools(
    private val context: Context,
    private val callTools: SanaCallTools
) {

    /**
     * Launches WhatsApp application.
     */
    fun openWhatsApp(): ToolCallResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
            ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ToolCallResult("openWhatsApp", "WhatsApp", true, "WhatsApp is open, Boss! 💬")
        } else {
            // Direct to Play Store
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(storeIntent)
                ToolCallResult("openWhatsApp", "WhatsApp", false, "WhatsApp is not installed on your phone, Boss. Opened Play Store to download it.")
            } catch (e: Exception) {
                ToolCallResult("openWhatsApp", "WhatsApp", false, "WhatsApp is not installed on this device, Boss.")
            }
        }
    }

    /**
     * Opens a specific WhatsApp chat conversation for a contact or number.
     */
    fun openConversation(contactOrNumber: String): ToolCallResult {
        if (contactOrNumber.isBlank()) {
            return openWhatsApp()
        }

        val digits = contactOrNumber.filter { it.isDigit() }
        val targetPhone = if (digits.length >= 7) {
            digits
        } else {
            val lookup = callTools.findContact(contactOrNumber)
            if (lookup.found && lookup.number != null) {
                lookup.number.filter { it.isDigit() }
            } else {
                null
            }
        }

        return if (targetPhone != null) {
            try {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$targetPhone")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    ToolCallResult("openConversation", contactOrNumber, true, "Opened WhatsApp conversation with $contactOrNumber, Boss.")
                } else {
                    // Fallback to browser or open general WhatsApp
                    val webIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webIntent)
                    ToolCallResult("openConversation", contactOrNumber, true, "Opening WhatsApp chat with $contactOrNumber, Boss.")
                }
            } catch (e: Exception) {
                ToolCallResult("openConversation", contactOrNumber, false, "Could not open conversation: ${e.message}")
            }
        } else {
            // Could not find phone number, open WhatsApp general
            openWhatsApp()
            ToolCallResult(
                "openConversation",
                contactOrNumber,
                true,
                "I couldn't find a phone number for $contactOrNumber, Boss. I've opened WhatsApp so you can select them from your chat list."
            )
        }
    }

    /**
     * Prepares a WhatsApp message for a contact.
     * Complies with user requirement:
     * "Boss, I've opened the conversation and prepared the message. Android requires you to tap Send."
     */
    fun composeMessage(contactOrNumber: String, messageText: String): ToolCallResult {
        val digits = contactOrNumber.filter { it.isDigit() }
        val targetPhone = if (digits.length >= 7) {
            digits
        } else {
            val lookup = callTools.findContact(contactOrNumber)
            if (lookup.found && lookup.number != null) {
                lookup.number.filter { it.isDigit() }
            } else {
                null
            }
        }

        return try {
            if (targetPhone != null) {
                val encodedMsg = Uri.encode(messageText)
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$targetPhone&text=$encodedMsg")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolCallResult(
                    "composeMessage",
                    "$contactOrNumber: $messageText",
                    true,
                    "Boss, I've opened the WhatsApp conversation with $contactOrNumber and prepared the message. Android requires you to tap Send."
                )
            } else {
                // Share intent targeted to WhatsApp
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    ToolCallResult(
                        "composeMessage",
                        messageText,
                        true,
                        "Boss, I've opened WhatsApp and filled your message. Please select $contactOrNumber and tap Send."
                    )
                } else {
                    // Fallback to SMS
                    val smsUri = Uri.parse("smsto:${contactOrNumber.filter { it.isDigit() || it == '+' }}")
                    val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                        putExtra("sms_body", messageText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(smsIntent)
                    ToolCallResult(
                        "composeMessage",
                        messageText,
                        true,
                        "WhatsApp was not found, Boss. I prepared your message in SMS instead. Please tap Send."
                    )
                }
            }
        } catch (e: Exception) {
            ToolCallResult("composeMessage", contactOrNumber, false, "Could not prepare WhatsApp message: ${e.message}")
        }
    }
}
