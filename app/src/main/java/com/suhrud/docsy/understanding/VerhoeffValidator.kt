package com.suhrud.docsy.understanding

object VerhoeffValidator {

    private val d = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 2, 3, 4, 0, 6, 7, 8, 9, 5),
        intArrayOf(2, 3, 4, 0, 1, 7, 8, 9, 5, 6),
        intArrayOf(3, 4, 0, 1, 2, 8, 9, 5, 6, 7),
        intArrayOf(4, 0, 1, 2, 3, 9, 5, 6, 7, 8),
        intArrayOf(5, 6, 7, 8, 9, 0, 1, 2, 3, 4),
        intArrayOf(6, 7, 8, 9, 5, 1, 2, 3, 4, 0),
        intArrayOf(7, 8, 9, 5, 6, 2, 3, 4, 0, 1),
        intArrayOf(8, 9, 5, 6, 7, 3, 4, 0, 1, 2),
        intArrayOf(9, 5, 6, 7, 8, 4, 0, 1, 2, 3)
    )

    private val p = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
        intArrayOf(1, 5, 7, 6, 2, 8, 3, 0, 9, 4),
        intArrayOf(5, 8, 0, 3, 7, 9, 6, 1, 4, 2),
        intArrayOf(8, 9, 1, 6, 0, 4, 3, 5, 2, 7),
        intArrayOf(9, 4, 5, 3, 1, 2, 6, 8, 7, 0),
        intArrayOf(4, 2, 8, 6, 5, 7, 3, 9, 0, 1),
        intArrayOf(2, 7, 9, 3, 8, 0, 6, 4, 1, 5),
        intArrayOf(7, 0, 4, 6, 9, 1, 3, 2, 5, 8)
    )

    fun validateAadhaar(aadhaar: String): Boolean {
        val clean = aadhaar.replace(Regex("""[\s\-]"""), "")
        if (clean.length != 12 || !clean.all { it.isDigit() }) {
            return false
        }
        return validateVerhoeff(clean)
    }

    fun validateVerhoeff(numStr: String): Boolean {
        var c = 0
        val myArray = reverse(numStr)

        for (i in myArray.indices) {
            val digit = Character.getNumericValue(myArray[i])
            c = d[c][p[i % 8][digit]]
        }
        return c == 0
    }

    private fun reverse(s: String): String {
        return StringBuilder(s).reverse().toString()
    }
}
