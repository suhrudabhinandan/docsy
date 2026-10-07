package com.suhrud.docsy.data.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object DocumentCategories {
    const val ALL = "All"
    const val ELECTRICITY_BILL = "Electricity Bill"
    const val WATER_BILL = "Water Bill"
    const val GAS_BILL = "Gas Bill"
    const val AADHAAR = "Aadhaar Card"
    const val PAN = "PAN Card"
    const val MARKSHEET = "Marksheet"
    const val CERTIFICATE = "Certificate"
    const val BANK_STATEMENT = "Bank Statement"
    const val SCHEDULE = "Schedule"
    const val INVOICE = "Invoice"
    const val RESUME = "Resume"
    const val ID_CARD = "ID Card"
    const val PHOTOGRAPH = "Photograph"
    const val SCREENSHOT = "Screenshot"
    const val GENERAL = "General"
    const val UNKNOWN = "Unknown"
}

data class ExamScheduleItem(
    val subject: String,
    val date: String,
    val time: String = "",
    val venue: String = ""
)

data class StructuredFields(
    val category: String = DocumentCategories.UNKNOWN,
    val billingMonth: String? = null,
    val billingYear: Int? = null,
    val amountPayable: Double? = null,
    val dueDate: String? = null,
    val billNumber: String? = null,
    val consumerNumber: String? = null,
    val utilityType: String? = null,
    val issuerOrProvider: String? = null,
    val aadhaarNumberMasked: String? = null,
    val aadhaarNumberFull: String? = null,
    val panNumber: String? = null,
    val rollNumber: String? = null,
    val boardName: String? = null,
    val classOrStandard: String? = null,
    val percentageOrCgpa: String? = null,
    val personName: String? = null,
    val dateOfBirth: String? = null,
    val address: String? = null,
    val semester: String? = null,
    val exams: List<ExamScheduleItem>? = null,
    val organization: String? = null
)

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["pathUri"], unique = true),
        Index("documentType"),
        Index("modifiedDate"),
        Index("parentFolder"),
        Index("indexStatus")
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val pathUri: String,
    val fileName: String,
    val extension: String = "",
    val mimeType: String = "application/octet-stream",
    val fileSizeBytes: Long = 0L,
    val createdDate: Long = System.currentTimeMillis(),
    val modifiedDate: Long = System.currentTimeMillis(),
    val isDirectory: Boolean = false,
    val parentFolder: String = "",
    val contentHash: String = "",
    val extractedText: String = "",
    val documentType: String = DocumentCategories.UNKNOWN,
    val detectedMonth: String? = null,
    val detectedYear: Int? = null,
    val detectedUtility: String? = null,
    val detectedIssuer: String? = null,
    val detectedIdType: String? = null,
    val indexStatus: String = "PENDING", // PENDING, EXTRACTED, EMPTY, UNSUPPORTED, ENCRYPTED, FAILED
    val failureReason: String? = null,
    val extractorVersion: Int = 1,
    val extractedAt: Long = 0L,
    val ownerName: String? = null,
    val lastIndexedTime: Long = System.currentTimeMillis(),
    val summary: String = "",
    val structuredJson: String = ""
)

@Entity(tableName = "documents_fts")
@Fts4(contentEntity = DocumentEntity::class)
data class DocumentFts(
    val fileName: String,
    val extractedText: String
)

@Entity(
    tableName = "document_fields",
    indices = [
        Index("documentId"),
        Index("fieldKey")
    ]
)
data class DocumentFieldEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val fieldKey: String,
    val fieldValue: String,
    val numericValue: Double? = null,
    val dateValue: Long? = null
)

@Entity(
    tableName = "step_logs",
    indices = [Index("dateString")]
)
data class StepLogEntity(
    @PrimaryKey
    val dateString: String,
    val stepCount: Long = 0L,
    val lastSensorReading: Long = 0L,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_sessions",
    indices = [Index("lastUpdatedAt")]
)
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    indices = [Index("timestamp"), Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = "default_session",
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val answerHighlight: String? = null,
    val supportingMetadata: String? = null,
    val subtext: String? = null,
    val sourceDocumentPath: String? = null,
    val sourceDocumentName: String? = null,
    val sourceDocumentMime: String? = null,
    val isSensitive: Boolean = false,
    val unmaskedValue: String? = null,
    val isRevealed: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val answerHighlight: String? = null,
    val supportingMetadata: String? = null,
    val subtext: String? = null,
    val sourceDocument: DocumentEntity? = null,
    val sourceDescription: String? = null,
    val isSensitive: Boolean = false,
    val unmaskedValue: String? = null,
    val isRevealed: Boolean = false,
    val scheduleItems: List<ExamScheduleItem>? = null,
    val contributingSources: List<DocumentEntity>? = null,
    val missingPermissionRequired: String? = null
)
