package com.suhrud.docsy.understanding

import com.suhrud.docsy.reasoning.DocsyCalculator
import com.suhrud.docsy.reasoning.SubjectMark
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class ParsedMarksheet(
    val board: String?,
    val gradeClass: String?, // "10th", "12th", "B.Tech", "Semester 1", etc.
    val studentName: String?,
    val rollNumber: String?,
    val dob: String?,
    val subjects: List<SubjectMark>,
    val printedPercentage: BigDecimal?,
    val sgpa: String?,
    val cgpa: String?,
    val resultStatus: String?,
    val isLowConfidence: Boolean,
    val sourceIsCv: Boolean = false
) {
    val isClass12: Boolean
        get() = gradeClass == "12th" || (gradeClass == null && subjects.isNotEmpty())
}

object MarksheetParser {

    private val SUBJECT_KEYWORDS = listOf(
        "english", "hindi", "mathematics", "maths", "m4thematics", "physics", "chemistry",
        "biology", "computer science", "computer sci", "economics", "accountancy", "business studies",
        "history", "geography", "political science", "physical education", "physical edu", "science", "social science",
        "information technology", "sanskrit", "data structures", "algorithms", "dbms", "operating systems",
        "computer networks", "thermodynamics", "circuit theory", "digital logic", "microprocessors",
        "financial accounting", "cost accounting", "corporate law", "taxation", "anatomy", "physiology",
        "biochemistry", "pathology", "pharmacology", "general studies", "quantitative aptitude"
    )

    fun parse(text: String, isCvSource: Boolean = false): ParsedMarksheet {
        val cleanText = text.lowercase(Locale.ROOT)

        // Class / Degree / Semester detection
        val gradeClass = when {
            cleanText.contains("senior secondary") || cleanText.contains("class xii") ||
                    cleanText.contains("class 12") || cleanText.contains("12th") ||
                    cleanText.contains("higher secondary") || cleanText.contains("hsc") ||
                    cleanText.contains("senior school certificate") -> "12th"

            cleanText.contains("secondary school") || cleanText.contains("class x") ||
                    cleanText.contains("class 10") || cleanText.contains("10th") ||
                    cleanText.contains("ssc") || cleanText.contains("secondary school examination") -> "10th"

            cleanText.contains("b.tech") || cleanText.contains("bachelor of technology") || cleanText.contains("b.e.") -> "B.Tech"
            cleanText.contains("b.sc") || cleanText.contains("bachelor of science") -> "B.Sc"
            cleanText.contains("b.com") || cleanText.contains("bachelor of commerce") -> "B.Com"
            cleanText.contains("m.tech") || cleanText.contains("master of technology") -> "M.Tech"
            cleanText.contains("mba") || cleanText.contains("master of business administration") -> "MBA"
            cleanText.contains("mbbs") -> "MBBS"
            cleanText.contains("diploma") -> "Diploma"
            cleanText.contains("semester 1") || cleanText.contains("1st sem") -> "Semester 1"
            cleanText.contains("semester 2") || cleanText.contains("2nd sem") -> "Semester 2"
            cleanText.contains("semester 3") || cleanText.contains("3rd sem") -> "Semester 3"
            cleanText.contains("semester 4") || cleanText.contains("4th sem") -> "Semester 4"
            cleanText.contains("semester 5") || cleanText.contains("5th sem") -> "Semester 5"
            cleanText.contains("semester 6") || cleanText.contains("6th sem") -> "Semester 6"
            cleanText.contains("semester 7") || cleanText.contains("7th sem") -> "Semester 7"
            cleanText.contains("semester 8") || cleanText.contains("8th sem") -> "Semester 8"

            else -> if (isCvSource) "12th" else null
        }

        // Board / University / Institution detection
        val board = when {
            cleanText.contains("cbse") || cleanText.contains("central board of secondary education") -> "CBSE"
            cleanText.contains("icse") || cleanText.contains("cisce") || cleanText.contains("council for the indian school") -> "ICSE"
            cleanText.contains("jee main") || cleanText.contains("nta score") -> "NTA / JEE Main"
            cleanText.contains("jee advanced") -> "IIT / JEE Advanced"
            cleanText.contains("neet") -> "NTA / NEET"
            cleanText.contains("gate scorecard") || cleanText.contains("gate 20") -> "IIT / GATE"
            cleanText.contains("upsc") || cleanText.contains("union public service commission") -> "UPSC"
            cleanText.contains("vtu") || cleanText.contains("visvesvaraya technological university") -> "VTU"
            cleanText.contains("mumbai university") || cleanText.contains("university of mumbai") -> "Mumbai University"
            cleanText.contains("delhi university") || cleanText.contains("university of delhi") -> "Delhi University"
            cleanText.contains("anna university") -> "Anna University"
            cleanText.contains("aktu") || cleanText.contains("dr. a.p.j. abdul kalam technical university") -> "AKTU"
            cleanText.contains("sppu") || cleanText.contains("savitribai phule pune university") -> "SPPU"
            cleanText.contains("state board") || cleanText.contains("board of secondary education") -> "State Board"
            else -> null
        }

        // Student Name: Match specific multi-word labels first
        val rawName = extractRegexGroup(
            text,
            Regex("""(?:name of candidate|candidate's name|candidate name|student's name|student name|name of student|name of the student|name)[\s.:/\-]+([A-Za-z\s]{3,35})""", RegexOption.IGNORE_CASE)
        )
        val cutName = rawName?.split(Regex("""(?i)\b(father|mother|guardian|roll|date|dob|school|sub|code|subject)\b"""))?.firstOrNull()?.trim()
        val studentName = if (!cutName.isNullOrBlank() && cutName.length >= 3) NameMatcher.normalizeName(cutName).uppercase() else null

        // Roll Number / Application Number / Seat Number
        val rollNumber = extractRegexGroup(
            text,
            Regex("""(?:roll\s*(?:no|number)?|rollnum|seat\s*(?:no|number)?|application\s*(?:no|number)?|registration\s*(?:no|number)?|reg\s*no|index\s*no|enrollment\s*no|अनुक्रमांक)[\s.:/\-]*([A-Za-z0-9\-]{5,20})""", RegexOption.IGNORE_CASE)
        ) ?: extractRegexGroup(
            text,
            Regex("""\b([1-9][0-9]{6,11})\b""") // Standalone 7..12 digit roll number on CBSE/State board certificates
        )

        // Date of Birth
        val dob = extractRegexGroup(
            text,
            Regex("""(?:dob|date of birth)[\s:]+([0-9]{1,2}[\/\.\-][0-9]{1,2}[\/\.\-][0-9]{2,4})""", RegexOption.IGNORE_CASE)
        )

        // Result status
        val resultStatus = when {
            cleanText.contains("passed") || cleanText.contains("pass") -> "PASS"
            cleanText.contains("promoted") -> "PROMOTED"
            cleanText.contains("qualified") -> "QUALIFIED"
            cleanText.contains("failed") || cleanText.contains("fail") || cleanText.contains("essential repeat") -> "FAIL"
            cleanText.contains("compartment") -> "COMPARTMENT"
            else -> null
        }

        // SGPA & CGPA
        val sgpa = extractRegexGroup(text, Regex("""(?i)\bS\.?\s?G\.?\s?P\.?\s?A\.?\b\s*[:=\-]?\s*(\d{1,2}(?:\.\d{1,3})?)"""))
        val cgpa = extractRegexGroup(text, Regex("""(?i)\bC\.?\s?G\.?\s?P\.?\s?A\.?\b\s*[:=\-]?\s*(\d{1,2}(?:\.\d{1,3})?)"""))

        // Printed percentage
        val printedPct = extractPrintedPercentage(text)

        // Parse subject rows if not CV source
        val subjects = if (!isCvSource) parseSubjects(text) else emptyList()

        val isLowConfidence = !isCvSource && (
            (cleanText.contains("statement of marks") || cleanText.contains("marksheet")) &&
            (subjects.isEmpty() || subjects.any { it.marksObtained > it.maxMarks })
        )

        return ParsedMarksheet(
            board = board,
            gradeClass = gradeClass,
            studentName = studentName,
            rollNumber = rollNumber,
            dob = dob,
            subjects = subjects,
            printedPercentage = printedPct,
            sgpa = sgpa,
            cgpa = cgpa,
            resultStatus = resultStatus,
            isLowConfidence = isLowConfidence,
            sourceIsCv = isCvSource
        )
    }

