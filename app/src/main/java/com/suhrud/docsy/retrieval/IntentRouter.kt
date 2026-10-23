package com.suhrud.docsy.retrieval

import com.suhrud.docsy.tools.DateTimeTool
import com.suhrud.docsy.understanding.DocumentSynonymDictionary

/**
 * Multi-stage Intent Router with deterministic fast-path priority and collision resolution.
 * Satisfies Phase 9.1, 9.2, 9.4, and 9.6 in AUDIT.md.
 */
object IntentRouter {

    fun route(rawQuery: String): RoutingDecision {
        val normalized = QueryNormalizer.normalize(rawQuery)
        val clean = normalized.lowercase().trim()

        if (clean.isBlank()) {
            return RoutingDecision(
                outcome = RouterOutcome.UNRECOGNISED,
                intent = DocsyIntent.UNRECOGNISED,
                confidence = 0.0f,
                normalizedQuery = normalized
            )
        }

        if (isOutOfScopeQuery(clean)) {
            return RoutingDecision(RouterOutcome.OUT_OF_SCOPE, DocsyIntent.OUT_OF_SCOPE, 0.95f, normalizedQuery = normalized)
        }

        val queryClass = TopLevelQueryClassifier.classify(rawQuery)

        // ==========================================
        // STAGE 0: First-Stage Classification
        // ==========================================
        when (queryClass) {
            QueryClass.CHITCHAT -> {
                if (ChitchatEmbeddingEngine.isReady()) {
                    val match = ChitchatEmbeddingEngine.matchIntent(rawQuery)
                    if (match != null) {
                        val docsyIntent = when (match.intent) {
                            "greetings" -> DocsyIntent.GREETING
                            "how_are_you" -> DocsyIntent.HOW_ARE_YOU
                            "who_are_you" -> DocsyIntent.WHO_ARE_YOU
                            "gratitude" -> DocsyIntent.GRATITUDE
                            "farewell" -> DocsyIntent.FAREWELL
                            "joke" -> DocsyIntent.JOKE
                            else -> DocsyIntent.GENERAL_CHITCHAT
                        }
                        return RoutingDecision(
                            outcome = RouterOutcome.SMALL_TALK,
                            intent = docsyIntent,
                            confidence = match.confidence,
                            targetCategory = match.intent,
                            normalizedQuery = normalized
                        )
                    } else {
                        // Below threshold -> request rephrase
                        return RoutingDecision(
                            outcome = RouterOutcome.SMALL_TALK,
                            intent = DocsyIntent.CLARIFICATION_REQUIRED,
                            confidence = 0.0f,
                            targetCategory = "REPHRASE_REQUIRED",
                            normalizedQuery = normalized
                        )
                    }
                } else {
                    // Fallback before engine initialization
                    val intent = when {
                        isHowAreYou(clean) -> DocsyIntent.HOW_ARE_YOU
                        isWhoAreYou(clean) -> DocsyIntent.WHO_ARE_YOU
                        isGratitude(clean) -> DocsyIntent.GRATITUDE
                        isFarewell(clean) -> DocsyIntent.FAREWELL
                        clean.contains("joke") || clean.contains("funny") -> DocsyIntent.JOKE
                        else -> DocsyIntent.GREETING
                    }
                    val catName = when (intent) {
                        DocsyIntent.HOW_ARE_YOU -> "how_are_you"
                        DocsyIntent.WHO_ARE_YOU -> "who_are_you"
                        DocsyIntent.GRATITUDE -> "gratitude"
                        DocsyIntent.FAREWELL -> "farewell"
                        DocsyIntent.JOKE -> "joke"
                        else -> "greetings"
                    }
                    return RoutingDecision(RouterOutcome.SMALL_TALK, intent, 0.99f, targetCategory = catName, normalizedQuery = normalized)
                }
            }
            QueryClass.GENERAL_QUESTION -> {
                return RoutingDecision(
                    outcome = RouterOutcome.GENERAL_KNOWLEDGE,
                    intent = DocsyIntent.GENERAL_QUESTION,
                    confidence = 0.99f,
                    normalizedQuery = normalized
                )
            }
            QueryClass.AMBIGUOUS -> {
                return RoutingDecision(
                    outcome = RouterOutcome.NEEDS_CLARIFICATION,
                    intent = DocsyIntent.CLARIFICATION_REQUIRED,
                    confidence = 0.95f,
                    targetField = "Which document or certificate would you like me to look for?",
                    normalizedQuery = normalized
                )
            }
            QueryClass.PHONE_QUERY -> {
                // Continue to existing stages
            }
        }

        // ==========================================
        // STAGE 1: Small Talk (Fast Path)
        // ==========================================
        if (isGreeting(clean)) {
            return RoutingDecision(RouterOutcome.SMALL_TALK, DocsyIntent.GREETING, 0.99f, normalizedQuery = normalized)
        }
        if (isHowAreYou(clean)) {
            return RoutingDecision(RouterOutcome.SMALL_TALK, DocsyIntent.HOW_ARE_YOU, 0.99f, normalizedQuery = normalized)
        }
        if (isWhoAreYou(clean)) {
            return RoutingDecision(RouterOutcome.SMALL_TALK, DocsyIntent.WHO_ARE_YOU, 0.99f, normalizedQuery = normalized)
        }
        if (isGratitude(clean)) {
            return RoutingDecision(RouterOutcome.SMALL_TALK, DocsyIntent.GRATITUDE, 0.99f, normalizedQuery = normalized)
        }
        if (isFarewell(clean)) {
            return RoutingDecision(RouterOutcome.SMALL_TALK, DocsyIntent.FAREWELL, 0.99f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 2: User Profile (Fast Path)
        // ==========================================
        if (isUserNameQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.USER_NAME_QUERY, 0.95f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 3: Collision Resolution (Rule 9.6)
        // "videos count", "photos count", "musics" vs general storage
        // ==========================================
        if (isMediaCountQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.MEDIA_CATEGORY_COUNT, 0.95f, normalizedQuery = normalized)
        }
        if (isStorageSuperlativeQuery(clean)) {
            val intent = when {
                clean.contains("largest") || clean.contains("biggest") -> DocsyIntent.STORAGE_SUPERLATIVE_LARGEST
                clean.contains("smallest") -> DocsyIntent.STORAGE_SUPERLATIVE_SMALLEST
                clean.contains("newest") || clean.contains("latest") || clean.contains("recent") -> DocsyIntent.STORAGE_SUPERLATIVE_NEWEST
                clean.contains("oldest") -> DocsyIntent.STORAGE_SUPERLATIVE_OLDEST
                else -> DocsyIntent.STORAGE_SUPERLATIVE_LARGEST
            }
            return RoutingDecision(RouterOutcome.ANSWERABLE, intent, 0.95f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 4: Clock & Time (System vs Personal Dates R8)
        // ==========================================
        if (DateTimeTool.isPersonalDateQuery(clean)) {
            // Personal dates / DOB MUST route to Document Fact, NEVER system clock!
            val category = DocumentSynonymDictionary.resolveCanonicalCategory(clean)
            val field = FieldResolver.resolveTargetField(clean)
            return RoutingDecision(
                outcome = RouterOutcome.ANSWERABLE,
                intent = DocsyIntent.DOCUMENT_FACT_QUERY,
                confidence = 0.90f,
                targetCategory = category,
                targetField = field?.name ?: DocumentField.DATE_OF_BIRTH.name,
                normalizedQuery = normalized
            )
        }

        if (isSystemTimeQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.SYSTEM_TIME, 0.95f, normalizedQuery = normalized)
        }
        if (isSystemDateQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.SYSTEM_DATE, 0.95f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 5: Deep Phone Software & Hardware Specs
        // ==========================================
        if (isPhoneFullSpecsQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_FULL_SPECS, 0.98f, normalizedQuery = normalized)
        }
        if (isPhoneOverviewQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_OVERVIEW_SUMMARY, 0.98f, normalizedQuery = normalized)
        }
        if (isKernelQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_KERNEL, 0.97f, normalizedQuery = normalized)
        }
        if (isBuildNumberQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_BUILD_NUMBER, 0.97f, normalizedQuery = normalized)
        }
        if (isSecurityPatchQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_SECURITY_PATCH, 0.97f, normalizedQuery = normalized)
        }
        if (isAndroidVersionQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_ANDROID_VERSION, 0.97f, normalizedQuery = normalized)
        }
        if (isRefreshRateQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_REFRESH_RATE, 0.97f, normalizedQuery = normalized)
        }
        if (isChipsetCpuQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_CHIPSET_CPU, 0.96f, normalizedQuery = normalized)
        }
        if (isCameraQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_CAMERAS, 0.96f, normalizedQuery = normalized)
        }
        if (isSensorsQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_SENSORS, 0.96f, normalizedQuery = normalized)
        }
        if (isSecurityStatusQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_SECURITY_STATUS, 0.95f, normalizedQuery = normalized)
        }
        if (isAudioThermalQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.PHONE_AUDIO_THERMAL, 0.95f, normalizedQuery = normalized)
        }
        if (isBatteryQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.BATTERY_STATUS, 0.95f, normalizedQuery = normalized)
        }
        if (isNetworkQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.NETWORK_STATUS, 0.95f, normalizedQuery = normalized)
        }
        if (isBluetoothQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.BLUETOOTH_STATUS, 0.95f, normalizedQuery = normalized)
        }
        if (isMemoryQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.MEMORY_RAM_STATUS, 0.92f, normalizedQuery = normalized)
        }
        if (isStorageQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.INTERNAL_STORAGE_SPACE, 0.92f, normalizedQuery = normalized)
        }
        if (isHardwareQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.DEVICE_HARDWARE_SPECS, 0.92f, normalizedQuery = normalized)
        }
        if (isStepCountQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.STEP_COUNT_QUERY, 0.95f, normalizedQuery = normalized)
        }
        if (isAppsQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.INSTALLED_APPS_QUERY, 0.90f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 6: Telephony & Communication
        // ==========================================
        if (isContactQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.CONTACT_LOOKUP, 0.92f, normalizedQuery = normalized)
        }
        if (isSmsQuery(clean)) {
            val intent = if (clean.contains("unread") || clean.contains("count")) DocsyIntent.UNREAD_SMS_COUNT else DocsyIntent.SMS_SEARCH
            return RoutingDecision(RouterOutcome.ANSWERABLE, intent, 0.90f, normalizedQuery = normalized)
        }
        if (isCallLogQuery(clean)) {
            val intent = if (clean.contains("missed")) DocsyIntent.MISSED_CALLS_COUNT else DocsyIntent.CALL_LOG_SEARCH
            return RoutingDecision(RouterOutcome.ANSWERABLE, intent, 0.90f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 7: Calculator Evaluation
        // ==========================================
        if (isCalculatorQuery(clean)) {
            return RoutingDecision(RouterOutcome.ANSWERABLE, DocsyIntent.CALCULATOR_EVALUATION, 0.95f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 8: Out of Scope Queries
        // ==========================================
        if (isOutOfScopeQuery(clean)) {
            return RoutingDecision(RouterOutcome.OUT_OF_SCOPE, DocsyIntent.OUT_OF_SCOPE, 0.95f, normalizedQuery = normalized)
        }

        // ==========================================
        // STAGE 9: Document Facts & Category Search
        // ==========================================
        val docCategory = DocumentSynonymDictionary.resolveCanonicalCategory(clean)
        val targetField = FieldResolver.resolveTargetField(clean)

        if (docCategory != null || targetField != null) {
            val isIdDoc = docCategory in setOf("AADHAAR", "PAN", "PASSPORT", "VOTER_ID", "DRIVING_LICENSE")
            val isIdField = targetField == DocumentField.ID_NUMBER
            val intent = if (isIdField || (isIdDoc && (clean.contains("card") || clean.contains("number") || clean.contains("find") || clean.contains("where")))) {
                DocsyIntent.DOCUMENT_ID_LOOKUP
            } else {
                DocsyIntent.DOCUMENT_FACT_QUERY
            }
            return RoutingDecision(
                outcome = RouterOutcome.ANSWERABLE,
                intent = intent,
                confidence = 0.92f,
                targetCategory = docCategory,
                targetField = targetField?.name,
                normalizedQuery = normalized
            )
        }

        // Fallback: Lexical document query
        return RoutingDecision(
            outcome = RouterOutcome.ANSWERABLE,
            intent = DocsyIntent.DOCUMENT_SEARCH_LEXICAL,
            confidence = 0.60f,
            normalizedQuery = normalized
        )
    }

    // Small Talk Matchers
    private fun isGreeting(q: String) =
        q.matches(Regex("""^(hi|hello|hey|namaste|good morning|good afternoon|good evening)\b.*"""))

    private fun isHowAreYou(q: String) =
        q.contains("how are you") || q.contains("kaise ho") || q.contains("kya haal")

    private fun isWhoAreYou(q: String) =
        q.contains("who are you") || q.contains("what are you") || q.contains("tum kaun ho") || q.contains("kya ho tum")

    private fun isGratitude(q: String) =
        q.contains("thank you") || q.contains("thanks") || q.contains("shukriya") || q.contains("dhanyawad")

    private fun isFarewell(q: String) =
        q.matches(Regex("""^(bye|goodbye|see you|alvida)\b.*"""))

    // Profile Matcher
    private fun isUserNameQuery(q: String) =
        q.contains("what is my name") || q.contains("who am i") || q.contains("mera naam kya hai") || q.contains("my name")

    // Media and Storage Collision Matchers
    private fun isMediaCountQuery(q: String) =
        q.contains("video") || q.contains("photos") || q.contains("pictures") || q.contains("images") ||
            q.contains("audio") || q.contains("songs") || q.contains("musics")

    private fun isStorageSuperlativeQuery(q: String) =
        (q.contains("file") || q.contains("files") || q.contains("storage")) &&
            (q.contains("largest") || q.contains("biggest") || q.contains("smallest") ||
                q.contains("newest") || q.contains("oldest") || q.contains("latest") || q.contains("recent"))

    // System Time Matchers
    private fun isSystemTimeQuery(q: String) =
        (q.contains("time") || q.contains("samay") || q.contains("waqt") || q.contains("clock")) &&
            !q.contains("dob") && !q.contains("birth")

    private fun isSystemDateQuery(q: String) =
        (q.contains("date") || q.contains("tareekh") || q.contains("day of week") || q.contains("which day")) &&
            !q.contains("dob") && !q.contains("birth")

    // Device Matchers
    private fun isBatteryQuery(q: String) =
        q.contains("battery") || q.contains("charging") || q.contains("charge")

    private fun isNetworkQuery(q: String) =
        q.contains("wifi") || q.contains("wi-fi") || q.contains("network") || q.contains("mobile data") ||
            q.contains("airplane mode") || q.contains("internet")

    private fun isBluetoothQuery(q: String) =
        q.contains("bluetooth")

    // Phone Overview & Full Specs Matchers
    fun isPhoneOverviewQuery(q: String) =
        q.contains("tell me about my phone") || q.contains("about my phone") || q.contains("about phone") ||
            q.contains("device info") || q.contains("phone info") || q.contains("mera phone") ||
            q.contains("phone ke baare me") || q.contains("about device")

    fun isPhoneFullSpecsQuery(q: String) =
        q.contains("all details") || q.contains("full specs") || q.contains("everything about my phone") ||
            q.contains("all specs") || q.contains("complete specs") || q.contains("full details") ||
            q.contains("all phone details") || q.contains("full specification")

    fun isKernelQuery(q: String) =
        q.contains("kernel") || q.contains("kernal") || q.contains("os version") ||
            q.contains("linux version")

    fun isBuildNumberQuery(q: String) =
        q.contains("build number") || q.contains("build no") || q.contains("display id") ||
            q.contains("build id") || q.contains("fingerprint") || q.contains("build version")

    fun isSecurityPatchQuery(q: String) =
        q.contains("security patch") || q.contains("security update") || q.contains("patch level")

    fun isAndroidVersionQuery(q: String) =
        q.contains("android version") || q.contains("firmware") || q.contains("api level") ||
            q.contains("sdk level") || q.contains("sdk version") || q.contains("android release") ||
            (q.contains("android") && (q.contains("version") || q.contains("kya") || q.contains("konsa")))

    fun isChipsetCpuQuery(q: String) =
        q.contains("chipset") || q.contains("soc") || q.contains("processor") ||
            q.contains("cpu") || q.contains("gpu") || q.contains("renderer") ||
            q.contains("cores") || q.contains("cpu frequency") || q.contains("governor")

    fun isRefreshRateQuery(q: String) =
        q.contains("refresh rate") || q.contains("screen hz") || q.contains("display hz") ||
            q.contains("how many hz") || q.contains("frame rate") ||
            (q.contains("hz") && (q.contains("screen") || q.contains("display")))

    fun isCameraQuery(q: String) =
        q.contains("camera") || q.contains("cameras") || q.contains("megapixel") ||
            q.contains("megapixels") || q.contains("camera specs")

    fun isSensorsQuery(q: String) =
        q.contains("sensor") || q.contains("sensors") || q.contains("gyroscope") ||
            q.contains("accelerometer")

    fun isSecurityStatusQuery(q: String) =
        q.contains("security status") || q.contains("screen lock") || q.contains("developer options") ||
            q.contains("usb debugging") || q.contains("encryption") || q.contains("selinux")

    fun isAudioThermalQuery(q: String) =
        q.contains("ringer") || q.contains("volume") || q.contains("vibration") ||
            q.contains("haptics") || q.contains("thermal") || q.contains("throttling")

    fun isStorageQuery(q: String) =
        q.contains("internal storage") || q.contains("storage space") || q.contains("disk") ||
            q.contains("storage kitna hai") || q.contains("free storage") || q.contains("available storage")

    private fun isMemoryQuery(q: String) =
        q.contains("ram") || q.contains("ram kitni hai") || q.contains("memory") || q.contains("ram memory")

    private fun isHardwareQuery(q: String) =
        q.contains("model") || q.contains("hardware") || q.contains("display") || q.contains("screen")

    private fun isStepCountQuery(q: String) =
        q.contains("step") || q.contains("steps") || q.contains("walk") || q.contains("walked")

    private fun isAppsQuery(q: String) =
        q.contains("installed apps") || q.contains("what apps") || q.contains("list apps")

    // Telephony Matchers
    private fun isContactQuery(q: String) =
        q.contains("contact") || q.contains("phone number") || q.contains("mobile number") ||
            q.startsWith("call ") || q.contains("number of")

    private fun isSmsQuery(q: String) =
        q.contains("sms") || q.contains("message") || q.contains("messages")

    private fun isCallLogQuery(q: String) =
        q.contains("call log") || q.contains("missed call") || q.contains("calls")

    // Calculator Matcher
    private fun isCalculatorQuery(q: String) =
        q.contains("calculate") || q.contains("sum of") || q.contains("average of") ||
            q.matches(Regex(""".*\d+\s*[\+\-\*\/\%]\s*\d+.*"""))

    // Out of Scope Matcher
    private fun isOutOfScopeQuery(q: String) =
        q.contains("weather") || q.contains("temperature outside") || q.contains("stock price") ||
            q.contains("cricket score") || q.contains("search google") || q.contains("news today")
}
