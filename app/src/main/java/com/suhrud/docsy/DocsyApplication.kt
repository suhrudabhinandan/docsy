package com.suhrud.docsy

import android.app.Application
import com.suhrud.docsy.util.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DocsyApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashReporter.init(this)
        com.suhrud.docsy.retrieval.ChitchatEmbeddingEngine.initFromContext(this)
    }

    companion object {
        lateinit var instance: DocsyApplication
            private set
    }
}
