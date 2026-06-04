package com.example.myapplication.ai.agent

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class GemmaLocalClient(
    private val context: Context,
    private val modelPath: String,
    private val timeoutMs: Long = 20_000L
) {
    private var engine: Engine? = null

    suspend fun generate(prompt: String): String = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw IllegalStateException("Gemma .litertlm model file not found at $modelPath")
            }
            if (!modelFile.extension.equals("litertlm", ignoreCase = true)) {
                throw IllegalStateException("Gemma model must be a .litertlm file: $modelPath")
            }

            val activeEngine = engine ?: createEngine().also { engine = it }
            activeEngine.createConversation().use { conversation ->
                conversation.sendMessage(prompt).text
            }
        }
    }

    private fun createEngine(): Engine {
        Log.d("GEMMA_LOCAL", "Initializing LiteRT-LM Gemma model at $modelPath")
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.path
        )

        return Engine(engineConfig).also { engine ->
            engine.initialize()
        }
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
