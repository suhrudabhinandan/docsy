package com.suhrud.docsy.extractor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppPackageExtractor {

    fun extract(context: Context, file: File): ExtractionResult {
        val contentKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            return ExtractionResult(
                contentKey = contentKey,
                extension = file.extension,
                pageCount = 0,
                fullText = "",
                contentHash = contentKey,
                durationMs = 0L,
                failureReason = "Invalid application file"
            )
        }

        val packageManager = context.packageManager
        val archivePath = file.absolutePath
        val packageInfo = try {
            packageManager.getPackageArchiveInfo(archivePath, 0)
        } catch (_: Exception) { null }

        val sb = StringBuilder()
        sb.append("App Package Installer: ").append(file.name).append("\n")

        if (packageInfo != null) {
            packageInfo.applicationInfo?.sourceDir = archivePath
            packageInfo.applicationInfo?.publicSourceDir = archivePath
            val appLabel = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: file.name
            val pkgName = packageInfo.packageName ?: "Unknown"
            val versionName = packageInfo.versionName ?: "Unknown"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION") packageInfo.versionCode.toString()
            }

            sb.append("Label: ").append(appLabel).append("\n")
            sb.append("Package Name: ").append(pkgName).append("\n")
            sb.append("Version: ").append(versionName).append(" (").append(versionCode).append(")\n")
        } else {
            sb.append("Package Name: ").append(file.nameWithoutExtension).append("\n")
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        sb.append("Size: ").append(file.length() / 1024).append(" KB\n")
        sb.append("Modified Date: ").append(sdf.format(Date(file.lastModified()))).append("\n")

        return ExtractionResult(
            contentKey = contentKey,
            extension = file.extension,
            pageCount = 1,
            fullText = sb.toString().trim(),
            contentHash = contentKey,
            durationMs = 0L,
            isFallbackOcrUsed = false,
            failureReason = null
        )
    }
}
