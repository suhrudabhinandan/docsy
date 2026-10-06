package com.suhrud.docsy.domain.stats

import com.suhrud.docsy.data.local.DocumentDao
import com.suhrud.docsy.data.model.ChatMessage
import com.suhrud.docsy.data.model.DocumentEntity
import com.suhrud.docsy.domain.query.ParsedQuery
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FolderStatsEngine(
    private val documentDao: DocumentDao
) {

    suspend fun answerFolderQuery(parsed: ParsedQuery): ChatMessage {
        val target = parsed.targetFolder?.trim() ?: ""
        if (target.isBlank()) {
            return ChatMessage(
                isUser = false,
                text = "Which folder would you like to check?",
                answerHighlight = "No Folder Specified",
                supportingMetadata = "FOLDER SEARCH"
            )
        }

        val lowerTarget = target.lowercase(Locale.ROOT)
        if (lowerTarget.contains("data") && (lowerTarget.contains("android") || lowerTarget.startsWith("data"))) {
            return ChatMessage(
                isUser = false,
                text = "Android blocks all apps from accessing Android/data on Android 11+ for system security.",
                answerHighlight = "Access Restricted by Android",
                supportingMetadata = "STORAGE ACCESS RESTRICTION",
                subtext = "Android Scoped Storage policy prohibits reading Android/data"
            )
        }
        if (lowerTarget.contains("obb") && (lowerTarget.contains("android") || lowerTarget.startsWith("obb"))) {
            return ChatMessage(
                isUser = false,
                text = "Android blocks all apps from accessing Android/obb on Android 11+ for system security.",
                answerHighlight = "Access Restricted by Android",
                supportingMetadata = "STORAGE ACCESS RESTRICTION",
                subtext = "Android Scoped Storage policy prohibits reading Android/obb"
            )
        }

        val allDocs = documentDao.getAllDocumentsDirect()
        val candidateFolders = resolveFolderCandidates(allDocs, target)

        if (candidateFolders.isEmpty()) {
            return ChatMessage(
                isUser = false,
                text = "I couldn't find a folder called $target on your device.",
                answerHighlight = "Folder Not Found",
                supportingMetadata = "STORAGE // $target",
                subtext = "Make sure storage permissions are granted and files are indexed"
            )
        }

        if (candidateFolders.size > 1) {
            val listText = StringBuilder("I found ${candidateFolders.size} folders matching '$target':\n")
            candidateFolders.take(4).forEachIndexed { i, folderPath ->
                val count = allDocs.count { it.pathUri.startsWith(folderPath) && !it.isDirectory }
                listText.append("${i + 1}. $folderPath ($count files)\n")
            }
            listText.append("Please specify the exact full folder name you want to explore.")
            return ChatMessage(
                isUser = false,
                text = listText.toString().trim(),
                answerHighlight = "${candidateFolders.size} Folders Found",
                supportingMetadata = "MULTIPLE FOLDERS MATCH"
            )
        }

        val folderPath = candidateFolders.first()
        val folderDisplayName = folderPath.trimEnd('/').substringAfterLast('/', folderPath)

        val recursiveFiles = allDocs.filter {
            !it.isDirectory && (it.pathUri.startsWith("$folderPath/") || it.parentFolder.startsWith(folderPath))
        }

        val directFiles = allDocs.filter {
            !it.isDirectory && (it.parentFolder == folderPath || it.parentFolder.trimEnd('/') == folderPath.trimEnd('/'))
        }

        val subfolderPaths = allDocs.filter {
            it.isDirectory && it.pathUri.startsWith("$folderPath/") && it.pathUri != folderPath
        }.map { it.pathUri }.distinct()

        val photos = recursiveFiles.filter { isPhoto(it) }
        val videos = recursiveFiles.filter { isVideo(it) }
        val audios = recursiveFiles.filter { isAudio(it) }
        val docs = recursiveFiles.filter { isDocument(it) }
        val other = recursiveFiles.size - (photos.size + videos.size + audios.size + docs.size).coerceAtLeast(0)

        val totalSize = recursiveFiles.sumOf { it.fileSizeBytes }

        return when (parsed.folderQueryType) {
            "size" -> {
                val formatted = formatSize(totalSize)
                ChatMessage(
                    isUser = false,
                    text = "The total size of the $folderDisplayName folder is $formatted across ${recursiveFiles.size} files.",
                    answerHighlight = formatted,
                    supportingMetadata = "FOLDER STORAGE · $folderDisplayName",
                    subtext = "${recursiveFiles.size} files total · Path: $folderPath"
                )
            }

            "folders_count" -> {
                val count = subfolderPaths.size
                ChatMessage(
                    isUser = false,
                    text = "Found $count subfolders inside the $folderDisplayName folder.",
                    answerHighlight = "$count Folders",
                    supportingMetadata = "FOLDER DIRECTORIES",
                    subtext = "Path: $folderPath"
                )
            }

            "superlative" -> {
                val op = parsed.filterType ?: "BIGGEST"
                val file = when (op) {
                    "BIGGEST", "LARGEST" -> recursiveFiles.maxByOrNull { it.fileSizeBytes }
                    "SMALLEST" -> recursiveFiles.filter { it.fileSizeBytes > 0 }.minByOrNull { it.fileSizeBytes }
                    "NEWEST", "LATEST" -> recursiveFiles.maxByOrNull { it.modifiedDate }
                    "OLDEST" -> recursiveFiles.filter { it.modifiedDate > 0 }.minByOrNull { it.modifiedDate }
                    else -> recursiveFiles.maxByOrNull { it.fileSizeBytes }
                }

                if (file == null) {
                    ChatMessage(
                        isUser = false,
                        text = "There are no files in the $folderDisplayName folder.",
                        answerHighlight = "Empty Folder",
                        supportingMetadata = "FOLDER · $folderDisplayName"
                    )
                } else {
                    val dateFormatted = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(file.modifiedDate))
                    val highlight = file.fileName
                    ChatMessage(
                        isUser = false,
                        text = when (op) {
                            "NEWEST", "LATEST" -> "The newest file in $folderDisplayName is ${file.fileName}, modified on $dateFormatted."
                            "OLDEST" -> "The oldest file in $folderDisplayName is ${file.fileName}, dated $dateFormatted."
                            else -> "The largest file in $folderDisplayName is ${file.fileName} (${formatSize(file.fileSizeBytes)})."
                        },
                        answerHighlight = highlight,
                        supportingMetadata = "FILE · $folderDisplayName",
                        subtext = "${formatSize(file.fileSizeBytes)} · $dateFormatted",
                        sourceDocument = file
                    )
                }
            }

            else -> {
                val filter = parsed.filterType ?: "all"
                when (filter) {
                    "photo" -> {
                        val directCount = directFiles.count { isPhoto(it) }
                        ChatMessage(
                            isUser = false,
                            text = "There are ${photos.size} photos in the $folderDisplayName folder (${directCount} directly in folder, ${photos.size - directCount} in subfolders).",
                            answerHighlight = "${photos.size} Photos",
                            supportingMetadata = "PHOTOS · $folderDisplayName",
                            subtext = "Total files in folder: ${recursiveFiles.size}"
                        )
                    }

                    "video" -> {
                        val directCount = directFiles.count { isVideo(it) }
                        ChatMessage(
                            isUser = false,
                            text = "There are ${videos.size} videos in the $folderDisplayName folder (${directCount} directly in folder, ${videos.size - directCount} in subfolders).",
                            answerHighlight = "${videos.size} Videos",
                            supportingMetadata = "VIDEOS · $folderDisplayName",
                            subtext = "Total files in folder: ${recursiveFiles.size}"
                        )
                    }

                    "audio" -> {
                        ChatMessage(
                            isUser = false,
                            text = "There are ${audios.size} audio files in the $folderDisplayName folder.",
                            answerHighlight = "${audios.size} Audio Files",
                            supportingMetadata = "AUDIO · $folderDisplayName"
                        )
                    }

                    "document" -> {
                        ChatMessage(
                            isUser = false,
                            text = "There are ${docs.size} documents in the $folderDisplayName folder.",
                            answerHighlight = "${docs.size} Documents",
                            supportingMetadata = "DOCUMENTS · $folderDisplayName"
                        )
                    }

                    else -> {
                        ChatMessage(
                            isUser = false,
                            text = "Found ${recursiveFiles.size} files in the $folderDisplayName folder (${directFiles.size} direct children, ${recursiveFiles.size - directFiles.size} in subfolders).\nBreakdown: ${photos.size} photos, ${videos.size} videos, ${docs.size} documents, ${audios.size} audio files, ${other.coerceAtLeast(0)} other.",
                            answerHighlight = "${recursiveFiles.size} Files",
                            supportingMetadata = "FOLDER CONTENTS · $folderDisplayName",
                            subtext = "Size: ${formatSize(totalSize)} · Subfolders: ${subfolderPaths.size}"
                        )
                    }
                }
            }
        }
    }

    fun resolveFolderCandidates(allDocs: List<DocumentEntity>, targetName: String): List<String> {
        val target = targetName.trim().lowercase(Locale.ROOT)
        val allFolders = mutableSetOf<String>()
        for (doc in allDocs) {
            if (doc.isDirectory && doc.pathUri.isNotBlank()) {
                allFolders.add(doc.pathUri.trimEnd('/'))
            }
            if (doc.parentFolder.isNotBlank()) {
                allFolders.add(doc.parentFolder.trimEnd('/'))
            }
        }

        val exactMatches = allFolders.filter { folder ->
            val baseName = folder.substringAfterLast('/').lowercase(Locale.ROOT)
            baseName == target
        }
        if (exactMatches.isNotEmpty()) return exactMatches

        val baseNameMatches = allFolders.filter { folder ->
            val baseName = folder.substringAfterLast('/').lowercase(Locale.ROOT)
            baseName.contains(target)
        }
        if (baseNameMatches.isNotEmpty()) return baseNameMatches

        val pathMatches = allFolders.filter { folder ->
            folder.lowercase(Locale.ROOT).contains("/$target")
        }
        return pathMatches
    }

    private fun isPhoto(doc: DocumentEntity): Boolean {
        return doc.mimeType.startsWith("image/") || doc.extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    }

    private fun isVideo(doc: DocumentEntity): Boolean {
        return doc.mimeType.startsWith("video/") || doc.extension in listOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
    }

    private fun isAudio(doc: DocumentEntity): Boolean {
        return doc.mimeType.startsWith("audio/") || doc.extension in listOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus")
    }

    private fun isDocument(doc: DocumentEntity): Boolean {
        return doc.extension in listOf("pdf", "docx", "doc", "txt", "xlsx", "csv", "pptx") ||
                doc.mimeType.contains("pdf") || doc.mimeType.contains("document") || doc.mimeType.contains("text/")
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.ROOT, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.ROOT, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
