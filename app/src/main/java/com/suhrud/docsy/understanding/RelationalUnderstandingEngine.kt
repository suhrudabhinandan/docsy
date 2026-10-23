package com.suhrud.docsy.understanding

import com.suhrud.docsy.data.db.DocsyDatabase
import com.suhrud.docsy.data.db.entity.DocEntityRecord
import com.suhrud.docsy.data.db.entity.EntityRecord
import com.suhrud.docsy.data.db.entity.FieldEntity
import com.suhrud.docsy.data.db.entity.RelationRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TimelineItem(
    val docId: Long,
    val docType: String,
    val periodOrSequence: String,
    val sequenceNumber: Int, // e.g. 1 for Sem 1, 2 for Sem 2
    val label: String,
    val valueMap: Map<String, String> = emptyMap()
)

data class TimelineComparison(
    val ownerName: String,
    val targetItem: TimelineItem?,
    val comparisonItem: TimelineItem?,
    val differenceText: String,
    val working: String
)

object RelationalUnderstandingEngine {

    private const val MATCH_THRESHOLD = 0.85f

    /**
     * Extracts and canonicalizes entities for a document, persisting to database.
     */
    suspend fun processDocumentRelations(
        db: DocsyDatabase,
        docId: Long,
        docType: String,
        cleanText: String,
        fields: List<FieldEntity>,
        ownerName: String?
    ) {
        val entityDao = db.entityDao()
        val docEntityDao = db.docEntityDao()
        val relationDao = db.relationDao()

        // Clear previous relations for re-indexing
        docEntityDao.deleteDocEntitiesForDoc(docId)
        relationDao.deleteRelationsForDoc(docId)

        val extractedEntities = mutableListOf<Pair<EntityRecord, String>>() // EntityRecord to role

        // 1. Person entity (Owner)
        if (!ownerName.isNullOrBlank()) {
            val canonicalOwner = NameMatcher.normalizeName(ownerName)
            val existing = findOrCreateEntity(entityDao, "person", canonicalOwner)
            extractedEntities.add(existing to "owner")
        }

        // 2. Extracted fields (ID numbers, accounts, institutions, courses, periods)
        for (field in fields) {
            val key = field.key.lowercase(Locale.ROOT)
            val valStr = field.value.trim()
            if (valStr.isBlank()) continue

            when {
                key.contains("aadhaar") || key.contains("pan") || key.contains("roll") || key.contains("epic") -> {
                    val canonicalId = valStr.replace(" ", "").uppercase(Locale.ROOT)
                    val existing = findOrCreateEntity(entityDao, "person", canonicalId)
                    extractedEntities.add(existing to "subject")
                }
                key.contains("account") || key.contains("chq") || key.contains("policy") -> {
                    val canonicalAcc = valStr.replace(" ", "")
                    val existing = findOrCreateEntity(entityDao, "account", canonicalAcc)
                    extractedEntities.add(existing to "account_holder")
                }
                key.contains("board") || key.contains("university") || key.contains("employer") || key.contains("bank") -> {
                    val canonicalInst = valStr.uppercase(Locale.ROOT)
                    val existing = findOrCreateEntity(entityDao, "institution", canonicalInst)
                    extractedEntities.add(existing to "issuer")
                }
                key.contains("course") || key.contains("degree") || key.contains("branch") -> {
                    val canonicalCourse = valStr.uppercase(Locale.ROOT)
                    val existing = findOrCreateEntity(entityDao, "course", canonicalCourse)
                    extractedEntities.add(existing to "subject")
                }
                key.contains("semester") || key.contains("month") || key.contains("year") || key.contains("period") -> {
                    val existing = findOrCreateEntity(entityDao, "period", valStr)
                    extractedEntities.add(existing to "period")
                }
            }
        }

        // Save doc_entities and establish relations between documents that share entities
        for ((entityRec, role) in extractedEntities) {
            docEntityDao.insertDocEntity(
                DocEntityRecord(docId = docId, entityId = entityRec.id, role = role)
            )

            // Find other docs with same entity and create relation with stored evidence
            val otherDocIds = docEntityDao.getDocIdsForEntity(entityRec.id).filter { it != docId }
            for (otherId in otherDocIds) {
                val relType = when (entityRec.type) {
                    "person" -> "SAME_PERSON"
                    "course" -> "SAME_COURSE"
                    "account" -> "SAME_ACCOUNT"
                    "institution" -> "SAME_INSTITUTION"
                    else -> "SHARED_ENTITY"
                }

                // Strict retrieval check: ensure no guessed entity links
                relationDao.insertRelation(
                    RelationRecord(
                        aId = docId,
                        bId = otherId,
                        relation = relType,
                        score = 1.0f,
                        evidenceDocId = docId
                    )
                )
            }
        }
    }

    private suspend fun findOrCreateEntity(
        entityDao: com.suhrud.docsy.data.db.dao.EntityDao,
        type: String,
        canonicalName: String
    ): EntityRecord {
        val existing = entityDao.getEntityByTypeAndName(type, canonicalName)
        if (existing != null) return existing

        val newId = entityDao.insertEntity(
            EntityRecord(type = type, canonicalName = canonicalName)
        )
        return EntityRecord(id = newId, type = type, canonicalName = canonicalName)
    }