    private fun parseSubjects(text: String): List<SubjectMark> {
        val lines = text.lines()
        val list = mutableListOf<SubjectMark>()

        for (line in lines) {
            val lowerLine = line.lowercase(Locale.ROOT).trim()
            if (lowerLine.isBlank()) continue

            // 1. Keyword-based matching
            val matchedKeyword = SUBJECT_KEYWORDS.firstOrNull { lowerLine.contains(it) }

            if (matchedKeyword != null) {
                val allNumbers = Regex("""\b\d{1,3}\b""").findAll(line).mapNotNull {
                    it.value.toIntOrNull()
                }.toList()

                if (allNumbers.isNotEmpty()) {
                    val marksNumbers = if (allNumbers.size >= 3 && allNumbers[0] in 1..999 && allNumbers[1] in 0..100) {
                        allNumbers.drop(1)
                    } else if (allNumbers.size >= 2 && line.trim().startsWith(allNumbers[0].toString()) && allNumbers[0] in 1..999 && allNumbers[1] in 0..100) {
                        allNumbers.drop(1)
                    } else {
                        allNumbers
                    }.filter { it in 0..200 || it == 500 }

                    if (marksNumbers.isNotEmpty()) {
                        val obtained = BigDecimal(marksNumbers[0])
                        val maxMarks = if (marksNumbers.size > 1 && marksNumbers[1] in listOf(50, 70, 80, 100)) {
                            BigDecimal(marksNumbers[1])
                        } else {
                            BigDecimal("100")
                        }

                        val formattedSubName = when (matchedKeyword) {
                            "m4thematics" -> "Mathematics"
                            "computer sci" -> "Computer Science"
                            "physical edu" -> "Physical Education"
                            "dbms" -> "DBMS"
                            else -> matchedKeyword.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                        }

                        if (list.none { it.subjectName.equals(formattedSubName, ignoreCase = true) }) {
                            list.add(SubjectMark(subjectName = formattedSubName, marksObtained = obtained, maxMarks = maxMarks))
                        }
                    }
                }
            } else {
                // 2. Code + Subject Name + Marks pattern (e.g. "041 MATHEMATICS STANDARD 080 100 A1")
                val cbseMatch = Regex("""\b(\d{3})\s+([A-Za-z &.\-:/()]{3,35})\s+(\d{2,3})\b""").find(line)
                if (cbseMatch != null) {
                    val subName = cbseMatch.groupValues[2].trim().uppercase()
                    val score = cbseMatch.groupValues[3].toIntOrNull()
                    if (score != null && score in 0..100 && subName.count { it.isLetter() } >= 3 &&
                        !subName.contains("TOTAL") && !subName.contains("ROLL") && !subName.contains("STATEMENT")
                    ) {
                        if (list.none { it.subjectName.equals(subName, ignoreCase = true) }) {
                            list.add(SubjectMark(subjectName = subName, marksObtained = BigDecimal(score), maxMarks = BigDecimal(100)))
                        }
                    }
                }
            }
        }

        return list
    }

