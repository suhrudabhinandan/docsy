package com.suhrud.docsy.retrieval

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.suhrud.docsy.tools.TopicDomain
import java.io.File

data class FileReference(
    val documentId: Long,
    val fileName: String,
    val filePath: String,
    val mimeType: String
)

data class TableRow(
    val label: String,
    val value: String
)

enum class ReplyType {
    TEXT_ONLY,
    TABLE,
    FILE_REFERENCE,
    DISAMBIGUATION_PROMPT,
    NOT_FOUND,
    ERROR
}

data class DocsyReply(
    val primaryText: String,
    val domain: TopicDomain,
    val replyType: ReplyType = ReplyType.TEXT_ONLY,
    val tableRows: List<TableRow> = emptyList(),
    val fileReferences: List<FileReference> = emptyList(),
    val candidateOwners: List<String> = emptyList()
) {

    /**
     * Creates an Intent to open a referenced file using FileProvider.
     * Satisfies Phase 9.12 in AUDIT.md.
     */
    fun createFileOpenIntent(context: Context, fileRef: FileReference): Intent? {
        val file = File(fileRef.filePath)
        if (!file.exists()) return null

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, fileRef.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
