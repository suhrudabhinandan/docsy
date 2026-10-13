package com.suhrud.docsy.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class SmsRecord(
    val id: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val isIncoming: Boolean
)

data class CallRecord(
    val id: Long,
    val number: String,
    val contactName: String?,
    val date: Long,
    val durationSeconds: Long,
    val type: Int // CallLog.Calls.INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE
)

data class ContactRecord(
    val id: Long,
    val displayName: String,
    val phoneNumber: String
)

class TelephonyIngestor(private val context: Context) {

    fun hasSmsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCallLogPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Resolves phone number to Contact Name from device contacts provider.
     */
    fun resolveContactName(phoneNumber: String): String? {
        if (!hasContactsPermission() || phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ingests recent SMS messages with contact resolution.
     */
    fun readRecentSms(limit: Int = 500): List<SmsRecord> {
        if (!hasSmsPermission()) return emptyList()
        val records = mutableListOf<SmsRecord>()
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val address = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                    val contactName = resolveContactName(address)
                    records.add(
                        SmsRecord(
                            id = id,
                            address = address,
                            contactName = contactName,
                            body = body,
                            date = date,
                            isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful return on restriction
        }
        return records
    }

    /**
     * Ingests call logs with contact resolution.
     */
    fun readCallLogs(limit: Int = 200): List<CallRecord> {
        if (!hasCallLogPermission()) return emptyList()
        val records = mutableListOf<CallRecord>()
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE
            )
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit"
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE

                    val contactName = cachedName ?: resolveContactName(number)
                    records.add(
                        CallRecord(
                            id = id,
                            number = number,
                            contactName = contactName,
                            date = date,
                            durationSeconds = duration,
                            type = type
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Graceful return on restriction
        }
        return records
    }
}
