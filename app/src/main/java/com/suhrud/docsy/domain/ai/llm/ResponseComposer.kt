package com.suhrud.docsy.domain.ai.llm

import com.suhrud.docsy.data.model.ChatMessage
import java.util.Calendar
import java.util.Locale
import java.util.Random

enum class ResponseCategory {
    GREETING,
    THANKS,
    GOODBYE,
    HOW_ARE_YOU,
    WHO_ARE_YOU,
    ACKNOWLEDGEMENT,
    NOT_FOUND,
    MISSING_PERMISSION,
    CLARIFYING_QUESTION,
    SUCCESS_LEAD_IN,
    DEVICE_FACTS,
    STEP_ANSWERS,
    OUT_OF_SCOPE
}

object ResponseComposer {

    private val lastChosenMap = mutableMapOf<ResponseCategory, MutableList<Int>>()
    private val rng = Random()

    private val GREETING_VARIANTS = listOf(
        "Hello! How can I help you with your phone, files, or step count today?",
        "Hi there! What would you like to search for on your device?",
        "Good day! Ready to help you find your documents, messages, or steps.",
        "Hey! How can I assist you on your device right now?",
        "Hello! What can I look up for you on your phone today?",
        "Hi! Ready to search your local files, call logs, or system details.",
        "Hey there! What local document or step count information do you need?",
        "Greetings! How may I assist you with your phone's data today?",
        "Hello! I'm here and ready to help you check your files or device stats.",
        "Hi! What would you like to find on your phone right now?",
        "Hey! Let me know what document, message, or step log you're looking for.",
        "Hello there! Ready to assist with anything stored on your phone."
    )

    private val HINGLISH_GREETING_VARIANTS = listOf(
        "Namaste! Aaj main aapke phone, files ya steps ke liye kya search karoon?",
        "Haanji! Aap apne phone ke kaunse document ya message dhoondhna chahte hain?",
        "Hello! Kaise madad kar sakta hoon aapke device ke data dhoondhne mein?",
        "Namaste! Aapke sabhi local files aur step count search karne ke liye tayar hoon.",
        "Haanji! Bolo, aapko kaunsa bill, marksheet ya photo dhoondhna hai?",
        "Hi! Aapke phone par jo bhi stored hai, main 100% offline dhoondh sakta hoon.",
        "Namaste ji! Aapke device ki jankari ya files ke liye bataiye kya chahiye?",
        "Haanji! Aapke SMS, calls ya documents search karne ke liye bataiye.",
        "Hello! Main aapka offline device assistant hoon, bataiye kya madad karoon?",
        "Namaste! Aapke daily steps ya storage check karne ke liye bataiye.",
        "Haanji! Aapka koi bhi offline query ho, bataiye main search kar deta hoon.",
        "Hi ji! Aapke phone par safe and offline search ke liye main ready hoon."
    )

    private val THANKS_VARIANTS = listOf(
        "You're very welcome! Let me know if you need anything else.",
        "Glad I could help! Feel free to ask whenever you need more details.",
        "Happy to help! I'm always here for your on-device queries.",
        "Anytime! Let me know if there's any other file or stat you need.",
        "You're welcome! Ready whenever you want to look up another file.",
        "My pleasure! Always here to search your local phone data.",
        "No problem at all! Let me know if you need anything else on your device.",
        "Glad to be of service! What else can I check for you?",
        "You got it! Feel free to ask about your files or steps anytime.",
        "Always happy to assist with your phone's documents and stats!",
        "You're welcome! Let me know whenever you have more questions.",
        "Anytime! I'm right here whenever you need another search."
    )

    private val NOT_FOUND_VARIANTS = listOf(
        "I couldn't find %s on your device.",
        "I checked your stored documents, but %s isn't available.",
        "I searched your local files, but I couldn't locate %s.",
        "No matching record for %s was found in your storage.",
        "I went through your indexed files, but %s isn't present.",
        "%s doesn't seem to be stored on your phone.",
        "I looked through your documents, but %s wasn't found.",
        "I couldn't locate %s among your indexed device files.",
        "There's no file matching %s in your accessible storage.",
        "I scanned your local records, but %s wasn't found.",
        "No document matching %s is currently on your device.",
        "I couldn't find any file for %s in your storage."
    )

    private val OUT_OF_SCOPE_VARIANTS = listOf(
        "I focus strictly on your phone's local files, messages, calls, steps, and device stats.",
        "I don't have general internet information, but I can help you search anything stored on your phone!",
        "I answer questions about your device and files offline, rather than general web knowledge.",
        "My knowledge is limited to your phone's local storage, steps, messages, and device specs.",
        "I'm an offline phone assistant, so I search your local documents and device data rather than the web.",
        "I don't look up internet general knowledge, but I'm happy to help search your device's files!",
        "I specialize in searching your phone's stored documents, steps, calls, and settings offline.",
        "I'm built for on-device search—I can check your files, SMS, and device stats, but not general web topics.",
        "I answer questions about what's stored on your phone. Let me know if you want to search your files!",
        "I'm strictly an offline assistant for your phone. I can search your documents, steps, and phone details.",
        "I don't have access to live web search, but I'm ready to find any document or stat on your device.",
        "I specialize in local phone search. Ask me about your documents, bills, steps, or device details!"
    )

