package com.suhrud.docsy

import android.graphics.Rect
import com.suhrud.docsy.extractor.LayoutModelAnalyzer
import com.suhrud.docsy.extractor.RawTextUnit
import com.suhrud.docsy.understanding.LayoutMarksheetExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LayoutAwareMarksheetTest {

    private fun u(text: String, l: Int, t: Int, r: Int) = RawTextUnit(text, Rect(l, t, r, t + 30))

    /** Photo-sized text: 40 px tall boxes on an 80 px row pitch, like OCR output for a photographed certificate. */
    private fun v(text: String, l: Int, t: Int, r: Int) = RawTextUnit(text, Rect(l, t, r, t + 40))

    /** A semester marksheet: header block, a grade table, and an SGPA label with its value beside it. */
    private fun semesterSheet(): List<RawTextUnit> = listOf(
        u("Name: Asha Rao", 50, 40, 400),
        u("Roll No", 50, 100, 170), u("20CS1045", 220, 100, 360),

        u("Course Code", 50, 200, 180), u("Course Title", 220, 200, 420), u("Credits", 460, 200, 540),
        u("Grade", 580, 200, 650), u("Grade Point", 690, 200, 800),

        u("CS101", 50, 250, 180), u("Data Structures", 220, 250, 420), u("4", 470, 250, 520), u("A", 590, 250, 630), u("9", 710, 250, 740),
        u("CS102", 50, 300, 180), u("Discrete Maths", 220, 300, 420), u("3", 470, 300, 520), u("B", 590, 300, 630), u("8", 710, 300, 740),
        u("CS103", 50, 350, 180), u("Operating Systems", 220, 350, 420), u("3", 470, 350, 520), u("O", 590, 350, 630), u("10", 710, 350, 740),

        u("SGPA", 50, 450, 150), u("9.00", 220, 450, 300),
        u("Result", 50, 500, 150), u("PASS", 220, 500, 300)
    )

    @Test
    fun columnsAreAlignedAcrossRows() {
        val page = LayoutModelAnalyzer.analyzeUnits(semesterSheet(), 1)
        val ds = page.tables.first { it.text == "Data Structures" }
        // Credits, grade and grade point sit in the same columns as their headers.
        val credits = page.tables.first { it.text == "Credits" }.col
        val grade = page.tables.first { it.text == "Grade" }.col
        assertEquals("4", page.tables.first { it.row == ds.row && it.col == credits }.text)
        assertEquals("A", page.tables.first { it.row == ds.row && it.col == grade }.text)
    }

    @Test
    fun labelValuePairsAreFoundBesideTheLabel() {
        val page = LayoutModelAnalyzer.analyzeUnits(semesterSheet(), 1)
        assertEquals("20CS1045", page.keyValues.firstOrNull { it.key.equals("Roll No", true) }?.value)
        assertEquals("9.00", page.keyValues.firstOrNull { it.key.equals("SGPA", true) }?.value)
    }

    @Test
    fun marksheetFieldsAndSubjectsComeFromLayout() {
        val page = LayoutModelAnalyzer.analyzeUnits(semesterSheet(), 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)

        assertEquals("20CS1045", sheet.field("rollNo"))
        assertEquals("9.00", sheet.field("sgpa"))
        assertEquals("Asha Rao", sheet.field("studentName"))
        assertEquals("PASS", sheet.field("result"))

        assertEquals(3, sheet.subjects.size)
        val ds = sheet.subjects.first { it.name == "Data Structures" }
        assertEquals("A", ds.grade)
        assertEquals("4", ds.credits)
        assertEquals("9", ds.gradePoint)
    }

    @Test
    fun sgpaIsDerivedFromCreditsWhenNotPrinted() {
        val units = semesterSheet().filterNot { it.text == "SGPA" || it.text == "9.00" }
        val page = LayoutModelAnalyzer.analyzeUnits(units, 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)
        // (4*9 + 3*8 + 3*10) / 10 = 9.00
        assertEquals("9.00", sheet.field("sgpa"))
        assertTrue(sheet.fields.first { it.key == "sgpa" }.derived)
    }

    @Test
    fun marksTableWithTotalsColumnIsRead() {
        val units = listOf(
            u("Subject", 50, 100, 200), u("Marks Obtained", 240, 100, 400), u("Maximum Marks", 440, 100, 600),
            u("English", 50, 150, 200), u("091", 260, 150, 330), u("100", 470, 150, 530),
            u("Mathematics", 50, 200, 200), u("095", 260, 200, 330), u("100", 470, 200, 530),
            u("Grand Total", 50, 250, 200), u("186", 260, 250, 330), u("200", 470, 250, 530)
        )
        val page = LayoutModelAnalyzer.analyzeUnits(units, 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)
        assertEquals(2, sheet.subjects.size) // "Grand Total" is not a subject
        assertNotNull(sheet.subjects.firstOrNull { it.name == "English" && it.marks == "91" && it.maxMarks == "100" })
    }

    /** Word positions taken from the real "BTech_2nd_Sem.pdf" (PDF text layer, scaled x2 like NativePdfText does). */
    private fun realBtechSheet(): List<RawTextUnit> = listOf(
        RawTextUnit("GEC AUTONOMOUS COLLEGE", Rect(374, 148, 849, 180)),
        RawTextUnit("STUDENT RESULT SUMMARY", Rect(425, 232, 798, 258)),
        RawTextUnit("Examination: B.Tech Second Semester", Rect(144, 335, 560, 359)),
        RawTextUnit("Examination Type: Regular", Rect(720, 335, 1008, 359)),
        RawTextUnit("Registration Number: 2301292380", Rect(144, 398, 510, 422)),
        RawTextUnit("Year: 1st", Rect(720, 398, 813, 422)),
        RawTextUnit("Semester: 2nd", Rect(853, 398, 1009, 422)),
        RawTextUnit("Student Name: SUHRUD ABHINANDAN", Rect(144, 462, 577, 486)),
        RawTextUnit("Branch: B.Tech", Rect(864, 462, 1026, 486)),
        RawTextUnit("Sl.", Rect(160, 575, 192, 601)),
        RawTextUnit("Subject Code", Rect(231, 575, 398, 601)),
        RawTextUnit("Subject Name", Rect(514, 575, 686, 601)),
        RawTextUnit("T/P", Rect(791, 575, 831, 601)),
        RawTextUnit("Credit", Rect(854, 575, 929, 601)),
        RawTextUnit("Grade", Rect(992, 575, 1067, 601)),
        RawTextUnit("No.", Rect(155, 605, 197, 631)),
        RawTextUnit("1", Rect(169, 657, 182, 681)),
        RawTextUnit("23BT0TBS202", Rect(242, 656, 387, 678)),
        RawTextUnit("APPLIED CHEMISTRY", Rect(426, 656, 654, 678)),
        RawTextUnit("T", Rect(804, 657, 818, 681)),
        RawTextUnit("3", Rect(885, 657, 898, 681)),
        RawTextUnit("O", Rect(1020, 657, 1039, 681)),
        RawTextUnit("2", Rect(169, 707, 182, 731)),
        RawTextUnit("23BT0TES201", Rect(242, 706, 387, 728)),
        RawTextUnit("BASIC CIVIL ENGINEERING", Rect(426, 706, 713, 728)),
        RawTextUnit("T", Rect(804, 707, 818, 731)),
        RawTextUnit("2", Rect(885, 707, 898, 731)),
        RawTextUnit("D", Rect(1021, 707, 1038, 731)),
        RawTextUnit("FUNDAMENTALS OF DIGITAL", Rect(426, 751, 730, 773)),
        RawTextUnit("3", Rect(169, 766, 182, 790)),
        RawTextUnit("23BT0TES202", Rect(242, 765, 387, 787)),
        RawTextUnit("T", Rect(804, 766, 818, 790)),
        RawTextUnit("3", Rect(885, 766, 898, 790)),
        RawTextUnit("O", Rect(1020, 766, 1039, 790)),
        RawTextUnit("LOGIC DESIGN", Rect(426, 780, 584, 802)),
        RawTextUnit("ENGLISH PROFICIENCY AND", Rect(426, 819, 731, 841)),
        RawTextUnit("4", Rect(169, 835, 182, 859)),
        RawTextUnit("23BT0THS201", Rect(242, 834, 387, 856)),
        RawTextUnit("COMMUNICATION SKILLS", Rect(426, 848, 695, 870)),
        RawTextUnit("T", Rect(804, 835, 818, 859)),
        RawTextUnit("3", Rect(885, 835, 898, 859)),
        RawTextUnit("O", Rect(1020, 835, 1039, 859)),
        RawTextUnit("5", Rect(169, 894, 182, 918)),
        RawTextUnit("23BT0TBS201", Rect(242, 893, 387, 915)),
        RawTextUnit("MATHEMATICS-II", Rect(426, 893, 602, 915)),
        RawTextUnit("T", Rect(804, 894, 818, 918)),
        RawTextUnit("3", Rect(885, 894, 898, 918)),
        RawTextUnit("O", Rect(1020, 894, 1039, 918)),
        RawTextUnit("STATISTICS WITH PYTHON", Rect(426, 938, 709, 960)),
        RawTextUnit("6", Rect(169, 954, 182, 978)),
        RawTextUnit("23BT0TES203", Rect(242, 953, 387, 975)),
        RawTextUnit("PROGRAMMING", Rect(426, 967, 597, 989)),
        RawTextUnit("T", Rect(804, 954, 818, 978)),
        RawTextUnit("3", Rect(885, 954, 898, 978)),
        RawTextUnit("O", Rect(1020, 954, 1039, 978)),
        RawTextUnit("ENGINEERING GRAPHICS WITH", Rect(426, 1007, 763, 1029)),
        RawTextUnit("7", Rect(169, 1022, 182, 1046)),
        RawTextUnit("23BT0PES201", Rect(242, 1021, 387, 1043)),
        RawTextUnit("P", Rect(803, 1022, 819, 1046)),
        RawTextUnit("1", Rect(885, 1022, 898, 1046)),
        RawTextUnit("E", Rect(1021, 1022, 1037, 1046)),
        RawTextUnit("AUTOCAD LAB", Rect(426, 1036, 580, 1058)),
        RawTextUnit("8", Rect(169, 1082, 182, 1106)),
        RawTextUnit("23BT0PES202", Rect(242, 1081, 387, 1103)),
        RawTextUnit("FDLD LAB", Rect(426, 1081, 531, 1103)),
        RawTextUnit("P", Rect(803, 1082, 819, 1106)),
        RawTextUnit("1", Rect(885, 1082, 898, 1106)),
        RawTextUnit("E", Rect(1021, 1082, 1037, 1106)),
        RawTextUnit("9", Rect(169, 1132, 182, 1156)),
        RawTextUnit("23BT0PES203", Rect(242, 1131, 387, 1153)),
        RawTextUnit("PYTHON PROGRAMMING LAB", Rect(426, 1131, 742, 1153)),
        RawTextUnit("P", Rect(803, 1132, 819, 1156)),
        RawTextUnit("1", Rect(885, 1132, 898, 1156)),
        RawTextUnit("E", Rect(1021, 1132, 1037, 1156)),
        RawTextUnit("10", Rect(162, 1182, 189, 1206)),
        RawTextUnit("23BT0PHS201", Rect(241, 1181, 388, 1203)),
        RawTextUnit("COMMUNICATIVE ENGLISH LAB", Rect(426, 1181, 761, 1203)),
        RawTextUnit("P", Rect(803, 1182, 819, 1206)),
        RawTextUnit("1", Rect(885, 1182, 898, 1206)),
        RawTextUnit("E", Rect(1021, 1182, 1037, 1206)),
        RawTextUnit("11", Rect(163, 1232, 188, 1256)),
        RawTextUnit("23BT0PES204", Rect(242, 1231, 387, 1253)),
        RawTextUnit("POWER BI", Rect(426, 1231, 535, 1253)),
        RawTextUnit("P", Rect(803, 1232, 819, 1256)),
        RawTextUnit("1", Rect(885, 1232, 898, 1256)),
        RawTextUnit("E", Rect(1021, 1232, 1037, 1256)),
        RawTextUnit("Total Credits", Rect(693, 1281, 838, 1305)),
        RawTextUnit("22", Rect(877, 1282, 906, 1308)),
        RawTextUnit("SGPA:9.32", Rect(969, 1282, 1090, 1306))
    )

    @Test
    fun realBtechSheet_readsAllSubjectsWrappedNamesAndSgpa() {
        val page = LayoutModelAnalyzer.analyzeUnits(realBtechSheet(), 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)

        assertEquals("2301292380", sheet.field("rollNo"))
        assertEquals("SUHRUD ABHINANDAN", sheet.field("studentName"))
        assertEquals("9.32", sheet.field("sgpa"))
        assertEquals("22", sheet.field("totalCredits"))
        assertEquals("2nd", sheet.field("semester"))

        assertEquals(11, sheet.subjects.size)
        // Names that wrap over two lines, centred next to the serial number, still end up in the right row.
        val fdld = sheet.subjects.first { it.code == "23BT0TES202" }
        assertEquals("FUNDAMENTALS OF DIGITAL LOGIC DESIGN", fdld.name)
        assertEquals("3", fdld.credits)
        assertEquals("O", fdld.grade)
        assertEquals("ENGLISH PROFICIENCY AND COMMUNICATION SKILLS", sheet.subjects.first { it.code == "23BT0THS201" }.name)
        // The credits add up to the printed total, so no row was lost or merged.
        assertEquals(22, sheet.subjects.sumOf { it.credits!!.toInt() })
        // The footer ("Total Credits 22 SGPA") is not a subject.
        assertTrue(sheet.subjects.none { it.name.contains("Total", ignoreCase = true) })
    }

    @Test
    fun cbseCertificate_readsSubjectsRollNoAndResult() {
        val units = listOf(
           v("Roll No. 12119554", 200, 100, 700),

           v("MARKS OBTAINED", 1150, 700, 1900),
           v("SUB. CODE", 150, 800, 300),v("SUBJECT", 450, 800, 700),v("THEORY", 1150, 800, 1300),
           v("IA/ PR.", 1380, 800, 1500),v("TOTAL", 1560, 800, 1650),v("TOTAL (IN WORDS)", 1700, 800, 2000),
           v("POSITIONAL GRADE", 2100, 800, 2300),

           v("184", 160, 880, 240),v("ENGLISH LNG & LIT.", 450, 880, 700),v("074", 1170, 880, 1260),v("020", 1400, 880, 1470),
           v("094", 1570, 880, 1640),v("NINETY FOUR", 1710, 880, 1900),v("A1", 2170, 880, 2230),
           v("085", 160, 960, 240),v("HINDI COURSE-B", 450, 960, 700),v("058", 1170, 960, 1260),v("018", 1400, 960, 1470),
           v("076", 1570, 960, 1640),v("SEVENTY SIX", 1710, 960, 1900),v("C1", 2170, 960, 2230),
           v("041", 160, 1040, 240),v("MATHEMATICS STANDARD", 450, 1040, 700),v("074", 1170, 1040, 1260),v("019", 1400, 1040, 1470),
           v("093", 1570, 1040, 1640),v("NINETY THREE", 1710, 1040, 1900),v("A1", 2170, 1040, 2230),
           v("086", 160, 1120, 240),v("SCIENCE - THEORY", 450, 1120, 700),v("070", 1170, 1120, 1260),v("019", 1400, 1120, 1470),
           v("089", 1570, 1120, 1640),v("EIGHTY NINE", 1710, 1120, 1900),v("A1", 2170, 1120, 2230),
           v("087", 160, 1200, 240),v("SOCIAL SCIENCE", 450, 1200, 700),v("054", 1170, 1200, 1260),v("020", 1400, 1200, 1470),
           v("074", 1570, 1200, 1640),v("SEVENTY FOUR", 1710, 1200, 1900),v("C1", 2170, 1200, 2230),
           v("ADDITIONAL SUBJECT", 450, 1280, 800),
           v("402", 160, 1330, 240),v("INFORMATION TECHNOLOGY", 450, 1330, 800),v("030", 1170, 1330, 1260),v("046", 1400, 1330, 1470),
           v("076", 1570, 1330, 1640),v("SEVENTY SIX", 1710, 1330, 1900),v("C1", 2170, 1330, 2230),

           v("Result PASS", 200, 1500, 500)
        )
        val page = LayoutModelAnalyzer.analyzeUnits(units, 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)

        assertEquals("12119554", sheet.field("rollNo"))
        assertEquals("PASS", sheet.field("result"))
        assertEquals(6, sheet.subjects.size)
        val english = sheet.subjects.first { it.code == "184" }
        assertEquals("94", english.marks)      // "094" printed
        assertEquals("100", english.maxMarks)  // CBSE: no maximum column, every subject is out of 100
        assertEquals("A1", english.grade)
        assertEquals("INFORMATION TECHNOLOGY", sheet.subjects.first { it.code == "402" }.name)
    }

    @Test
    fun cbseSubjectNameIsRestoredFromTheSubjectCodeWhenOcrCannotReadIt() {
        val units = listOf(
            v("SUB. CODE", 150, 800, 300), v("SUBJECT", 450, 800, 700), v("TOTAL", 1560, 800, 1650), v("POSITIONAL GRADE", 2100, 800, 2300),
            v("184", 160, 880, 240), v("??", 450, 880, 520), v("094", 1570, 880, 1640), v("A1", 2170, 880, 2230),
            v("085", 160, 960, 240), v("HINDI COURSE-B", 450, 960, 700), v("076", 1570, 960, 1640), v("C1", 2170, 960, 2230),
            v("041", 160, 1040, 240), v("MATHEMATICS STANDARD", 450, 1040, 700), v("093", 1570, 1040, 1640), v("A1", 2170, 1040, 2230)
        )
        val page = LayoutModelAnalyzer.analyzeUnits(units, 1)
        val sheet = LayoutMarksheetExtractor.extract(listOf(page), page.fullText)
        assertEquals("ENGLISH LNG & LIT.", sheet.subjects.first { it.code == "184" }.name)
    }
}
