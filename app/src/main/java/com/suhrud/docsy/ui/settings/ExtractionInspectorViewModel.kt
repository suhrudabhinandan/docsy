package com.suhrud.docsy.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.LineEntity
import com.suhrud.docsy.data.db.entity.RecordEntity
import com.suhrud.docsy.data.db.entity.SheetMetaEntity
import com.suhrud.docsy.data.db.entity.SubjectEntity
import com.suhrud.docsy.scanner.ContentSectionDetector
import com.suhrud.docsy.scanner.FileSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DataHealthSummary(
    val totalDocs: Int = 0,
    val withText: Int = 0,
    val withFields: Int = 0,
    val withSubjects: Int = 0,
    val withRecords: Int = 0,
    val needsReview: Int = 0,
    val failed: Int = 0
)

data class DocumentCardItem(
    val doc: DocumentEntity,
    val fieldsPreview: List<FieldEntity>,
    val averageConfidence: Float,
    val charCount: Int
)

data class DocumentDetailState(
    val doc: DocumentEntity,
    val fields: List<FieldEntity>,
    val subjects: List<SubjectEntity>,
    val sheetMeta: List<SheetMetaEntity>,
    val records: List<RecordEntity>,
    val lines: List<LineEntity>,
    val fullText: String
)

@OptIn(FlowPreview::class)
class ExtractionInspectorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DocsyDatabase.getInstance(application)

    private val _dataHealth = MutableStateFlow(DataHealthSummary())
    val dataHealth: StateFlow<DataHealthSummary> = _dataHealth.asStateFlow()

    private val _selectedSection = MutableStateFlow(FileSection.PDF)
    val selectedSection: StateFlow<FileSection> = _selectedSection.asStateFlow()

    private val _statusFilter = MutableStateFlow("ALL") // "ALL", "DONE", "NEEDS_REVIEW", "FAILED"
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _healthMetricFilter = MutableStateFlow<String?>(null) // "WITH_TEXT", "WITH_FIELDS", "WITH_SUBJECTS", "WITH_RECORDS", "NEEDS_REVIEW", "FAILED"
    val healthMetricFilter: StateFlow<String?> = _healthMetricFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _debouncedQuery = MutableStateFlow("")
    val debouncedQuery: StateFlow<String> = _debouncedQuery.asStateFlow()

    private val _categoryCounts = MutableStateFlow<Map<FileSection, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<FileSection, Int>> = _categoryCounts.asStateFlow()

    private val _documentCards = MutableStateFlow<List<DocumentCardItem>>(emptyList())
    val documentCards: StateFlow<List<DocumentCardItem>> = _documentCards.asStateFlow()

    private val _selectedDocDetail = MutableStateFlow<DocumentDetailState?>(null)
    val selectedDocDetail: StateFlow<DocumentDetailState?> = _selectedDocDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { removeLegacyDemoDocuments() }
            refreshAllData()
        }

        viewModelScope.launch {
            _searchQuery.debounce(300L).collect { q ->
                _debouncedQuery.value = q
                refreshDocumentList()
            }
        }
    }

    fun refreshAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                // Data health summary
                val allDocs = try {
                    db.documentDao().getAllDocumentsSnapshot()
                } catch (e: Exception) {
                    _errorMessage.value = "Error reading database snapshot: ${e.message}"
                    emptyList()
                }

                val withTextCount = db.lineDao().getDocsWithLinesCount()
                val withFieldsCount = db.fieldDao().getDocsWithFieldsCount()
                val withSubjectsCount = db.subjectDao().getDocsWithSubjectsCount()
                val withRecordsCount = db.recordDao().getDocsWithRecordsCount()
                val needsReviewCount = allDocs.count { it.state == "NEEDS_REVIEW" }
                val failedCount = allDocs.count { it.state == "FAILED" }

                _dataHealth.value = DataHealthSummary(
                    totalDocs = allDocs.size,
                    withText = withTextCount,
                    withFields = withFieldsCount,
                    withSubjects = withSubjectsCount,
                    withRecords = withRecordsCount,
                    needsReview = needsReviewCount,
                    failed = failedCount
                )

                // Category counts
                val countsMap = FileSection.ALL_SECTIONS.associateWith { sec ->
                    allDocs.count { doc ->
                        val detected = ContentSectionDetector.detectSection(File(doc.path))
                        doc.section.equals(sec.id, ignoreCase = true) || doc.fileSection == sec || detected == sec ||
                                (sec == FileSection.SMS && (doc.docType.orEmpty().contains("sms", ignoreCase = true) || doc.ext.equals("sms", ignoreCase = true) || doc.mime.equals("text/sms", ignoreCase = true)))
                    }
                }
                _categoryCounts.value = countsMap

                refreshDocumentListInternal(allDocs)
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to load extraction list: ${t.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectSection(section: FileSection) {
        _selectedSection.value = section
        _healthMetricFilter.value = null
        viewModelScope.launch { refreshDocumentList() }
    }

    fun setStatusFilter(filter: String) {
        _statusFilter.value = filter
        _healthMetricFilter.value = null
        viewModelScope.launch { refreshDocumentList() }
    }

    fun setHealthMetricFilter(filter: String) {
        if (_healthMetricFilter.value == filter) {
            _healthMetricFilter.value = null
        } else {
            _healthMetricFilter.value = filter
        }
        viewModelScope.launch { refreshDocumentList() }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private suspend fun refreshDocumentList() {
        withContext(Dispatchers.IO) {
            val allDocs = db.documentDao().getAllDocumentsSnapshot()
            refreshDocumentListInternal(allDocs)
        }
    }

    private suspend fun refreshDocumentListInternal(allDocs: List<DocumentEntity>) {
        val currentSec = _selectedSection.value
        val currentStatus = _statusFilter.value
        val healthFilter = _healthMetricFilter.value
        val q = _debouncedQuery.value.trim().lowercase()

        val cards = mutableListOf<DocumentCardItem>()

        for (doc in allDocs) {
            val detectedSec = ContentSectionDetector.detectSection(File(doc.path))
            val matchesSection = doc.section.equals(currentSec.id, ignoreCase = true) || doc.fileSection == currentSec || detectedSec == currentSec ||
                    (currentSec == FileSection.SMS && (doc.docType.orEmpty().contains("sms", ignoreCase = true) || doc.ext.equals("sms", ignoreCase = true) || doc.mime.equals("text/sms", ignoreCase = true)))

            if (!matchesSection && healthFilter == null) continue

            val matchesStatus = when (currentStatus) {
                "DONE" -> doc.state == "DONE" || doc.state == "INDEXED"
                "NEEDS_REVIEW" -> doc.state == "NEEDS_REVIEW"
                "FAILED" -> doc.state == "FAILED"
                else -> true
            }
            if (!matchesStatus) continue

            val fields = db.fieldDao().getFieldsForDocument(doc.id)
            val subjects = db.subjectDao().getSubjectsForDocument(doc.id)
            val records = db.recordDao().getRecordsForDocument(doc.id)
            val lines = db.lineDao().getLinesForDocument(doc.id)

            val matchesHealthMetric = when (healthFilter) {
                "WITH_TEXT" -> lines.isNotEmpty() || !doc.extractedText.isNullOrBlank()
                "WITH_FIELDS" -> fields.isNotEmpty()
                "WITH_SUBJECTS" -> subjects.isNotEmpty()
                "WITH_RECORDS" -> records.isNotEmpty()
                "NEEDS_REVIEW" -> doc.state == "NEEDS_REVIEW"
                "FAILED" -> doc.state == "FAILED"
                else -> true
            }
            if (!matchesHealthMetric) continue

            val textContent = lines.joinToString("\n") { it.text }
            val matchesSearch = if (q.isBlank()) true else {
                doc.displayName.lowercase().contains(q) ||
                        doc.name.lowercase().contains(q) ||
                        doc.docType.orEmpty().lowercase().contains(q) ||
                        fields.any { it.label.lowercase().contains(q) || it.value.lowercase().contains(q) } ||
                        textContent.lowercase().contains(q)
            }
            if (!matchesSearch) continue

            val avgConf = if (fields.isNotEmpty()) fields.map { it.confidence }.average().toFloat() else doc.docTypeScore
            val charCount = if (lines.isNotEmpty()) lines.sumOf { it.text.length } else doc.charsExtracted.coerceAtLeast(doc.extractedText?.length ?: 0)

            cards.add(
                DocumentCardItem(
                    doc = doc,
                    fieldsPreview = fields.take(2),
                    averageConfidence = avgConf,
                    charCount = charCount
                )
            )
        }

        _documentCards.value = cards
    }

    /**
     * Earlier builds inserted six demo documents into the real database when the inspector opened.
     * Remove exactly those rows (matched by their fake paths) and their related rows. Real files are never touched.
     */
    private fun removeLegacyDemoDocuments() {
        val fakePaths = listOf(
            "/storage/emulated/0/Download/electricity_bill_apr2024.jpg",
            "/storage/emulated/0/Pictures/aadhaar_pan_cards.png",
            "/storage/emulated/0/Documents/cat_2024_admit_card.docx",
            "/storage/emulated/0/Documents/class_12_marksheet.pdf",
            "/storage/emulated/0/Documents/classroom_semester4_results.xlsx",
            "/storage/emulated/0/Download/scanned_note_2024.jpg"
        ).filter { !File(it).exists() }
        if (fakePaths.isEmpty()) return
        val inList = fakePaths.joinToString(",") { "'" + it.replace("'", "''") + "'" }
        val sqlite = db.openHelper.writableDatabase
        val related = listOf(
            "fields", "subjects", "records", "sheet_meta", "lines", "chunks",
            "doc_tables", "doc_blocks", "doc_json", "doc_layout", "doc_facts", "doc_entities"
        )
        for (table in related) {
            try {
                sqlite.execSQL("DELETE FROM $table WHERE documentId IN (SELECT id FROM documents WHERE path IN ($inList))")
            } catch (_: Exception) {
                // Table or column name may differ; skipping is harmless.
            }
        }
        try {
            sqlite.execSQL("DELETE FROM documents WHERE path IN ($inList)")
        } catch (_: Exception) {}
    }

    fun openDocumentDetail(docId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val doc = db.documentDao().getDocumentById(docId) ?: return@launch
                val fields = db.fieldDao().getFieldsForDocument(docId)
                val subjects = db.subjectDao().getSubjectsForDocument(docId)
                val sheetMeta = db.sheetMetaDao().getSheetMetaForDocument(docId)
                val records = db.recordDao().getRecordsForDocument(docId)
                val lines = db.lineDao().getLinesForDocument(docId)
                val fullText = if (lines.isNotEmpty()) lines.joinToString("\n") { it.text } else db.documentDao().getExtractedTextById(docId).orEmpty()

                _selectedDocDetail.value = DocumentDetailState(
                    doc = doc,
                    fields = fields,
                    subjects = subjects,
                    sheetMeta = sheetMeta,
                    records = records,
                    lines = lines,
                    fullText = fullText
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun closeDocumentDetail() {
        _selectedDocDetail.value = null
    }

    fun updateFieldValue(fieldId: Long, newValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.fieldDao().updateFieldValueByUser(fieldId, newValue)
            val currentDetail = _selectedDocDetail.value
            if (currentDetail != null) {
                openDocumentDetail(currentDetail.doc.id)
            }
            refreshDocumentList()
        }
    }

    fun reclassifyDocument(docId: Long, newType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = db.documentDao().getDocumentById(docId) ?: return@launch
            db.documentDao().updateDocument(
                doc.copy(
                    docType = newType,
                    docTypeScore = 1.0f,
                    state = "DONE",
                    updatedAt = System.currentTimeMillis()
                )
            )
            openDocumentDetail(docId)
            refreshAllData()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY] as? Application)
                ExtractionInspectorViewModel(app)
            }
        }
    }
}