    private fun getNextVariant(category: ResponseCategory, variants: List<String>): String {
        val history = lastChosenMap.getOrPut(category) { mutableListOf() }
        var chosenIndex = rng.nextInt(variants.size)

        // Ensure no repetition within last 5 picks
        var attempts = 0
        while (history.contains(chosenIndex) && attempts < 20) {
            chosenIndex = rng.nextInt(variants.size)
            attempts++
        }

        history.add(chosenIndex)
        if (history.size > 5) {
            history.removeAt(0)
        }

        return variants[chosenIndex]
    }

    fun isHinglish(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT)
        val hinglishTokens = setOf("kaise", "hai", "kya", "mera", "meri", "dhanyawad", "batao", "kab", "kitna", "kitni", "namaste", "ji", "hoon", "nahi")
        return lower.split(" ").any { it in hinglishTokens }
    }

    fun composeSmallTalk(rawQuery: String, userName: String, isFirstGreeting: Boolean = false): ChatMessage {
        val q = rawQuery.lowercase(Locale.ROOT)
        val hinglish = isHinglish(q)
        val nameStr = if (userName.isNotBlank()) " $userName" else ""

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val realTimeGreeting = when (currentHour) {
            in 5..11 -> "good morning"
            in 12..16 -> "good afternoon"
            in 17..21 -> "good evening"
            else -> "good night"
        }

        if (isFirstGreeting) {
            val introText = "Hello$nameStr! I'm Docsy, your 100% offline personal assistant. I can help you search your files and documents, photos and media, text messages, calls, contacts, daily steps, and device details."
            return ChatMessage(isUser = false, text = introText, supportingMetadata = null)
        }

        val text: String = when {
            q.contains("good morning") -> {
                if (currentHour !in 5..11) {
                    "Good $realTimeGreeting$nameStr! It's currently ${realTimeGreeting.replaceFirstChar { it.uppercase() }} here on your device clock. How can I help you right now?"
                } else {
                    "Good morning$nameStr! How can I assist you with your phone or files today?"
                }
            }

            q.contains("good afternoon") -> {
                if (currentHour !in 12..16) {
                    "Good $realTimeGreeting$nameStr! It's currently $realTimeGreeting time. What can I look up on your phone for you?"
                } else {
                    "Good afternoon$nameStr! What can I search for you on your device today?"
                }
            }

            q.contains("good evening") -> {
                if (currentHour !in 17..21) {
                    "Good $realTimeGreeting$nameStr! How can I assist you right now?"
                } else {
                    "Good evening$nameStr! Ready to search your files or check your step count."
                }
            }

            q.contains("thank") || q.contains("dhanyawad") -> {
                getNextVariant(ResponseCategory.THANKS, THANKS_VARIANTS)
            }

            q.contains("how are you") || q.contains("kaise ho") -> {
                "I'm doing great$nameStr! Ready to search your local files, call logs, or step counts 100% offline."
            }

            q.contains("who are you") || q.contains("what can you do") || q.contains("help") -> {
                "I'm Docsy, your on-device personal assistant. I search your local documents, bills, marksheets, photos, videos, audio, SMS, call logs, contacts, and step counts—completely offline."
            }

            q.contains("bye") || q.contains("goodbye") -> {
                "Goodbye$nameStr! Feel free to ask whenever you need anything else on your device."
            }

            else -> {
                if (hinglish) {
                    getNextVariant(ResponseCategory.GREETING, HINGLISH_GREETING_VARIANTS)
                } else {
                    getNextVariant(ResponseCategory.GREETING, GREETING_VARIANTS)
                }
            }
        }

        return ChatMessage(
            isUser = false,
            text = text,
            supportingMetadata = "DOCSY ASSISTANT"
        )
    }

    fun composeNotFound(requestedDoc: String, isScanningFinished: Boolean = true, scannedCount: Int = 0, totalCount: Int = 0): ChatMessage {
        val template = getNextVariant(ResponseCategory.NOT_FOUND, NOT_FOUND_VARIANTS)
        val baseText = String.format(Locale.ROOT, template, requestedDoc)

        val nextStep = if (!isScanningFinished && totalCount > 0) {
            " I'm still scanning your storage ($scannedCount of $totalCount files scanned), so I may find it shortly."
        } else {
            " Make sure the file is stored in accessible phone storage."
        }

        return ChatMessage(
            isUser = false,
            text = "$baseText$nextStep",
            supportingMetadata = "NOT FOUND IN STORAGE"
        )
    }

    fun composeOutOfScope(): ChatMessage {
        val text = getNextVariant(ResponseCategory.OUT_OF_SCOPE, OUT_OF_SCOPE_VARIANTS)
        return ChatMessage(
            isUser = false,
            text = text,
            supportingMetadata = "OFFLINE ASSISTANT"
        )
    }
}
