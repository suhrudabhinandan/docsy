package com.suhrud.docsy.extractor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * The on-device vision-language model (VLM): a model that looks at the page image itself.
 * Runs fully offline through LiteRT-LM, on the GPU when the phone allows it (CPU otherwise).
 *
 * It is slow and memory-hungry compared with OCR, so it is only a rescue reader: the scanner calls it for
 * pages that normal reading could not turn into usable fields. One page at a time, a per-scan budget, and the
 * model is unloaded after a short idle period so it does not hold gigabytes of RAM.
 *
 * Model file: a multimodal LiteRT-LM model such as Gemma-3n E2B (".litertlm"). It is too large to ship in the APK.
 * Put it in /sdcard/Docsy/models/ (or /sdcard/Download/, or the app's own "models" folder).
 */
object VlmEngine {
    private const val TAG = "VlmEngine"
    private const val IDLE_UNLOAD_MS = 90_000L
    private const val MAX_PER_SCAN = 25
    private const val MIN_TOTAL_RAM_BYTES = 5_500_000_000L // Gemma-3n E2B needs roughly 4-6 GB while running

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: Engine? = null
    private var idleJob: Job? = null
    @Volatile private var remainingBudget = MAX_PER_SCAN
    @Volatile private var initFailed = false

    @Volatile var lastStatus: String = "Not checked"
        private set

    fun resetBudget() { remainingBudget = MAX_PER_SCAN }

    @Volatile private var cachedModel: File? = null
    @Volatile private var cachedModelAt = 0L

    /** The folder listing is cached for a minute: the scanner asks once per file. */
    fun findModelFile(context: Context): File? {
        val now = System.currentTimeMillis()
        if (now - cachedModelAt < 60_000L && (cachedModel?.exists() != false)) return cachedModel
        val found = searchModelFile(context)
        cachedModel = found
        cachedModelAt = now
        return found
    }

    private fun searchModelFile(context: Context): File? {
        val dirs = listOfNotNull(
            context.getExternalFilesDir("models"),
            File(Environment.getExternalStorageDirectory(), "Docsy/models"),
            File(Environment.getExternalStorageDirectory(), "Download")
        )
        val candidates = dirs.flatMap { d ->
            try { d.listFiles { f -> f.isFile && f.name.endsWith(".litertlm", ignoreCase = true) }?.toList().orEmpty() }
            catch (_: Throwable) { emptyList() }
        }
        return candidates.firstOrNull { it.name.contains("e2b", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains("gemma", ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private fun enoughMemory(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem >= MIN_TOTAL_RAM_BYTES
    }

    /** Cheap check the scanner can call per file: model present, phone big enough, no earlier load failure. */
    fun isReady(context: Context): Boolean {
        val ready = when {
            initFailed -> { lastStatus = "Model failed to load on this phone"; false }
            !enoughMemory(context) -> { lastStatus = "This phone has too little RAM for the vision model"; false }
            findModelFile(context) == null -> {
                lastStatus = "No model found. Put a Gemma-3n .litertlm file in /sdcard/Docsy/models/"; false
            }
            else -> { lastStatus = "Ready"; true }
        }
        return ready && remainingBudget > 0
    }

    private suspend fun ensureEngine(context: Context): Engine? {
        engine?.let { return it }
        val model = findModelFile(context) ?: return null
        val attempts = listOf(
            "GPU" to { EngineConfig(modelPath = model.absolutePath, backend = Backend.GPU(), visionBackend = Backend.GPU(), cacheDir = context.cacheDir.path) },
            "CPU" to { EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), visionBackend = Backend.CPU(), cacheDir = context.cacheDir.path) }
        )
        for ((name, config) in attempts) {
            try {
                val e = Engine(config())
                withContext(Dispatchers.IO) { e.initialize() } // can take ~10 s
                engine = e
                lastStatus = "Loaded on $name"
                Log.i(TAG, "Vision model loaded on $name: ${model.name}")
                return e
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Vision model failed on $name: ${t.message}")
            }
        }
        initFailed = true
        lastStatus = "Model failed to load on this phone"
        return null
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            mutex.withLock {
                try { engine?.close() } catch (_: Throwable) {}
                engine = null
                Log.i(TAG, "Vision model unloaded after idle")
            }
        }
    }

    /** Reads one page image and returns the model's transcription, or null if it could not. */
    suspend fun transcribe(context: Context, bitmap: Bitmap, prompt: String): String? = mutex.withLock {
        if (remainingBudget <= 0) return null
        idleJob?.cancel()
        val e = ensureEngine(context) ?: return null
        remainingBudget--

        val imageFile = File(context.cacheDir, "vlm_${System.nanoTime()}.jpg")
        try {
            withContext(Dispatchers.IO) {
                FileOutputStream(imageFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
            val sb = StringBuilder()
            val finished = withTimeoutOrNull(180_000L) {
                e.createConversation(
                    ConversationConfig(samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1))
                ).use { conversation ->
                    conversation.sendMessageAsync(
                        Contents.of(Content.ImageFile(imageFile.absolutePath), Content.Text(prompt))
                    ).collect { sb.append(it.toString()) }
                }
                true
            }
            if (finished == null) { Log.w(TAG, "Vision model timed out on a page"); return null }
            sb.toString().takeIf { it.isNotBlank() }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Vision model error: ${t.message}")
            null
        } finally {
            try { imageFile.delete() } catch (_: Throwable) {}
            scheduleIdleUnload()
        }
    }
}
