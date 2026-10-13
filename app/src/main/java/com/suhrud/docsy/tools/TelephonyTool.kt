package com.suhrud.docsy.tools

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import androidx.core.content.ContextCompat

data class SmsMessageItem(
    val address: String,
    val body: String,
    val date: Long,
    val isRead: Boolean
)

data class CallLogItem(
    val number: String,
    val cachedName: String?,
    val type: Int, // 1=INCOMING, 2=OUTGOING, 3=MISSED
    val date: Long,
    val durationSeconds: Long
)

data class TelephonyStats(
    val unreadSmsCount: Int,
    val totalSmsCount: Int,
    val missedCallsCount: Int,
    val totalCallsCount: Int
)

/**
 * SMS and Call Log inspection and search tool.
 * Satisfies Phase 8.7 in AUDIT.md.
 */
object TelephonyTool {

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallLogPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Counts unread SMS messages.
     */
    fun getUnreadSmsCount(context: Context): Int {
        if (!hasSmsPermission(context)) return 0
        val uri = Uri.parse("content://sms/inbox")
        val cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf("_id"),
                "read = 0",
                null,
                null
            )
        } catch (_: Exception) {
            null
        }
        return cursor?.use { it.count } ?: 0
    }

    /**
     * Searches SMS messages by keyword in body or sender address.
     */
    fun searchSms(context: Context, query: String, limit: Int = 5): List<SmsMessageItem> {
        if (!hasSmsPermission(context)) return emptyList()

        val results = mutableListOf<SmsMessageItem>()
        val uri = Uri.parse("content://sms/")
        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri,
                arrayOf("address", "body", "date", "read"),
                "body LIKE ? OR address LIKE ?",
                arrayOf("%$query%", "%$query%"),
                "date DESC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val addrIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            val readIdx = it.getColumnIndex("read")

            while (it.moveToNext() && results.size < limit) {
                val address = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                val read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true

                results.add(SmsMessageItem(address, body, date, read))
            }
        }
        return results
    }

    /**
     * Counts missed calls.
     */
    fun getMissedCallsCount(context: Context): Int {
        if (!hasCallLogPermission(context)) return 0
        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                null
            )
        } catch (_: Exception) {
            null
        }
        return cursor?.use { it.count } ?: 0
    }

    /**
     * Retrieves recent call records.
     */
    fun getRecentCalls(context: Context, limit: Int = 5): List<CallLogItem> {
        if (!hasCallLogPermission(context)) return emptyList()

        val results = mutableListOf<CallLogItem>()
        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
            val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext() && results.size < limit) {
                val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                val type = if (typeIdx >= 0) it.getInt(typeIdx) else 0
                val date = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L

                results.add(CallLogItem(number, name, type, date, duration))
            }
        }
        return results
    }
}
