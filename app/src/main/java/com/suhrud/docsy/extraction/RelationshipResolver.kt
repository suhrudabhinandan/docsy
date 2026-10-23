package com.suhrud.docsy.extraction

import com.suhrud.docsy.extractor.models.ProvenanceInfo

data class EntityRelationship(
    val subjectEntity: String,
    val relationType: String,
    val objectEntity: String,
    val confidence: Float,
    val evidenceText: String? = null,
    val provenance: ProvenanceInfo? = null
)

object RelationshipResolver {

    fun resolveRelationships(
        entities: List<ExtractedStructuredEntity>,
        docType: String = "",
        sourcePath: String = ""
    ): List<EntityRelationship> {
        val relationships = mutableListOf<EntityRelationship>()

        val studentName = entities.firstOrNull { it.entityType == "STUDENT_NAME" }?.normalizedValue
            ?: entities.firstOrNull { it.entityType == "PERSON_NAME" }?.normalizedValue
        val rollNo = entities.firstOrNull { it.entityType == "ROLL_NUMBER" }?.normalizedValue
        val regNo = entities.firstOrNull { it.entityType == "REGISTRATION_NUMBER" }?.normalizedValue
        val institution = entities.firstOrNull { it.entityType == "INSTITUTION_NAME" }?.normalizedValue
            ?: entities.firstOrNull { it.entityType == "ORGANIZATION" }?.normalizedValue

        val invoiceNo = entities.firstOrNull { it.entityType == "INVOICE_NUMBER" }?.normalizedValue
        val amount = entities.firstOrNull { it.entityType == "AMOUNT" }?.normalizedValue
        val employer = entities.firstOrNull { it.entityType == "EMPLOYER" }?.normalizedValue

        if (studentName != null) {
            if (rollNo != null) {
                relationships.add(
                    EntityRelationship(
                        subjectEntity = studentName,
                        relationType = "HAS_ROLL_NO",
                        objectEntity = rollNo,
                        confidence = 0.95f,
                        evidenceText = "Student $studentName has roll number $rollNo",
                        provenance = ProvenanceInfo(sourcePath)
                    )
                )
            }
            if (regNo != null) {
                relationships.add(
                    EntityRelationship(
                        subjectEntity = studentName,
                        relationType = "HAS_REG_NO",
                        objectEntity = regNo,
                        confidence = 0.95f,
                        evidenceText = "Student $studentName has registration number $regNo",
                        provenance = ProvenanceInfo(sourcePath)
                    )
                )
            }
            if (institution != null) {
                relationships.add(
                    EntityRelationship(
                        subjectEntity = studentName,
                        relationType = "ENROLLED_AT",
                        objectEntity = institution,
                        confidence = 0.90f,
                        evidenceText = "Student $studentName enrolled at $institution",
                        provenance = ProvenanceInfo(sourcePath)
                    )
                )
            }
        }

        if (docType == "INVOICE" || docType == "UTILITY_BILL") {
            if (invoiceNo != null && institution != null) {
                relationships.add(
                    EntityRelationship(
                        subjectEntity = invoiceNo,
                        relationType = "ISSUED_BY",
                        objectEntity = institution,
                        confidence = 0.90f,
                        evidenceText = "Invoice $invoiceNo issued by $institution",
                        provenance = ProvenanceInfo(sourcePath)
                    )
                )
            }
            if (invoiceNo != null && amount != null) {
                relationships.add(
                    EntityRelationship(
                        subjectEntity = invoiceNo,
                        relationType = "HAS_AMOUNT",
                        objectEntity = amount,
                        confidence = 0.92f,
                        evidenceText = "Invoice $invoiceNo total amount $amount",
                        provenance = ProvenanceInfo(sourcePath)
                    )
                )
            }
        }

        if (studentName != null && employer != null && docType == "RESUME") {
            relationships.add(
                EntityRelationship(
                    subjectEntity = studentName,
                    relationType = "WORKS_FOR",
                    objectEntity = employer,
                    confidence = 0.88f,
                    evidenceText = "$studentName worked for $employer",
                    provenance = ProvenanceInfo(sourcePath)
                )
            )
        }

        return relationships
    }
}
