package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.example.data.model.ToolCallResult

class SanaCallTools(private val context: Context) {

    private val telecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    /**
     * Resolves a contact name to their phone number using Android Contacts Provider.
     */
    fun findContact(contactName: String): ContactLookupResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ContactLookupResult(
                found = false,
                name = contactName,
                number = null,
                permissionRequired = true,
                message = "Boss, I need Contacts permission to look up $contactName. Please grant it in Permissions."
            )
        }

        var matchedNumber: String? = null
        var matchedName: String? = null
        var cursor: Cursor? = null

        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$contactName%")

            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)

            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                matchedName = cursor.getString(nameIndex)
                matchedNumber = cursor.getString(numberIndex)
            }
        } catch (e: Exception) {
            // Handled below
        } finally {
            cursor?.close()
        }

        return if (matchedNumber != null) {
            ContactLookupResult(
                found = true,
                name = matchedName ?: contactName,
                number = matchedNumber,
                permissionRequired = false,
                message = "Found contact for $matchedName: $matchedNumber"
            )
        } else {
            ContactLookupResult(
                found = false,
                name = contactName,
                number = null,
                permissionRequired = false,
                message = "I couldn't find a contact named $contactName in your phone book, Boss."
            )
        }
    }

    /**
     * Places a phone call. If CALL_PHONE is granted, uses direct call; otherwise opens Dialer.
     */
    fun makeCall(target: String): ToolCallResult {
        if (target.isBlank()) {
            return ToolCallResult("makeCall", target, false, "Boss, who would you like me to call?")
        }

        // Determine if target is directly a number or a name
        val digits = target.filter { it.isDigit() || it == '+' }
        val phoneNumber = if (digits.length >= 3) {
            digits
        } else {
            val lookup = findContact(target)
            if (lookup.permissionRequired) {
                // Return honest report and guidance
                return ToolCallResult("makeCall", target, false, lookup.message)
            }
            if (!lookup.found || lookup.number == null) {
                // If not found in contacts, open dialer with whatever name or digits were given
                return openDialer(target)
            }
            lookup.number
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        return if (hasCallPermission) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:${Uri.encode(phoneNumber)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                ToolCallResult("makeCall", target, true, "Calling $target ($phoneNumber) now, Boss! 📞")
            } catch (e: Exception) {
                openDialer(phoneNumber)
            }
        } else {
            openDialer(phoneNumber)
        }
    }

    /**
     * Opens the system dialer with the phone number pre-filled.
     */
    fun openDialer(numberOrName: String): ToolCallResult {
        return try {
            val cleanDigits = numberOrName.filter { it.isDigit() || it == '+' }
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                if (cleanDigits.isNotBlank()) {
                    data = Uri.parse("tel:${Uri.encode(cleanDigits)}")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            ToolCallResult("openDialer", numberOrName, true, "Opened phone dialer for $numberOrName, Boss. Tap Call to proceed.")
        } catch (e: Exception) {
            ToolCallResult("openDialer", numberOrName, false, "Could not open dialer: ${e.message}")
        }
    }

    /**
     * Answers an incoming phone call using Android TelecomManager if permitted.
     */
    fun answerCall(): ToolCallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            if (!hasAnswerPermission) {
                return ToolCallResult(
                    "answerCall",
                    "Telecom",
                    false,
                    "Boss, Android requires the Answer Phone Calls permission to accept incoming calls automatically."
                )
            }
            return try {
                telecomManager?.acceptRingingCall()
                ToolCallResult("answerCall", "Incoming Call", true, "Answered the call for you, Boss! 📞")
            } catch (e: Exception) {
                ToolCallResult("answerCall", "Incoming Call", false, "Could not answer call automatically: ${e.message}")
            }
        } else {
            return ToolCallResult("answerCall", "Incoming Call", false, "Answering calls programmatically is only supported on Android 8.0 and above, Boss.")
        }
    }

    /**
     * Ends the active phone call if supported by the system TelecomManager.
     */
    fun endCall(): ToolCallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasAnswerPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            if (!hasAnswerPermission) {
                return ToolCallResult("endCall", "Telecom", false, "Boss, Android requires Answer Phone Calls permission to end calls.")
            }
            return try {
                val ended = telecomManager?.endCall() ?: false
                if (ended) {
                    ToolCallResult("endCall", "Active Call", true, "Call ended, Boss. 🔴")
                } else {
                    ToolCallResult("endCall", "Active Call", false, "No active call found to end, Boss.")
                }
            } catch (e: Exception) {
                ToolCallResult("endCall", "Active Call", false, "Could not end call: ${e.message}")
            }
        } else {
            return ToolCallResult("endCall", "Active Call", false, "Ending calls programmatically requires Android 9.0+, Boss.")
        }
    }

    data class ContactLookupResult(
        val found: Boolean,
        val name: String,
        val number: String?,
        val permissionRequired: Boolean,
        val message: String
    )
}
