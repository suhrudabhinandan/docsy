package com.suhrud.docsy.domain.retrieval

import android.content.Context
import android.util.Log
import com.suhrud.docsy.data.local.DocumentDao
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.data.model.DocumentCategories
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.domain.ai.DocumentClassifier
import com.suhrud.docsy.domain.ai.llm.ResponseComposer
import com.suhrud.docsy.domain.datetime.DateTimeEngine
import com.suhrud.docsy.domain.health.StepManager
import com.suhrud.docsy.domain.query.AggregationOp
import com.suhrud.docsy.domain.query.ParsedQuery
import com.suhrud.docsy.domain.query.QueryIntent
import com.suhrud.docsy.domain.stats.DeviceInfoEngine
import com.suhrud.docsy.domain.stats.DeviceStatsEngine
import com.suhrud.docsy.domain.stats.FolderStatsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class StrictRelevanceEngine(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val deviceStatsEngine: DeviceStatsEngine
) {

    private val TAG = "DocsyAnswerGate"
    private val folderStatsEngine = FolderStatsEngine(documentDao)

    suspend fun answerQuery(
        parsed: ParsedQuery,
        userName: String
    ): ChatMessage = withContext(Dispatchers.IO) {
        when (parsed.intent) {
            QueryIntent.SMALL_TALK -> {
                ResponseComposer.composeSmallTalk(parsed.rawQuery, userName)
            }

            QueryIntent.OUT_OF_SCOPE -> {
                ResponseComposer.composeOutOfScope()
            }

            QueryIntent.DEVICE_INFO -> {
                DeviceInfoEngine.answerDeviceInfo(context, parsed.rawQuery)
            }

            QueryIntent.APP_QUERY -> {
                deviceStatsEngine.getInstalledAppsStats()
            }

            QueryIntent.STORAGE_QUERY -> {
                deviceStatsEngine.getStorageStats()
            }

            QueryIntent.DATE_TIME -> {
                DateTimeEngine.answerDateTime(context, parsed)
            }

            QueryIntent.FOLDER_STATS -> {
                folderStatsEngine.answerFolderQuery(parsed)
            }

            QueryIntent.PERSONAL_PROFILE -> {
                val name = userName.ifBlank { "there" }
                ChatMessage(
                    isUser = false,
                    text = "Your name is $name.",
                    answerHighlight = name,
                    supportingMetadata = "USER PROFILE"
                )
            }

            QueryIntent.DEVICE_STATS -> {
                if (parsed.rawQuery.lowercase(Locale.ROOT).contains("battery") ||
                    parsed.rawQuery.lowercase(Locale.ROOT).contains("ram") ||
                    parsed.rawQuery.lowercase(Locale.ROOT).contains("kernel") ||
                    parsed.rawQuery.lowercase(Locale.ROOT).contains("android") ||
                    parsed.rawQuery.lowercase(Locale.ROOT).contains("specs") ||
                    parsed.rawQuery.lowercase(Locale.ROOT).contains("phone")
                ) {
                    DeviceInfoEngine.answerDeviceInfo(context, parsed.rawQuery)
                } else {
                    when (parsed.filterType) {
                        "storage" -> deviceStatsEngine.getStorageStats()
                        "app" -> deviceStatsEngine.getInstalledAppsStats()
                        "contacts" -> deviceStatsEngine.getContactsStats()
                        "photo", "video", "audio", "document", "all" -> deviceStatsEngine.getFileCountStats(parsed.filterType)
                        else -> deviceStatsEngine.getStorageStats()
                    }
                }
            }

            QueryIntent.FILE_SUPERLATIVE -> {
                deviceStatsEngine.getSuperlativeFile(
                    superlative = parsed.filterType ?: "BIGGEST",
                    targetType = parsed.requestedField
                )
            }

            QueryIntent.SMS_QUERY -> {
                deviceStatsEngine.getSmsStats()
            }

            QueryIntent.CALL_QUERY -> {
                deviceStatsEngine.getCallLogStats(missedOnly = parsed.filterType == "missed_calls")
            }

            QueryIntent.STEPS_QUERY -> {
                val stepManager = StepManager(context)
                stepManager.getStepStats(parsed.filterType ?: "today")
            }

            QueryIntent.FIND_PERSONAL_INFO -> {
                answerPersonalInfo(parsed)
            }

            QueryIntent.FIND_SCHEDULE -> {
                answerSchedule(parsed)
            }

            QueryIntent.COMPARE_DOCUMENT_VALUES -> {
                answerAggregation(parsed)
            }

            QueryIntent.FIND_DOCUMENT_INFO -> {
                answerDocumentQuery(parsed)
            }

            QueryIntent.UNKNOWN -> {
                if (!parsed.hasConstraints) {
                    Log.d(TAG, "[Answer Gate REJECT] UNKNOWN query without constraints: ${parsed.rawQuery}")
                    ResponseComposer.composeSmallTalk(parsed.rawQuery, userName)
                } else {
                    answerDocumentQuery(parsed)
                }
            }
        }
    }

    private suspend fun answerPersonalInfo(parsed: ParsedQuery): ChatMessage {
        val targetType = parsed.documentType ?: DocumentCategories.AADHAAR
        val docs = documentDao.getDocumentsByType(targetType)

        if (docs.isEmpty()) {
            val label = if (targetType == DocumentCategories.AADHAAR) "Aadhaar card" else "PAN card"
            return ResponseComposer.composeNotFound(label)
        }

        val bestDoc = docs.first()
        val fields = DocumentClassifier.classifyAndExtract(bestDoc.fileName, bestDoc.extractedText)

        return if (targetType == DocumentCategories.AADHAAR) {
            val aadhaarFull = fields.aadhaarNumberFull
            val aadhaarMasked = fields.aadhaarNumberMasked ?: aadhaarFull?.let { "XXXX-XXXX-${it.takeLast(4)}" }
            if (aadhaarMasked != null) {
                ChatMessage(
                    isUser = false,
                    text = "Your Aadhaar number is $aadhaarMasked.",
                    answerHighlight = aadhaarMasked,
                    supportingMetadata = "AADHAAR CARD · UIDAI",
                    subtext = "Tap to reveal unmasked number",
                    sourceDocument = bestDoc,
                    isSensitive = true,
                    unmaskedValue = aadhaarFull,
                    isRevealed = false
                )
            } else {
                ResponseComposer.composeNotFound("Aadhaar card")
            }
        } else {
            val pan = fields.panNumber
            if (pan != null) {
                ChatMessage(
                    isUser = false,
                    text = "Your PAN is $pan.",
                    answerHighlight = pan,
                    supportingMetadata = "PERMANENT ACCOUNT NUMBER",
                    subtext = "Income Tax Department",
                    sourceDocument = bestDoc
                )
            } else {
                ResponseComposer.composeNotFound("PAN card")
            }
        }
    }

    private suspend fun answerSchedule(parsed: ParsedQuery): ChatMessage {
        val docs = documentDao.getDocumentsByType(DocumentCategories.SCHEDULE)
        if (docs.isEmpty()) {
            return ResponseComposer.composeNotFound("exam schedule")
        }

        val bestDoc = docs.first()
        val fields = DocumentClassifier.classifyAndExtract(bestDoc.fileName, bestDoc.extractedText)
        val scheduleList = fields.exams ?: emptyList()

        return if (scheduleList.isNotEmpty()) {
            ChatMessage(
                isUser = false,
                text = "Here is your examination timetable from ${bestDoc.fileName}.",
                answerHighlight = "Exam Schedule",
                supportingMetadata = bestDoc.fileName.uppercase(Locale.ROOT),
                subtext = "${scheduleList.size} exams scheduled",
                sourceDocument = bestDoc,
                scheduleItems = scheduleList
            )
        } else {
            ResponseComposer.composeNotFound("exam schedule")
        }
    }

    private suspend fun answerAggregation(parsed: ParsedQuery): ChatMessage {
        val docType = parsed.documentType ?: DocumentCategories.ELECTRICITY_BILL
        val docs = documentDao.getDocumentsByType(docType)

        if (docs.isEmpty()) {
            return ResponseComposer.composeNotFound("$docType documents")
        }

        val amountsWithDocs = mutableListOf<Pair<Double, DocumentEntity>>()
        for (doc in docs) {
            val fields = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)
            val amt = fields.amountPayable
            if (amt != null && amt > 0) {
                amountsWithDocs.add(amt to doc)
            }
        }

        if (amountsWithDocs.isEmpty()) {
            return ResponseComposer.composeNotFound("amounts in $docType documents")
        }

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

        return when (parsed.operation) {
            AggregationOp.MAX -> {
                val maxPair = amountsWithDocs.maxByOrNull { it.first }!!
                val formatted = currencyFormatter.format(maxPair.first)
                ChatMessage(
                    isUser = false,
                    text = "Your highest $docType was $formatted from ${maxPair.second.fileName}.",
                    answerHighlight = formatted,
                    supportingMetadata = "HIGHEST ${docType.uppercase(Locale.ROOT)}",
                    subtext = maxPair.second.fileName,
                    sourceDocument = maxPair.second,
                    contributingSources = amountsWithDocs.map { it.second }
                )
            }
            AggregationOp.MIN -> {
                val minPair = amountsWithDocs.minByOrNull { it.first }!!
                val formatted = currencyFormatter.format(minPair.first)
                ChatMessage(
                    isUser = false,
                    text = "Your lowest $docType was $formatted from ${minPair.second.fileName}.",
                    answerHighlight = formatted,
                    supportingMetadata = "LOWEST ${docType.uppercase(Locale.ROOT)}",
                    subtext = minPair.second.fileName,
                    sourceDocument = minPair.second,
                    contributingSources = amountsWithDocs.map { it.second }
                )
            }
            AggregationOp.SUM -> {
                val total = amountsWithDocs.sumOf { it.first }
                val formatted = currencyFormatter.format(total)
                ChatMessage(
                    isUser = false,
                    text = "The total amount across ${amountsWithDocs.size} $docType documents is $formatted.",
                    answerHighlight = formatted,
                    supportingMetadata = "TOTAL ${docType.uppercase(Locale.ROOT)}",
                    subtext = "Sum across ${amountsWithDocs.size} bills",
                    contributingSources = amountsWithDocs.map { it.second }
                )
            }
            else -> {
                ResponseComposer.composeNotFound("requested values")
            }
        }
    }

    private suspend fun answerDocumentQuery(parsed: ParsedQuery): ChatMessage {
        // HARD GUARD: Zero extracted constraints = zero file results
        if (!parsed.hasConstraints && parsed.namedDocument == null) {
            Log.d(TAG, "[Answer Gate REJECT] Query has zero extracted constraints: ${parsed.rawQuery}")
            return ResponseComposer.composeNotFound("that document")
        }

        val docType = parsed.documentType
        val utility = parsed.utilityOrIssuer
        val month = parsed.month
        val year = parsed.year
        val standard = parsed.standardOrClass
        val board = parsed.board
        val namedDoc = parsed.namedDocument

        val candidates: List<DocumentEntity> = when {
            docType != null -> documentDao.getDocumentsByType(docType)
            utility != null -> documentDao.getDocumentsByUtility(utility)
            else -> documentDao.searchKeyword(parsed.rawQuery)
        }

        if (candidates.isEmpty()) {
            return ResponseComposer.composeNotFound(namedDoc ?: docType ?: utility ?: "that document")
        }

        // Open-ended named document identity matching
        if (namedDoc != null) {
            val requiredWords = namedDoc.lowercase(Locale.ROOT).split(" ").filter { it.length > 2 }
            val matchingNamedDocs = candidates.filter { doc ->
                val fullText = "${doc.fileName} ${doc.extractedText}".lowercase(Locale.ROOT)
                requiredWords.all { word -> fullText.contains(word) }
            }

            if (matchingNamedDocs.isEmpty()) {
                Log.d(TAG, "[Answer Gate REJECT] No candidates matched named document identity: $namedDoc")
                return ResponseComposer.composeNotFound(namedDoc)
            }

            val doc = matchingNamedDocs.first()
            val fields = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)

            return ChatMessage(
                isUser = false,
                text = "Found your $namedDoc: ${doc.fileName}.",
                answerHighlight = doc.fileName,
                supportingMetadata = "DOCUMENT MATCH",
                subtext = doc.fileName,
                sourceDocument = doc
            )
        }

        if (docType == DocumentCategories.MARKSHEET) {
            val validMarksheets = candidates.filter { doc ->
                val f = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)
                val matchesClass = standard == null || f.classOrStandard.equals(standard, ignoreCase = true)
                val matchesBoard = board == null || f.boardName.equals(board, ignoreCase = true)
                matchesClass && matchesBoard
            }

            if (validMarksheets.isEmpty()) {
                val classLabel = standard ?: ""
                val boardLabel = board ?: ""
                Log.d(TAG, "[Answer Gate REJECT] No marksheets matched $classLabel $boardLabel")
                return ResponseComposer.composeNotFound("$classLabel $boardLabel marksheet".trim())
            }

            val doc = validMarksheets.first()
            val f = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)
            val score = f.percentageOrCgpa ?: f.rollNumber?.let { "Roll No $it" } ?: "Passed"
            return ChatMessage(
                isUser = false,
                text = "Found your ${f.classOrStandard ?: standard} ${f.boardName ?: board} Marksheet: $score.",
                answerHighlight = score,
                supportingMetadata = "${f.boardName ?: "BOARD"} · ${f.classOrStandard ?: "CLASS"}",
                subtext = doc.fileName,
                sourceDocument = doc
            )
        }

        val matchingCandidates = candidates.filter { doc ->
            // Reject images and general documents if OCR text is empty
            if (doc.extractedText.isBlank() && (doc.mimeType.startsWith("image/") || doc.documentType == DocumentCategories.GENERAL)) {
                return@filter false
            }

            val f = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)

            val matchesUtility = utility == null || f.utilityType.equals(utility, ignoreCase = true) ||
                    doc.detectedUtility.equals(utility, ignoreCase = true)

            val matchesMonth = month == null || f.billingMonth.equals(month, ignoreCase = true) ||
                    doc.detectedMonth.equals(month, ignoreCase = true)

            val matchesYear = year == null || f.billingYear == year || doc.detectedYear == year

            matchesUtility && matchesMonth && matchesYear
        }

        if (matchingCandidates.isNotEmpty()) {
            val doc = matchingCandidates.first()
            val f = DocumentClassifier.classifyAndExtract(doc.fileName, doc.extractedText)

            val requested = parsed.requestedField
            if (requested != null) {
                val fieldValue = when (requested) {
                    "amount" -> f.amountPayable?.let { NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(it) }
                    "due_date" -> f.dueDate
                    "roll_number" -> f.rollNumber
                    "score" -> f.percentageOrCgpa
                    "number" -> f.consumerNumber ?: f.aadhaarNumberMasked ?: f.panNumber
                    else -> null
                }

                if (fieldValue != null) {
                    return ChatMessage(
                        isUser = false,
                        text = "Found $requested in ${doc.fileName}: $fieldValue.",
                        answerHighlight = fieldValue,
                        supportingMetadata = doc.documentType.uppercase(Locale.ROOT),
                        subtext = doc.fileName,
                        sourceDocument = doc
                    )
                } else {
                    return ResponseComposer.composeNotFound("$requested in ${doc.fileName}")
                }
            } else {
                return ChatMessage(
                    isUser = false,
                    text = "Found your ${doc.documentType} document: ${doc.fileName}.",
                    answerHighlight = doc.fileName,
                    supportingMetadata = doc.documentType.uppercase(Locale.ROOT),
                    subtext = doc.fileName,
                    sourceDocument = doc
                )
            }
        }

        Log.d(TAG, "[Answer Gate REJECT] No candidate matched criteria")
        return ResponseComposer.composeNotFound(namedDoc ?: docType ?: utility ?: "that document")
    }
}
