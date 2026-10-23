package com.suhrud.docsy.retrieval

enum class QueryClass {
    CHITCHAT,
    PHONE_QUERY,
    GENERAL_QUESTION,
    AMBIGUOUS
}

/**
 * First-stage classifier determining if user input is CHITCHAT, PHONE_QUERY, GENERAL_QUESTION, or AMBIGUOUS.
 * Satisfies ITEM C requirements.
 */
object TopLevelQueryClassifier {

    fun classify(rawQuery: String): QueryClass {
        val normalized = QueryNormalizer.normalize(rawQuery)
        val clean = normalized.lowercase().trim()
        val originalLower = rawQuery.lowercase().trim()

        if (clean.isBlank()) return QueryClass.CHITCHAT

        // 1. Ambiguous check: vague device/file lookup without specific target
        if (isAmbiguous(clean, originalLower)) {
            return QueryClass.AMBIGUOUS
        }

        // 2. Chitchat check (greetings, thanks, feelings, jokes, identity)
        if (isChitchat(clean, originalLower)) {
            return QueryClass.CHITCHAT
        }

        // 3. General Question check (general knowledge or writing) - check before phone query if no personal marker
        if (isGeneralQuestion(clean, originalLower)) {
            return QueryClass.GENERAL_QUESTION
        }

        // 4. Phone Query check (explicit personal/device keywords with word boundaries)
        if (isPhoneQuery(clean, originalLower)) {
            return QueryClass.PHONE_QUERY
        }

        // Default fallback: if unclassified, default to PHONE_QUERY so retrieval handles it
        return QueryClass.PHONE_QUERY
    }

    private fun isAmbiguous(clean: String, original: String): Boolean {
        val ambiguousQueries = setOf(
            "my certificate", "my document", "find file", "search file",
            "my paper", "show document", "search certificate", "find document"
        )
        return clean in ambiguousQueries || original in ambiguousQueries
    }

    private fun isChitchat(clean: String, original: String): Boolean {
        val targets = listOf(clean, original)

        // Greetings
        if (targets.any { t -> t.matches(Regex("""^(hi|hello|hey|namaste|good morning|good afternoon|good evening|yo|sup|ssup|hola|howdy|greetings)\b.*""")) }) return true

        // Gratitude
        val gratitude = listOf("thank", "thanks", "shukriya", "dhanyawad", "thx", "appreciate")
        if (gratitude.any { g -> targets.any { t -> t.contains(g) } }) return true

        // Emergency & Self-harm
        val emergencyKeywords = listOf("emergency", "911", "112", "police", "ambulance", "suicide", "self harm", "hurt myself", "kill myself", "want to die")
        if (emergencyKeywords.any { k -> targets.any { t -> t.contains(k) } }) return true

        // Coin flip, dice roll, age, version
        val chitchatKeywords = listOf(
            "coin", "toss", "heads or tails", "dice", "roll die", "roll a die", "throw a dice",
            "how old", "your age", "what age", "when were you created", "app version", "what version", "which version"
        )
        if (chitchatKeywords.any { k -> targets.any { t -> t.contains(k) } }) return true

        // Feelings, How are you, Who are you, Jokes, Opinions, Casual farewells
        val phrases = listOf(
            "how are you", "kaise ho", "kya haal", "hows life", "how is life", "how's life",
            "who are you", "what are you", "tum kaun ho", "kya ho tum", "who made you", "are you ai", "are you real",
            "joke", "funny", "laugh",
            "like music", "favorite color", "are you single", "do you love me",
            "awesome", "cool", "nice to meet", "good night", "bye", "alvida", "see ya"
        )
        if (phrases.any { p -> targets.any { t -> t.contains(p) } }) return true

        return false
    }

    private fun isPhoneQuery(clean: String, original: String): Boolean {
        val phoneKeywords = listOf(
            "my", "mine", "phone", "device", "mobile", "storage", "battery", "ram",
            "file", "files", "document", "documents", "pdf", "image", "photo", "photos",
            "video", "videos", "song", "songs", "sms", "message", "messages",
            "call", "calls", "contact", "contacts", "step", "steps", "walk", "walked",
            "marksheet", "certificate", "aadhaar", "pan", "passport", "voter", "license",
            "resume", "biodata", "bill", "receipt", "wifi", "bluetooth", "kernel", "build number",
            "android version", "cpu", "chipset", "camera", "sensor", "sensors", "clock",
            "time", "date", "dob", "birth", "calculate", "sum", "math", "profile", "apps",
            "12th", "10th", "xii", "percent", "percentage", "roll number"
        )

        for (kw in phoneKeywords) {
            val regex = Regex("""\b$kw\b""")
            if (regex.containsMatchIn(clean) || regex.containsMatchIn(original)) return true
        }

        // Hinglish phone terms
        val hinglishPhone = listOf(
            "mera", "meri", "mere", "apna", "apni", "anktalika", "bijli", "janamdin",
            "tareekh", "waqt", "samay", "kahan", "kitne", "kitna", "kitni"
        )
        for (hk in hinglishPhone) {
            val regex = Regex("""\b$hk\b""")
            if (regex.containsMatchIn(clean) || regex.containsMatchIn(original)) return true
        }

        return false
    }

    private fun isGeneralQuestion(clean: String, original: String): Boolean {
        val generalPrefixes = listOf(
            "explain", "write", "what is", "how does", "how do", "tell me a story",
            "summarize", "how to", "why is", "translate", "who wrote", "list top",
            "what are the", "essay on", "poem about", "definition of", "history of",
            "describe", "meaning of"
        )
        val hasPrefix = generalPrefixes.any { clean.startsWith(it) || original.startsWith(it) }
        if (!hasPrefix) return false

        // If it explicitly asks about personal items ("my date of birth", "my father's name"), it's a phone query
        val personalMarkers = listOf("my ", "mine", "mera ", "meri ", "mere ", "phone", "device")
        if (personalMarkers.any { clean.contains(it) || original.contains(it) }) {
            return false
        }
        return true
    }
}
