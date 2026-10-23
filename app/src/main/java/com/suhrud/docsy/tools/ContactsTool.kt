package com.suhrud.docsy.tools

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactItem(
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val matchedAlias: String? = null
)

/**
 * Contacts search and relationship alias tool.
 * Satisfies Phase 8.6 in AUDIT.md.
 */
object ContactsTool {

    // Relationship aliases mapping to standard canonical relations
    val RELATION_ALIASES: Map<String, List<String>> = mapOf(
        "mother" to listOf("mom", "mummy", "maa", "mata", "mother", "aai", "ammi", "mum"),
        "father" to listOf("dad", "papa", "father", "baba", "pitaji", "abbu", "pop"),
        "brother" to listOf("brother", "bhai", "bhaiya", "bro", "veer"),
        "sister" to listOf("sister", "didi", "behen", "sis"),
        "wife" to listOf("wife", "biwi", "patni"),
        "husband" to listOf("husband", "pati", "shauhar")
    )

    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Resolves alias tokens (e.g. "mom", "papa") to all equivalent aliases.
     */
    fun getExpandedSearchTerms(query: String): List<String> {
        val lower = query.trim().lowercase()
        val terms = mutableListOf(lower)

        for ((_, aliases) in RELATION_ALIASES) {
            if (aliases.contains(lower)) {
                terms.addAll(aliases)
            }
        }
        return terms.distinct()
    }

    /**
     * Searches device contacts by name or relationship alias.
     */
    fun searchContacts(context: Context, query: String, limit: Int = 10): List<ContactItem> {
        if (!hasContactsPermission(context)) return emptyList()

        val searchTerms = getExpandedSearchTerms(query)
        val results = mutableListOf<ContactItem>()

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
        } catch (_: Exception) {
            null
        }

        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (it.moveToNext() && results.size < limit) {
                val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                val number = if (numberIdx >= 0) it.getString(numberIdx) ?: "" else ""
                val photo = if (photoIdx >= 0) it.getString(photoIdx) else null

                val lowerName = name.lowercase()
                val matchedTerm = searchTerms.firstOrNull { term -> lowerName.contains(term) }

                if (matchedTerm != null || query.isBlank()) {
                    results.add(
                        ContactItem(
                            name = name,
                            phoneNumber = number.replace(" ", "").replace("-", ""),
                            photoUri = photo,
                            matchedAlias = matchedTerm
                        )
                    )
                }
            }
        }

        return results
    }

    fun formatAnswer(contacts: List<ContactItem>, query: String): String {
        if (contacts.isEmpty()) {
            return "No contacts found matching \"$query\"."
        }
        if (contacts.size == 1) {
            val c = contacts.first()
            return "${c.name}: ${c.phoneNumber}"
        }
        return contacts.take(3).joinToString("\n") { "${it.name}: ${it.phoneNumber}" }
    }
}
