package com.suhrud.docsy.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import com.suhrud.docsy.data.db.dao.DocumentDao
import com.suhrud.docsy.data.db.entity.DocumentEntity
import com.suhrud.docsy.data.db.entity.DocumentStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class DiscoveredFile(
    val file: File,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long
)

data class ScanProgress(
    val isScanning: Boolean = false,
    val totalFilesWalked: Int = 0,
    val documentsDiscovered: Int = 0,
    val currentDirectory: String = "",
    val error: String? = null
)

open class FileScanner(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    private val docsyPackageDir = try {
        context.filesDir?.parentFile?.canonicalPath
    } catch (_: Exception) {
        null
    }

    /**
     * Resolves all root storage volumes without hard-coded folder restrictions.
     */
    open fun getRootStorageVolumes(): List<File> {
        val roots = LinkedHashSet<File>()

        // 1. Primary external storage root (/storage/emulated/0)
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists() && primary.canRead()) {
            roots.add(primary)
        }

        // 2. StorageManager volumes (API 24+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            sm?.storageVolumes?.forEach { volume ->
                try {
                    val getDirMethod = volume.javaClass.getMethod("getDirectory")
                    val dir = getDirMethod.invoke(volume) as? File
                    if (dir != null && dir.exists() && dir.canRead()) {
                        roots.add(dir)
                    }
                } catch (_: Exception) {
                    // Method may be hidden or unavailable on certain vendors
                }
            }
        }

        // 3. Fallback extraction from external files dirs (for SD card root detection)
        val extDirs = context.getExternalFilesDirs(null)
        extDirs?.filterNotNull()?.forEach { appSpecificDir ->
            val path = appSpecificDir.absolutePath
            val androidIndex = path.indexOf("/Android")
            if (androidIndex > 0) {
                val rootDir = File(path.substring(0, androidIndex))
                if (rootDir.exists() && rootDir.canRead()) {
                    roots.add(rootDir)
                }
            }
        }

        return roots.toList()
    }

    /**
     * Executes full recursive scan across all storage roots using coroutine producer-consumer channels.
     * @param forceReindex If true, forces re-indexing of all matching files regardless of contentKey.
     */
    suspend fun scanStorage(forceReindex: Boolean = false): ScanProgress = withContext(dispatcher) {
        _scanProgress.update {
            it.copy(
                isScanning = true,
                totalFilesWalked = 0,
                documentsDiscovered = 0,
                currentDirectory = "Preparing storage roots...",
                error = null
            )
        }

        val roots = getRootStorageVolumes()
        val channel = Channel<DiscoveredFile>(capacity = 256)
        val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

        val totalWalkedCounter = AtomicInteger(0)
        val discoveredCounter = AtomicInteger(0)
        val visitedDirs = ConcurrentHashMap.newKeySet<String>()

        // Launch consumer workers
        val workerJobs = (1..workerCount).map {
            launch(dispatcher) {
                val batch = mutableListOf<DocumentEntity>()

                for (discovered in channel) {
                    val contentKey = FileFilter.computeContentKey(
                        discovered.path,
                        discovered.sizeBytes,
                        discovered.lastModified
                    )

                    val existingDoc = if (!forceReindex) {
                        documentDao.getDocumentByContentKey(contentKey)
                    } else null

                    if (existingDoc == null) {
                        val extension = FileFilter.getExtension(discovered.file.name)
                        val entity = DocumentEntity(
                            absolutePath = discovered.path,
                            displayName = FileFilter.sanitizeDisplayName(discovered.file.name),
                            extension = extension,
                            mimeType = FileFilter.getMimeType(extension),
                            sizeBytes = discovered.sizeBytes,
                            lastModified = discovered.lastModified,
                            contentKey = contentKey,
                            indexedAt = System.currentTimeMillis(),
                            status = DocumentStatus.DISCOVERED
                        )
                        batch.add(entity)
                        discoveredCounter.incrementAndGet()

                        if (batch.size >= 50) {
                            documentDao.insertDocuments(batch)
                            batch.clear()
                            _scanProgress.update {
                                it.copy(
                                    totalFilesWalked = totalWalkedCounter.get(),
                                    documentsDiscovered = discoveredCounter.get()
                                )
                            }
                        }
                    }
                }

                if (batch.isNotEmpty()) {
                    documentDao.insertDocuments(batch)
                    batch.clear()
                }
            }
        }

        // Producer: walk all roots and send matching files to channel
        try {
            for (root in roots) {
                walkDirectory(
                    dir = root,
                    channel = channel,
                    visitedDirs = visitedDirs,
                    totalWalkedCounter = totalWalkedCounter
                )
            }
        } catch (e: Exception) {
            _scanProgress.update { it.copy(error = e.localizedMessage) }
        } finally {
            channel.close()
            workerJobs.forEach { it.join() }
        }

        _scanProgress.update {
            it.copy(
                isScanning = false,
                totalFilesWalked = totalWalkedCounter.get(),
                documentsDiscovered = discoveredCounter.get(),
                currentDirectory = "Complete"
            )
        }

        _scanProgress.value
    }

    /**
     * Recursive directory walker with symlink loop detection and skip rules.
     */
    private suspend fun walkDirectory(
        dir: File,
        channel: Channel<DiscoveredFile>,
        visitedDirs: MutableSet<String>,
        totalWalkedCounter: AtomicInteger
    ) {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) return

        // Symlink loop detection: track canonical paths
        val canonicalPath = try {
            dir.canonicalPath
        } catch (_: Exception) {
            return
        }

        if (!visitedDirs.add(canonicalPath)) {
            // Already visited this physical directory, skip to avoid loops
            return
        }

        // Skip rules: cache, tmp, temp, Android/data, Android/obb, docsy package
        if (FileFilter.shouldSkipDirectory(dir, docsyPackageDir)) {
            return
        }

        _scanProgress.update { it.copy(currentDirectory = dir.name) }

        val children = dir.listFiles() ?: return

        for (child in children) {
            if (child.isDirectory) {
                walkDirectory(child, channel, visitedDirs, totalWalkedCounter)
            } else if (child.isFile) {
                totalWalkedCounter.incrementAndGet()

                // Check extension and size
                if (FileFilter.isSupportedFile(child)) {
                    val discovered = DiscoveredFile(
                        file = child,
                        path = child.absolutePath,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified()
                    )
                    channel.send(discovered)
                }
            }
        }
    }
}
