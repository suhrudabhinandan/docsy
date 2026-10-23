package com.suhrud.docsy.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class DiscoveredFile(
    val file: File,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)

data class ScanProgress(
    val isScanning: Boolean = false,
    val totalFilesWalked: Int = 0,
    val documentsDiscovered: Int = 0,
    val currentDirectory: String = "",
    val error: String? = null
)

private const val TIMED_OUT_REASON = "TIMED_OUT"
private const val RETRY_AFTER_MS = 6L * 60 * 60 * 1000

open class FileScanner(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val docsyPackageDir = try {
        context.filesDir?.parentFile?.canonicalPath
    } catch (_: Exception) {
        null
    }

    open fun getRootStorageVolumes(): List<File> {
        val roots = LinkedHashSet<File>()

        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists() && primary.canRead()) {
            roots.add(primary)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { volume ->
                try {
                    val getDirMethod = volume.javaClass.getMethod("getDirectory")
                    val dir = getDirMethod.invoke(volume) as? File
                    if (dir != null && dir.exists() && dir.canRead()) {
                        roots.add(dir)
                    }
                } catch (_: Exception) {
                }
            }
        }

        val extDirs = context.getExternalFilesDirs(null)
        extDirs?.filterNotNull()?.forEach { appSpecificDir ->
            val path = appSpecificDir.absolutePath
            val androidIndex = path.indexOf("/Android")
            if (androidIndex > 0) {
                val rootDir = File(path.substring(0, androidIndex))
                if (rootDir.exists() && rootDir.canRead()) {
                    roots.add(rootDir)
                }
            }
        }

        return roots.toList()
    }

    suspend fun scanStorage(forceReindex: Boolean = false): ScanProgress = withContext(dispatcher) {
        com.suhrud.docsy.extractor.VlmEngine.resetBudget()
        _scanProgress.update {
            it.copy(
                isScanning = true,
                totalFilesWalked = 0,
                documentsDiscovered = 0,
                currentDirectory = "Preparing storage roots...",
                error = null
            )
        }
        ReadingStateRepository.startReading(totalFiles = 0)

        val roots = getRootStorageVolumes()
        val channel = Channel<DiscoveredFile>(Channel.UNLIMITED)
        // Cores-based, and reduced automatically when the phone is already hot.
        val workerCount = ScanCoordinator.ocrParallelism(context)

        val totalWalkedCounter = AtomicInteger(0)
        val discoveredCounter = AtomicInteger(0)
        val visitedDirs = ConcurrentHashMap.newKeySet<String>()
        val firstException = java.util.concurrent.atomic.AtomicReference<String?>(null)

        val sharedPipeline = com.suhrud.docsy.extractor.ExtractionPipeline()

        kotlinx.coroutines.supervisorScope {
            val diagJob = launch {
                val isExternalStorageManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else true

                while (true) {
                    val walked = totalWalkedCounter.get()
                    val disc = discoveredCounter.get()
                    val dirs = visitedDirs.size
                    val rootsStr = roots.joinToString { it.absolutePath }
                    val exStr = firstException.get() ?: "None"

                    val logLine = "DIAGNOSTIC: discoveredTotal=$walked, doneCount=$disc, visitedDirs=$dirs, entriesSeen=$walked, " +
                            "roots=[$rootsStr], isExternalStorageManager=$isExternalStorageManager, firstException=$exStr"

                    android.util.Log.d("FileScannerDiag", logLine)
                    com.suhrud.docsy.util.CrashReporter.appendDebugLog(context, logLine)

                    kotlinx.coroutines.delay(5000L)
                }
            }

            // Consumer workers
            val workerJobs = (1..workerCount).map {
                launch(dispatcher) {
                    val db = com.suhrud.docsy.data.db.DocsyDatabase.getInstance(context)
                    val factDao = db.factDao()
                    val chunkDao = db.chunkDao()
                    val docJsonDao = db.docJsonDao()
                    val docLayoutDao = db.docLayoutDao()
                    val lineDao = db.lineDao()
                    val fieldDao = db.fieldDao()
                    val subjectDao = db.subjectDao()
                    val docBlockDao = db.docBlockDao()
                    val docTableDao = db.docTableDao()
                    val marksheetDao = db.marksheetDao()

                    for (discovered in channel) {

                        try {
                            ScanCoordinator.throttleIfHot(context)
                            val contentKey = FileFilter.computeContentKey(
                                discovered.path,
                                discovered.sizeBytes,
                                discovered.lastModified
                            )

                            val existingDoc = if (!forceReindex) {
                                documentDao.getDocumentByContentKey(contentKey)
                            } else null

                            val extension = FileFilter.getExtension(discovered.file.name)

                            // A file that timed out is retried later instead of being lost forever (at most every 6 hours).
                            val retryTimedOut = existingDoc != null &&
                                existingDoc.state == DocumentStatus.FAILED.name &&
                                existingDoc.failureReason == TIMED_OUT_REASON &&
                                System.currentTimeMillis() - existingDoc.extractedAt > RETRY_AFTER_MS

                            if (existingDoc == null || retryTimedOut) {
                                val isImage = ContentSectionDetector.detectSection(discovered.file) == FileSection.IMAGES
                                val timeoutMs = when {
                                    discovered.file.extension.equals("pdf", true) -> 20 * 60 * 1000L
                                    isImage -> 60_000L // OCR on a busy or warm phone can need well over 10 s
                                    else -> 30_000L
                                }
                                val baseExtractionResult = try {
                                    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                                        sharedPipeline.extractFile(discovered.file)
                                    }
                                } catch (t: Throwable) {
                                    firstException.compareAndSet(null, "${t.javaClass.simpleName}: ${t.message}")
                                    null
                                }

                                // Rescue: if normal reading left a marksheet without subjects (or a photo without text),
                                // the on-device vision model re-reads it. Only runs when a model file is installed.
                                val extractionResult = try {
                                    com.suhrud.docsy.extractor.VlmRescue.maybeRescue(context, discovered.file, baseExtractionResult)
                                } catch (ce: kotlinx.coroutines.CancellationException) {
                                    throw ce
                                } catch (_: Throwable) {
                                    baseExtractionResult
                                }

                                val section = ContentSectionDetector.detectSection(discovered.file)
                                val extractedText = extractionResult?.fullText?.take(512 * 1024)
                                val entity = DocumentEntity(
                                    path = discovered.path,
                                    name = FileFilter.sanitizeDisplayName(discovered.file.name),
                                    ext = extension,
                                    mime = FileFilter.getMimeType(extension),
                                    size = discovered.sizeBytes,
                                    modified = discovered.lastModified,
                                    contentHash = contentKey,
                                    extractedAt = System.currentTimeMillis(),
                                    section = section.id,
                                    state = if (extractionResult == null) DocumentStatus.FAILED.name else DocumentStatus.INDEXED.name,
                                    extractedText = extractedText?.take(2048),
                                    extractionMethod = when {
                                        extractionResult?.usedVlm == true -> "VLM"
                                        extractionResult?.isFallbackOcrUsed == true -> "OCR"
                                        else -> "Native"
                                    },
                                    failureReason = if (extractionResult == null) TIMED_OUT_REASON else null
                                )

                                db.withTransaction {
                                    val docId = documentDao.insertDocument(entity)

                                    // One transaction per document; re-extraction replaces all rows of that document
                                    lineDao.deleteLinesForDocument(docId)
                                    fieldDao.deleteFieldsForDocument(docId)
                                    subjectDao.deleteSubjectsForDocument(docId)
                                    docBlockDao.deleteBlocksForDocument(docId)
                                    docTableDao.deleteTablesForDocument(docId)
                                    marksheetDao.deleteMarksheetForDocument(docId)

                                    if (extractionResult != null && extractionResult.layoutPages.isNotEmpty()) {
                                        for (layoutPage in extractionResult.layoutPages) {
                                            if (layoutPage.blocks.isNotEmpty()) {
                                                val blockEntities = layoutPage.blocks.map { b ->
                                                    com.suhrud.docsy.data.db.entity.DocBlockEntity(
                                                        docId = docId,
                                                        page = b.page,
                                                        order = b.order,
                                                        type = b.type,
                                                        text = b.text,
                                                        x = b.x,
                                                        y = b.y,
                                                        w = b.w,
                                                        h = b.h,
                                                        conf = b.confidence
                                                    )
                                                }
                                                docBlockDao.insertBlocks(blockEntities)
                                            }

                                            if (layoutPage.tables.isNotEmpty()) {
                                                val tableEntities = layoutPage.tables.map { t ->
                                                    com.suhrud.docsy.data.db.entity.DocTableEntity(
                                                        docId = docId,
                                                        page = t.page,
                                                        tableId = t.tableId,
                                                        row = t.row,
                                                        col = t.col,
                                                        text = t.text,
                                                        conf = t.confidence
                                                    )
                                                }
                                                docTableDao.insertTables(tableEntities)
                                            }
                                        }
                                    }

                                    if (!extractedText.isNullOrBlank()) {
                                        // 1. Rebuild text layout and save in doc_layout
                                        val pageMarker = Regex("""^\[\[PAGE (\d+)\]\]$""")
                                        val pagedClean = com.suhrud.docsy.reconstruction.TextReconstructionEngine.cleanAndDehyphenateText(extractedText)
                                        val cleanText = pagedClean.lines().filterNot { pageMarker.matches(it.trim()) }.joinToString("\n")
                                        docLayoutDao.insertDocLayout(
                                            com.suhrud.docsy.data.db.entity.DocLayoutEntity(
                                                documentId = docId,
                                                page = 1,
                                                blob = cleanText.toByteArray(Charsets.UTF_8)
                                            )
                                        )

                                        // 2. Classify document
                                        val classification = com.suhrud.docsy.understanding.DocumentClassifier.classify(cleanText, discovered.file.name)
                                        val docType = classification.category
                                        val docTypeScore = classification.confidence

                                        // 3. Extract Owner
                                        val ownerResult = com.suhrud.docsy.understanding.OwnerExtractor.extractOwner(cleanText, docType)
                                        val ownerName = ownerResult?.ownerName
                                        val ownerEvidence = ownerResult?.sourceLine

                                        // Update document with docType and owner
                                        documentDao.updateDocument(
                                            entity.copy(
                                                id = docId,
                                                docType = docType,
                                                docTypeScore = docTypeScore,
                                                ownerName = ownerName,
                                                ownerEvidence = ownerEvidence,
                                                extractedText = cleanText.take(2048)
                                            )
                                        )

                                        // 4. Save Lines (one row per OCR/text line, lineNo in reading order) & Chunks
                                        val chunksToInsert = mutableListOf<com.suhrud.docsy.data.db.entity.ChunkEntity>()
                                        val linesToInsert = mutableListOf<com.suhrud.docsy.data.db.entity.LineEntity>()
                                        var currentPage = 1
                                        val pagedLines = ArrayList<Pair<Int, String>>()
                                        for (raw in pagedClean.lines()) {
                                            val m = pageMarker.matchEntire(raw.trim())
                                            if (m != null) { currentPage = m.groupValues[1].toInt(); continue }
                                            if (raw.isNotBlank()) pagedLines.add(currentPage to raw)
                                        }
                                        val textLines = pagedLines.map { it.second }

                                        var globalLineCounter = 1
                                        for ((idx, line) in textLines.withIndex()) {
                                            val lineChunks = line.chunked(4096)
                                            for (subLine in lineChunks) {
                                                val safeSubLine = subLine.take(512 * 1024)
                                                val kind = when {
                                                    safeSubLine.contains(":") -> "KEYVALUE"
                                                    safeSubLine.lowercase().contains("subject") || safeSubLine.contains("|") -> "TABLE_ROW"
                                                    safeSubLine == safeSubLine.uppercase() && safeSubLine.length in 3..40 -> "HEADER"
                                                    else -> "PARAGRAPH"
                                                }
                                                chunksToInsert.add(
                                                    com.suhrud.docsy.data.db.entity.ChunkEntity(
                                                        documentId = docId,
                                                        page = pagedLines[idx].first,
                                                        position = idx,
                                                        kind = kind,
                                                        text = safeSubLine
                                                    )
                                                )
                                                linesToInsert.add(
                                                    com.suhrud.docsy.data.db.entity.LineEntity(
                                                        documentId = docId,
                                                        page = pagedLines[idx].first,
                                                        lineNo = globalLineCounter++,
                                                        yTop = idx * 20f,
                                                        xLeft = 0f,
                                                        text = safeSubLine
                                                    )
                                                )
                                            }
                                        }

                                        if (chunksToInsert.isNotEmpty()) {
                                            chunkDao.insertChunks(chunksToInsert)
                                        }
                                        if (linesToInsert.isNotEmpty()) {
                                            lineDao.insertLines(linesToInsert)
                                        }

                                        // 5. Extract typed facts, fields, and marksheets subjects
                                        val factList = mutableListOf<com.suhrud.docsy.data.db.entity.FactEntity>()
                                        val fieldList = mutableListOf<com.suhrud.docsy.data.db.entity.FieldEntity>()
                                        val subjectList = mutableListOf<com.suhrud.docsy.data.db.entity.SubjectEntity>()

                                        val useCases = com.suhrud.docsy.understanding.DocumentClassifier.getUseCasesForCategory(docType)
                                        if (useCases.isNotEmpty()) {
                                            val useCaseStr = useCases.joinToString(", ")
                                            factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "use_cases", value = useCaseStr))
                                            fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "use_cases", label = "Document Use Cases", value = useCaseStr))
                                        }

                                        val jsonBuilder = StringBuilder()
                                        jsonBuilder.append("{")
                                        jsonBuilder.append("\"docType\":\"").append(docType).append("\"")
                                        if (useCases.isNotEmpty()) {
                                            jsonBuilder.append(",\"useCases\":[\"").append(useCases.joinToString("\",\"")).append("\"]")
                                        }

                                        val lowerText = cleanText.lowercase()
                                        val isAcademicOrMarksheet = docType.contains("marksheet", ignoreCase = true) ||
                                                docType.contains("certificate", ignoreCase = true) ||
                                                docType.contains("degree", ignoreCase = true) ||
                                                lowerText.contains("statement of marks") ||
                                                lowerText.contains("mark sheet") ||
                                                lowerText.contains("marksheet") ||
                                                lowerText.contains("secondary school") ||
                                                lowerText.contains("senior secondary") ||
                                                lowerText.contains("class 10") || lowerText.contains("class 12") ||
                                                lowerText.contains("class x") || lowerText.contains("class xii") ||
                                                lowerText.contains("10th") || lowerText.contains("12th") ||
                                                lowerText.contains("examination certificate") ||
                                                lowerText.contains("pass certificate")

                                        if (isAcademicOrMarksheet) {
                                            val parsed = com.suhrud.docsy.understanding.MarksheetParser.parse(cleanText)
                                            if (parsed.gradeClass != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "class", value = parsed.gradeClass))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "class", value = parsed.gradeClass))
                                                jsonBuilder.append(",\"class\":\"").append(parsed.gradeClass).append("\"")
                                            }
                                            if (parsed.board != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "board", value = parsed.board))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "board", value = parsed.board))
                                                jsonBuilder.append(",\"board\":\"").append(parsed.board).append("\"")
                                            }
                                            if (parsed.studentName != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "studentName", value = parsed.studentName))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "studentName", value = parsed.studentName))
                                                jsonBuilder.append(",\"studentName\":\"").append(parsed.studentName).append("\"")
                                            }
                                            if (parsed.rollNumber != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "rollNo", value = parsed.rollNumber))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "rollNo", value = parsed.rollNumber))
                                                jsonBuilder.append(",\"rollNo\":\"").append(parsed.rollNumber).append("\"")
                                            }
                                            if (parsed.printedPercentage != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "printedPercentage", value = "${parsed.printedPercentage}%"))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "printedPercentage", value = "${parsed.printedPercentage}%"))
                                                jsonBuilder.append(",\"printedPercentage\":").append(parsed.printedPercentage)
                                            }
                                            if (parsed.resultStatus != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "result", value = parsed.resultStatus))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "result", value = parsed.resultStatus))
                                                jsonBuilder.append(",\"result\":\"").append(parsed.resultStatus).append("\"")
                                            }
                                            if (parsed.subjects.isNotEmpty()) {
                                                jsonBuilder.append(",\"subjects\":[")
                                                for ((sIdx, s) in parsed.subjects.withIndex()) {
                                                    if (sIdx > 0) jsonBuilder.append(",")
                                                    jsonBuilder.append("{\"name\":\"").append(s.subjectName)
                                                        .append("\",\"marks\":").append(s.marksObtained)
                                                        .append(",\"max\":").append(s.maxMarks).append("}")
                                                    factList.add(
                                                        com.suhrud.docsy.data.db.entity.FactEntity(
                                                            documentId = docId,
                                                            key = "subject_${s.subjectName.lowercase().replace(" ", "_")}",
                                                            value = "${s.marksObtained}/${s.maxMarks}"
                                                        )
                                                    )
                                                    fieldList.add(
                                                        com.suhrud.docsy.data.db.entity.FieldEntity(
                                                            documentId = docId,
                                                            key = "subject_${s.subjectName.lowercase().replace(" ", "_")}",
                                                            value = "${s.marksObtained}/${s.maxMarks}"
                                                        )
                                                    )
                                                    subjectList.add(
                                                        com.suhrud.docsy.data.db.entity.SubjectEntity(
                                                            documentId = docId,
                                                            name = s.subjectName,
                                                            marks = s.marksObtained.toString(),
                                                            maxMarks = s.maxMarks.toString()
                                                        )
                                                    )
                                                }
                                                jsonBuilder.append("]")
                                            }

                                            // Layout-aware pass: table columns and label -> value pairs.
                                            // Fills what the text parser missed (roll number, SGPA/CGPA, result, subjects).
                                            val layoutSheet = com.suhrud.docsy.understanding.LayoutMarksheetExtractor.extract(
                                                extractionResult?.layoutPages.orEmpty(), cleanText
                                            )
                                            val haveKeys = fieldList.map { it.key }.toMutableSet()
                                            for (lf in layoutSheet.fields) {
                                                if (lf.key in haveKeys) continue
                                                haveKeys.add(lf.key)
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = lf.key, value = lf.value))
                                                fieldList.add(
                                                    com.suhrud.docsy.data.db.entity.FieldEntity(
                                                        documentId = docId, key = lf.key, label = lf.label, value = lf.value,
                                                        confidence = lf.confidence, isDerived = lf.derived, formula = lf.formula
                                                    )
                                                )
                                                jsonBuilder.append(",\"").append(lf.key).append("\":\"").append(lf.value.replace("\"", "'")).append("\"")
                                            }
                                            if (layoutSheet.subjects.isNotEmpty() && layoutSheet.subjects.size > subjectList.size) {
                                                // The table gave more rows than the text parser: trust the table.
                                                subjectList.clear()
                                                factList.removeAll { it.key.startsWith("subject_") }
                                                fieldList.removeAll { it.key.startsWith("subject_") }
                                                for ((i, sub) in layoutSheet.subjects.withIndex()) {
                                                    val extras = listOfNotNull(sub.grade, sub.gradePoint?.let { "GP $it" }, sub.credits?.let { "$it cr" })
                                                        .joinToString(" \u2022 ")
                                                    val shown = if (sub.marks != null) "${sub.marks}/${sub.maxMarks ?: "?"}" else extras
                                                    val subKey = "subject_${sub.name.lowercase().replace(" ", "_")}"
                                                    factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = subKey, value = shown))
                                                    fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = subKey, label = sub.name, value = shown))
                                                    subjectList.add(
                                                        com.suhrud.docsy.data.db.entity.SubjectEntity(
                                                            documentId = docId,
                                                            rowNo = i,
                                                            name = sub.name,
                                                            marks = sub.marks,
                                                            maxMarks = sub.maxMarks,
                                                            grade = if (sub.marks != null) {
                                                                listOfNotNull(sub.grade, sub.gradePoint?.let { "GP $it" }, sub.credits?.let { "$it cr" })
                                                                    .joinToString(" \u2022 ").ifBlank { null }
                                                            } else extras.ifBlank { null },
                                                            confidence = sub.confidence
                                                        )
                                                    )
                                                }
                                            }

                                            // Ingest into v12 marksheets / marksheet_subjects tables
                                            try {
                                                val readByStr = if (extractionResult?.usedVlm == true) "VLM" else "NATIVE"
                                                val (marksheetEntity, marksheetSubjectEntities) = com.suhrud.docsy.understanding.MarksheetIngestionEngine.buildMarksheetEntities(
                                                    docId = docId,
                                                    parsed = parsed,
                                                    layoutSheet = layoutSheet,
                                                    cleanText = cleanText,
                                                    readBy = readByStr
                                                )
                                                val insertedMarksheetId = marksheetDao.insertMarksheet(marksheetEntity)
                                                if (marksheetSubjectEntities.isNotEmpty()) {
                                                    marksheetDao.insertMarksheetSubjects(
                                                        marksheetSubjectEntities.map { it.copy(marksheetId = insertedMarksheetId) }
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                com.suhrud.docsy.util.CrashReporter.writeLoggedThrowable(context, "MarksheetIngestion", e)
                                            }
                                        } else {
                                            // Government IDs or generic documents
                                            val detectedIds = com.suhrud.docsy.understanding.GovernmentIdDetector.detectIds(cleanText)
                                            for (id in detectedIds) {
                                                factList.add(
                                                    com.suhrud.docsy.data.db.entity.FactEntity(
                                                        documentId = docId,
                                                        key = id.type.lowercase(),
                                                        value = id.normalizedValue
                                                    )
                                                )
                                                fieldList.add(
                                                    com.suhrud.docsy.data.db.entity.FieldEntity(
                                                        documentId = docId,
                                                        key = id.type.lowercase(),
                                                        value = id.normalizedValue,
                                                        normalizedValue = id.normalizedValue
                                                    )
                                                )
                                                jsonBuilder.append(",\"").append(id.type.lowercase()).append("\":\"").append(id.normalizedValue).append("\"")
                                            }
                                            if (ownerName != null) {
                                                factList.add(com.suhrud.docsy.data.db.entity.FactEntity(documentId = docId, key = "owner", value = ownerName))
                                                fieldList.add(com.suhrud.docsy.data.db.entity.FieldEntity(documentId = docId, key = "owner", value = ownerName))
                                                jsonBuilder.append(",\"owner\":\"").append(ownerName).append("\"")
                                            }
                                        }

                                        if (extractionResult?.usedVlm == true) {
                                            fieldList.add(
                                                com.suhrud.docsy.data.db.entity.FieldEntity(
                                                    documentId = docId, key = "read_by", label = "Read by",
                                                    value = "Read by the on-device vision model. Please check the numbers against the original.",
                                                    confidence = 0.75f, needsReview = true
                                                )
                                            )
                                        }

                                        // Give EVERY document a structured Field | Value table (never leave a card empty).
                                        val existingFieldKeys = fieldList.map { it.key }.toMutableSet()
                                        for (extra in com.suhrud.docsy.extraction.LabelValueExtractor.extract(docId, cleanText)) {
                                            if (extra.key !in existingFieldKeys) {
                                                fieldList.add(extra)
                                                existingFieldKeys.add(extra.key)
                                            }
                                        }
                                        if (fieldList.isEmpty()) {
                                            fieldList.add(
                                                com.suhrud.docsy.data.db.entity.FieldEntity(
                                                    documentId = docId,
                                                    key = "text_preview",
                                                    label = "Text preview",
                                                    value = cleanText.trim().replace(Regex("\\s+"), " ").take(300),
                                                    confidence = 0.5f,
                                                    needsReview = true
                                                )
                                            )
                                        }

                                        jsonBuilder.append("}")

                                        if (factList.isNotEmpty()) {
                                            factDao.insertFacts(factList)
                                        }
                                        if (fieldList.isNotEmpty()) {
                                            fieldDao.insertFields(fieldList)
                                        }
                                        if (subjectList.isNotEmpty()) {
                                            subjectDao.insertSubjects(subjectList)
                                        }

                                        docJsonDao.insertDocJson(
                                            com.suhrud.docsy.data.db.entity.DocJsonEntity(
                                                documentId = docId,
                                                json = jsonBuilder.toString()
                                            )
                                        )
                                    }
                                }
                            }

                            val category = ContentSectionDetector.detectSection(discovered.file)

                            val discCount = discoveredCounter.incrementAndGet()
                            val walkCount = totalWalkedCounter.get()

                            ReadingStateRepository.recordProcessedFile(
                                category = category,
                                fileName = discovered.file.name,
                                fileIndex = discCount,
                                totalFilesSoFar = walkCount
                            )
                        } catch (t: Throwable) {
                            android.util.Log.e("FileScanner", "Error processing file: ${discovered.path}", t)
                        }
                    }
                }
            }

            try {
                for (root in roots) {
                    walkDirectory(
                        dir = root,
                        channel = channel,
                        visitedDirs = visitedDirs,
                        totalWalkedCounter = totalWalkedCounter
                    )
                }
                ReadingStateRepository.setDiscoveryFinished(totalWalkedCounter.get())
            } catch (e: Exception) {
                com.suhrud.docsy.util.CrashReporter.writeLoggedThrowable(context, "FileScanner", e)
                _scanProgress.update { it.copy(error = e.localizedMessage) }
            } finally {
                channel.close()
                workerJobs.forEach { it.join() }
                diagJob.cancel()
            }
        }

        _scanProgress.update {
            it.copy(
                isScanning = false,
                totalFilesWalked = totalWalkedCounter.get(),
                documentsDiscovered = discoveredCounter.get(),
                currentDirectory = "Complete"
            )
        }

        _scanProgress.value
    }

    private suspend fun walkDirectory(
        dir: File,
        channel: Channel<DiscoveredFile>,
        visitedDirs: MutableSet<String>,
        totalWalkedCounter: AtomicInteger
    ) {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return

        val canonicalPath = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }

        if (!visitedDirs.add(canonicalPath)) {
            return
        }

        if (FileFilter.shouldSkipDirectory(dir, docsyPackageDir)) {
            return
        }

        _scanProgress.update { it.copy(currentDirectory = dir.name) }

        val children = dir.listFiles() ?: return

        for (child in children) {
            if (child.isDirectory) {
                walkDirectory(child, channel, visitedDirs, totalWalkedCounter)
            } else if (child.isFile) {
                totalWalkedCounter.incrementAndGet()

                if (FileFilter.isSupportedFile(child)) {
                    val discovered = DiscoveredFile(
                        file = child,
                        path = child.absolutePath,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified()
                    )
                    channel.send(discovered)
                }
            }
        }
    }
}