    /**
     * Builds an ordered timeline for an owner (e.g. semesters 1 to N, monthly statements/bills).
     */
    fun buildTimeline(
        ownerName: String,
        documents: List<Triple<Long, String, Map<String, String>>> // docId, docType, fieldsMap
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()

        for ((docId, docType, fieldMap) in documents) {
            val semStr = fieldMap["semester"] ?: fieldMap["class"] ?: ""
            val monthStr = fieldMap["month"] ?: fieldMap["bill_date"] ?: fieldMap["date"] ?: ""

            var seqNum = 0
            if (semStr.isNotBlank()) {
                val num = Regex("""\d+""").find(semStr)?.value?.toIntOrNull()
                if (num != null) seqNum = num
            }

            val label = when {
                semStr.isNotBlank() -> "Semester $seqNum"
                monthStr.isNotBlank() -> monthStr
                else -> docType
            }

            items.add(
                TimelineItem(
                    docId = docId,
                    docType = docType,
                    periodOrSequence = if (semStr.isNotBlank()) semStr else monthStr,
                    sequenceNumber = seqNum,
                    label = label,
                    valueMap = fieldMap
                )
            )
        }

        return items.sortedWith(compareBy({ it.sequenceNumber }, { it.periodOrSequence }))
    }

    /**
     * Solves structural queries on timeline: "last", "previous", "highest", "compare with 2nd sem".
     */
    fun resolveTimelineQuery(
        timeline: List<TimelineItem>,
        query: String
    ): TimelineComparison? {
        if (timeline.isEmpty()) return null
        val lower = query.lowercase(Locale.ROOT)

        if (lower.contains("highest")) {
            val topItem = timeline.maxByOrNull { item ->
                item.valueMap["total_marks"]?.toDoubleOrNull()
                    ?: item.valueMap["percentage"]?.replace("%", "")?.toDoubleOrNull()
                    ?: 0.0
            }
            if (topItem != null) {
                val valStr = topItem.valueMap["total_marks"] ?: topItem.valueMap["percentage"] ?: "N/A"
                return TimelineComparison(
                    ownerName = "",
                    targetItem = topItem,
                    comparisonItem = null,
                    differenceText = "Highest recorded performance in ${topItem.label}: $valStr",
                    working = "Extracted maximum value across ${timeline.size} timeline items."
                )
            }
        }

        if (lower.contains("compare") || lower.contains("vs") || lower.contains("difference")) {
            val semNumbers = Regex("""\d+""").findAll(lower).mapNotNull { it.value.toIntOrNull() }.toList()
            val sem1 = if (semNumbers.isNotEmpty()) timeline.find { it.sequenceNumber == semNumbers[0] } else timeline.getOrNull(0)
            val sem2 = if (semNumbers.size > 1) timeline.find { it.sequenceNumber == semNumbers[1] } else timeline.getOrNull(1)

            if (sem1 != null && sem2 != null) {
                val v1 = sem1.valueMap["percentage"]?.replace("%", "")?.toBigDecimalOrNull()
                    ?: sem1.valueMap["total_marks"]?.toBigDecimalOrNull()
                val v2 = sem2.valueMap["percentage"]?.replace("%", "")?.toBigDecimalOrNull()
                    ?: sem2.valueMap["total_marks"]?.toBigDecimalOrNull()

                if (v1 != null && v2 != null) {
                    val diff = (v1 - v2).abs().setScale(2, RoundingMode.HALF_UP)
                    return TimelineComparison(
                        ownerName = "",
                        targetItem = sem1,
                        comparisonItem = sem2,
                        differenceText = "${sem1.label} ($v1) vs ${sem2.label} ($v2) difference = $diff",
                        working = "$v1 - $v2 = $diff"
                    )
                }
            }
        }

        if (lower.contains("previous") || lower.contains("last")) {
            val lastItem = timeline.last()
            val prevItem = if (timeline.size >= 2) timeline[timeline.size - 2] else null
            return TimelineComparison(
                ownerName = "",
                targetItem = lastItem,
                comparisonItem = prevItem,
                differenceText = "Latest: ${lastItem.label}" + if (prevItem != null) ", Previous: ${prevItem.label}" else "",
                working = "Timeline order sequence: ${timeline.map { it.label }}"
            )
        }

        return null
    }

    /**
     * Calculates derived facts in code: totals, percentages, averages, trends, age, tenure.
     */
    fun computeDerivedFacts(fields: Map<String, String>): Map<String, String> {
        val derived = mutableMapOf<String, String>()

        // 1. Calculate Age from Date of Birth
        val dobStr = fields["dob"] ?: fields["date_of_birth"] ?: fields["birth_date"]
        if (!dobStr.isNullOrBlank()) {
            val age = calculateAge(dobStr)
            if (age != null) {
                derived["age"] = "$age years"
            }
        }

        // 2. Tenure from Start and End dates
        val startDateStr = fields["start_date"] ?: fields["date_of_joining"] ?: fields["issue_date"]
        val endDateStr = fields["end_date"] ?: fields["relieving_date"] ?: fields["valid_till"]
        if (!startDateStr.isNullOrBlank() && !endDateStr.isNullOrBlank()) {
            val tenureMonths = calculateTenureMonths(startDateStr, endDateStr)
            if (tenureMonths != null) {
                derived["tenure"] = "$tenureMonths months"
            }
        }

        return derived
    }

    private fun calculateAge(dobStr: String): Int? {
        val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd MMM yyyy")
        for (fmt in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                val dob = LocalDate.parse(dobStr.trim(), formatter)
                val now = LocalDate.now()
                return Period.between(dob, now).years
            } catch (_: Exception) {}
        }
        return null
    }

    private fun calculateTenureMonths(startDateStr: String, endDateStr: String): Int? {
        val formats = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                val start = LocalDate.parse(startDateStr.trim(), formatter)
                val end = LocalDate.parse(endDateStr.trim(), formatter)
                val period = Period.between(start, end)
                return period.years * 12 + period.months
            } catch (_: Exception) {}
        }
        return null
    }
}