    private fun extractPrintedPercentage(text: String): BigDecimal? {
        val match = Regex("""(?:percentage|aggregate|agg\s*%|pct|score|scored)[\s:]+([0-9]{2}(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("""([0-9]{2}(?:\.[0-9]{1,2})?)\s*%""").find(text)

        return match?.groupValues?.get(1)?.toBigDecimalOrNull()
    }

    private fun extractRegexGroup(text: String, regex: Regex): String? {
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }

    fun formatAnswer(marksheet: ParsedMarksheet): String {
        if (marksheet.sourceIsCv) {
            val pctStr = marksheet.printedPercentage?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "N/A"
            return "According to your CV, your 12th score is $pctStr%. (Note: Official marksheet document not found)."
        }

        if (marksheet.isLowConfidence) {
            val tableText = if (marksheet.subjects.isNotEmpty()) {
                "\nSubject Marks Table:\n" + marksheet.subjects.joinToString("\n") { "${it.subjectName}: ${it.marksObtained}/${it.maxMarks}" }
            } else ""
            return "12th marksheet values are low confidence.$tableText\nPlease confirm subject marks to calculate exact percentage."
        }

        if (marksheet.subjects.isEmpty()) {
            return if (marksheet.printedPercentage != null) {
                "Your percentage is ${marksheet.printedPercentage.setScale(2, RoundingMode.HALF_UP).toPlainString()}%"
            } else if (marksheet.cgpa != null || marksheet.sgpa != null) {
                "Your SGPA/CGPA is ${marksheet.cgpa ?: marksheet.sgpa}."
            } else {
                "Marksheet found, but subject marks could not be extracted automatically."
            }
        }

        val allCalc = DocsyCalculator.calculateMarksheetPercentage(marksheet.subjects, bestFiveOnly = false)

        val sb = StringBuilder()
        val totalObtStr = allCalc.totalObtained.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val totalMaxStr = allCalc.totalMaximum.setScale(2, RoundingMode.HALF_UP).toPlainString()

        if (marksheet.subjects.size > 5) {
            val best5Calc = DocsyCalculator.calculateMarksheetPercentage(marksheet.subjects, bestFiveOnly = true)
            val b5ObtStr = best5Calc.totalObtained.setScale(2, RoundingMode.HALF_UP).toPlainString()
            val b5MaxStr = best5Calc.totalMaximum.setScale(2, RoundingMode.HALF_UP).toPlainString()
            sb.append("Your percentage is ${best5Calc.percentage.toPlainString()}%\n")
            sb.append("Best Five: $b5ObtStr out of $b5MaxStr = ${best5Calc.percentage.toPlainString()}%\n")
            sb.append("All Subjects (${marksheet.subjects.size}): $totalObtStr out of $totalMaxStr = ${allCalc.percentage.toPlainString()}%")
        } else {
            sb.append("Your percentage is ${allCalc.percentage.toPlainString()}%\n")
            sb.append("Working: $totalObtStr out of $totalMaxStr = ${allCalc.percentage.toPlainString()}%")
        }

        if (marksheet.printedPercentage != null) {
            val printed = marksheet.printedPercentage.setScale(2, RoundingMode.HALF_UP)
            sb.append("\nPrinted Percentage: ${printed.toPlainString()}%")
            if (printed.compareTo(allCalc.percentage) != 0) {
                sb.append(" (Note: Mismatch with calculated value ${allCalc.percentage.toPlainString()}%)")
            }
        }

        if (marksheet.cgpa != null) {
            sb.append("\nCGPA: ${marksheet.cgpa}")
        }
        if (marksheet.sgpa != null) {
            sb.append("\nSGPA: ${marksheet.sgpa}")
        }
        if (marksheet.resultStatus != null) {
            sb.append("\nResult: ${marksheet.resultStatus}")
        }

        return sb.toString()
    }
}
