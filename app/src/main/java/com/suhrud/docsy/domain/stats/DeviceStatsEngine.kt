package com.suhrud.docsy.domain.stats

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.suhrud.docsy.data.local.DocumentDao
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.data.model.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceStatsEngine(
    private val context: Context,
    private val documentDao: DocumentDao
) {

    suspend fun getStorageStats(): ChatMessage = withContext(Dispatchers.IO) {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val usedGb = String.format(Locale.ROOT, "%.1f", usedBytes / (1024.0 * 1024.0 * 1024.0))
            val totalGb = String.format(Locale.ROOT, "%.1f", totalBytes / (1024.0 * 1024.0 * 1024.0))
            val freeGb = String.format(Locale.ROOT, "%.1f", freeBytes / (1024.0 * 1024.0 * 1024.0))

            ChatMessage(
                isUser = false,
                text = "$freeGb GB free out of $totalGb GB total ($usedGb GB used).",
                answerHighlight = "$freeGb GB free",
                supportingMetadata = "DEVICE STORAGE",
                subtext = "$usedGb GB used of $totalGb GB total"
            )
        } catch (e: Exception) {
            ChatMessage(
                isUser = false,
                text = "Could not read storage statistics on this device."
            )
        }
    }

    suspend fun getInstalledAppsStats(): ChatMessage = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            var userApps = 0
            var systemApps = 0

            for (pkg in packages) {
                if ((pkg.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    systemApps++
                } else {
                    userApps++
                }
            }

            val total = packages.size
            ChatMessage(
                isUser = false,
                text = "You have $total apps installed ($userApps user apps and $systemApps system apps).",
                answerHighlight = "$total apps",
                supportingMetadata = "INSTALLED APPLICATIONS",
                subtext = "$userApps user apps · $systemApps system apps"
            )
        } catch (e: Exception) {
            ChatMessage(
                isUser = false,
                text = "Could not list installed packages on this device."
            )
        }
    }

    suspend fun getSmsStats(): ChatMessage = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext ChatMessage(
                isUser = false,
                text = "I need SMS access to answer that. Grant it in Settings.",
                missingPermissionRequired = Manifest.permission.READ_SMS
            )
        }

        try {
            val inboxUri = Uri.parse("content://sms/inbox")
            val sentUri = Uri.parse("content://sms/sent")

            var inboxCount = 0
            var sentCount = 0

            context.contentResolver.query(inboxUri, arrayOf("_id"), null, null, null)?.use {
                inboxCount = it.count
            }
            context.contentResolver.query(sentUri, arrayOf("_id"), null, null, null)?.use {
                sentCount = it.count
            }

            val total = inboxCount + sentCount
            ChatMessage(
                isUser = false,
                text = "You have $total SMS messages ($inboxCount received in inbox, $sentCount sent).",
                answerHighlight = "$total SMS",
                supportingMetadata = "MESSAGES",
                subtext = "$inboxCount received · $sentCount sent"
            )
        } catch (e: Exception) {
            ChatMessage(
                isUser = false,
                text = "Could not read SMS messages on this device: ${e.message}"
            )
        }
    }

    suspend fun getCallLogStats(missedOnly: Boolean = false): ChatMessage = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext ChatMessage(
                isUser = false,
                text = "I need Call Log access to answer that. Grant it in Settings.",
                missingPermissionRequired = Manifest.permission.READ_CALL_LOG
            )
        }

        try {
            val uri = CallLog.Calls.CONTENT_URI
            var incoming = 0
            var outgoing = 0
            var missed = 0

            context.contentResolver.query(
                uri,
                arrayOf(CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val typeCol = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
                val todayStart = getStartOfDayMillis()

                while (cursor.moveToNext()) {
                    val type = if (typeCol != -1) cursor.getInt(typeCol) else -1
                    val date = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    if (missedOnly) {
                        if (type == CallLog.Calls.MISSED_TYPE && date >= todayStart) {
                            missed++
                        }
                    } else {
                        when (type) {
                            CallLog.Calls.INCOMING_TYPE -> incoming++
                            CallLog.Calls.OUTGOING_TYPE -> outgoing++
                            CallLog.Calls.MISSED_TYPE -> missed++
                        }
                    }
                }
            }

            if (missedOnly) {
                ChatMessage(
                    isUser = false,
                    text = "You have $missed missed call${if (missed == 1) "" else "s"} today.",
                    answerHighlight = "$missed missed",
                    supportingMetadata = "TODAY'S CALLS",
                    subtext = "Call Log"
                )
            } else {
                val total = incoming + outgoing + missed
                ChatMessage(
                    isUser = false,
                    text = "You have $total logged calls ($incoming incoming, $outgoing outgoing, $missed missed).",
                    answerHighlight = "$total calls",
                    supportingMetadata = "CALL LOG",
                    subtext = "$incoming in · $outgoing out · $missed missed"
                )
            }
        } catch (e: Exception) {
            ChatMessage(
                isUser = false,
                text = "Could not read call history: ${e.message}"
            )
        }
    }

    suspend fun getContactsStats(): ChatMessage = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext ChatMessage(
                isUser = false,
                text = "I need Contacts access to answer that. Grant it in Settings.",
                missingPermissionRequired = Manifest.permission.READ_CONTACTS
            )
        }

        try {
            var count = 0
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use {
                count = it.count
            }

            ChatMessage(
                isUser = false,
                text = "You have $count contacts saved.",
                answerHighlight = "$count contacts",
                supportingMetadata = "CONTACT BOOK",
                subtext = "Phone & Account Contacts"
            )
        } catch (e: Exception) {
            ChatMessage(
                isUser = false,
                text = "Could not read contacts on this device."
            )
        }
    }

    suspend fun getFileCountStats(filterType: String? = null): ChatMessage = withContext(Dispatchers.IO) {
        val count = when (filterType?.lowercase(Locale.ROOT)) {
            "photo", "photos", "image", "images" -> documentDao.getPhotoCount()
            "video", "videos" -> documentDao.getVideoCount()
            "audio", "song", "songs", "music" -> documentDao.getAudioCount()
            "pdf", "pdfs" -> documentDao.getPdfCount()
            "word", "doc", "docx" -> documentDao.getWordCount()
            "excel", "xls", "xlsx", "csv" -> documentDao.getExcelCount()
            "ppt", "pptx", "powerpoint" -> documentDao.getPowerPointCount()
            "txt", "text" -> documentDao.getTextCount()
            "document", "documents" -> documentDao.getOfficeDocCount()
            else -> documentDao.getDocumentCount()
        }

        val label = when (filterType?.lowercase(Locale.ROOT)) {
            "photo", "photos", "image", "images" -> "photos"
            "video", "videos" -> "videos"
            "audio", "song", "songs", "music" -> "songs"
            "pdf", "pdfs" -> "PDFs"
            "word", "doc", "docx" -> "Word documents"
            "excel", "xls", "xlsx", "csv" -> "Excel spreadsheets"
            "ppt", "pptx", "powerpoint" -> "PowerPoint presentations"
            "txt", "text" -> "text files"
            "document", "documents" -> "documents"
            else -> "files"
        }

        ChatMessage(
            isUser = false,
            text = "You have $count $label on your device.",
            answerHighlight = "$count $label",
            supportingMetadata = "STORAGE FILES",
            subtext = "Local Storage"
        )
    }

    suspend fun getVideoDurationStats(): ChatMessage = withContext(Dispatchers.IO) {
        val allDocs = documentDao.getAllDocumentsDirect()
        val videos = allDocs.filter { it.documentType == "Video" || it.mimeType.startsWith("video/") || it.extension in listOf("mp4", "mkv", "mov", "avi", "3gp") }
        if (videos.isEmpty()) {
            return@withContext ChatMessage(
                isUser = false,
                text = "No videos found on your device.",
                answerHighlight = "0 Videos",
                supportingMetadata = "VIDEOS · DURATION"
            )
        }

        var totalMs = 0L
        for (v in videos) {
            val dur = runCatching {
                org.json.JSONObject(v.structuredJson).optLong("durationMs", 0L)
            }.getOrDefault(0L)
            totalMs += dur
        }

        val totalSec = totalMs / 1000
        val hours = totalSec / 3600
        val mins = (totalSec % 3600) / 60
        val durFormatted = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        ChatMessage(
            isUser = false,
            text = "Total duration of all ${videos.size} videos is $durFormatted.",
            answerHighlight = durFormatted,
            supportingMetadata = "VIDEOS · ${videos.size} VIDEOS",
            subtext = "Based on indexed video metadata"
        )
    }

    suspend fun getSongsByArtist(artist: String): ChatMessage = withContext(Dispatchers.IO) {
        val allDocs = documentDao.getAllDocumentsDirect()
        val matchingSongs = allDocs.filter {
            (it.documentType == "Audio" || it.mimeType.startsWith("audio/")) &&
            (it.extractedText.contains(artist, ignoreCase = true) || it.fileName.contains(artist, ignoreCase = true))
        }

        if (matchingSongs.isEmpty()) {
            return@withContext ChatMessage(
                isUser = false,
                text = "I couldn't find any songs by $artist on your device.",
                answerHighlight = "No Songs Found",
                supportingMetadata = "MUSIC // $artist"
            )
        }

        val titles = matchingSongs.take(4).map { it.fileName.substringBeforeLast('.') }.joinToString(", ")
        val more = if (matchingSongs.size > 4) " and ${matchingSongs.size - 4} more" else ""
        ChatMessage(
            isUser = false,
            text = "Found ${matchingSongs.size} songs by $artist on your device: $titles$more.",
            answerHighlight = "${matchingSongs.size} Songs",
            supportingMetadata = "MUSIC · $artist",
            subtext = "Tracks: $titles"
        )
    }

    fun getSpeechTranscriptionLimitation(): ChatMessage {
        return ChatMessage(
            isUser = false,
            text = "Docsy reads media details (titles, artists, albums, durations, dates, file sizes, resolutions, and folders) on-device. Docsy does not transcribe speech or listen to audio recordings in order to keep the app 100% offline and under 100 MB.",
            answerHighlight = "Metadata Only",
            supportingMetadata = "MEDIA LIMITS",
            subtext = "Docsy reads details, not contents"
        )
    }

    suspend fun getSuperlativeFile(superlative: String, targetType: String? = null): ChatMessage = withContext(Dispatchers.IO) {
        val doc: DocumentEntity? = when (superlative.uppercase(Locale.ROOT)) {
            "LONGEST" -> {
                val allDocs = documentDao.getAllDocumentsDirect()
                val videos = allDocs.filter { it.documentType == "Video" || it.mimeType.startsWith("video/") || it.extension in listOf("mp4", "mkv", "mov", "avi") }
                videos.maxByOrNull {
                    runCatching { org.json.JSONObject(it.structuredJson).optLong("durationMs", 0L) }.getOrDefault(0L)
                } ?: documentDao.getLargestFile()
            }
            "BIGGEST", "LARGEST", "MAX_SIZE" -> {
                when (targetType?.lowercase(Locale.ROOT)) {
                    "video", "videos" -> documentDao.getLargestFileByPattern("video/%", "%.mp4")
                    "photo", "photos", "image", "images" -> documentDao.getLargestFileByPattern("image/%", "%.jpg")
                    "document", "pdf" -> documentDao.getLargestFileByPattern("application/pdf", "%.pdf")
                    "song", "songs", "audio", "music" -> documentDao.getLargestFileByPattern("audio/%", "%.mp3")
                    else -> documentDao.getLargestFile()
                }
            }
            "SMALLEST", "MIN_SIZE" -> {
                documentDao.getSmallestNonZeroFile()
            }
            "OLDEST" -> {
                when (targetType?.lowercase(Locale.ROOT)) {
                    "photo", "photos", "image" -> documentDao.getOldestFileByPattern("image/%", "%.jpg")
                    "video", "videos" -> documentDao.getOldestFileByPattern("video/%", "%.mp4")
                    "document", "pdf" -> documentDao.getOldestFileByPattern("application/pdf", "%.pdf")
                    "song", "songs", "audio" -> documentDao.getOldestFileByPattern("audio/%", "%.mp3")
                    else -> documentDao.getOldestFile()
                }
            }
            "NEWEST", "LATEST" -> {
                when (targetType?.lowercase(Locale.ROOT)) {
                    "photo", "photos", "image" -> documentDao.getNewestFileByPattern("image/%", "%.jpg")
                    "video", "videos" -> documentDao.getNewestFileByPattern("video/%", "%.mp4")
                    "document", "pdf" -> documentDao.getNewestFileByPattern("application/pdf", "%.pdf")
                    "song", "songs", "audio", "music" -> documentDao.getNewestFileByPattern("audio/%", "%.mp3")
                    else -> documentDao.getNewestFile()
                }
            }
            else -> documentDao.getLargestFile()
        }

        if (doc == null) {
            return@withContext ChatMessage(
                isUser = false,
                text = "No matching files found on this device."
            )
        }

        val sizeFormatted = formatFileSize(doc.fileSizeBytes)
        val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(doc.modifiedDate))

        val highlight = when (superlative.uppercase(Locale.ROOT)) {
            "BIGGEST", "LARGEST", "MAX_SIZE" -> sizeFormatted
            "SMALLEST", "MIN_SIZE" -> sizeFormatted
            "OLDEST", "NEWEST", "LATEST" -> dateFormatted
            else -> sizeFormatted
        }

        val subtext = "Modified $dateFormatted · $sizeFormatted"

        ChatMessage(
            isUser = false,
            text = "${doc.fileName} ($sizeFormatted, modified $dateFormatted). Located at: ${doc.pathUri}",
            answerHighlight = highlight,
            supportingMetadata = doc.fileName.uppercase(Locale.ROOT),
            subtext = subtext,
            sourceDocument = doc
        )
    }

    suspend fun getStepCountStats(): ChatMessage = withContext(Dispatchers.IO) {
        val hasActivityPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasActivityPermission) {
            return@withContext ChatMessage(
                isUser = false,
                text = "I need Activity Recognition permission to read step counts. Grant it in Settings.",
                missingPermissionRequired = Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepSensor == null) {
            return@withContext ChatMessage(
                isUser = false,
                text = "Step sensor hardware is not available on this device."
            )
        }

        ChatMessage(
            isUser = false,
            text = "On-device hardware step counter is active. Note: the hardware sensor only tracks steps since your device's last reboot and cannot provide past historical dates without Health Connect.",
            answerHighlight = "Active Sensor",
            supportingMetadata = "HARDWARE STEP COUNTER",
            subtext = "Tracks steps since last device reboot"
        )
    }

    private fun getStartOfDayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.ROOT, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.ROOT, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
