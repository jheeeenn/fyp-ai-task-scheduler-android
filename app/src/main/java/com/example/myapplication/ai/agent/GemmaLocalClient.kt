package com.example.myapplication.ai.agent

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.InputStream

class GemmaLocalClient(
    private val context: Context,
    private val fallbackModelPath: String = "",
    private val timeoutMs: Long = 10_000L
) {
    companion object {
        private const val TAG = "GEMMA_LOCAL"
        private const val PREFS_NAME = "gemma_prefs"
        private const val KEY_MODEL_PATH = "model_path"
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var modelFilePath: String? = null
    private var initializing: Boolean = false

    init {
        loadSavedModelPath()
    }

    fun getModelPath(): String? {
        loadSavedModelPath()
        return modelFilePath
    }

    fun hasUsableModel(): Boolean {
        val path = getModelPath() ?: return false
        val file = File(path)
        return file.exists() && file.extension.equals("litertlm", ignoreCase = true)
    }

    fun isInitialized(): Boolean {
        return engine != null && conversation != null
    }

    fun isInitializing(): Boolean {
        return initializing
    }

    private fun saveModelPath(path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_PATH, path)
            .apply()

        modelFilePath = path
    }

    private fun loadSavedModelPath() {
        val savedPath = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_PATH, null)

        modelFilePath = when {
            !savedPath.isNullOrBlank() && File(savedPath).exists() -> savedPath
            fallbackModelPath.isNotBlank() && File(fallbackModelPath).exists() -> fallbackModelPath
            else -> null
        }
    }

    suspend fun copyModelToAppStorage(
        inputStream: InputStream,
        fileName: String
    ): String {
        return withContext(Dispatchers.IO) {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            val safeFileName = if (fileName.endsWith(".litertlm", ignoreCase = true)) {
                fileName
            } else {
                "gemma-4-E2B-it.litertlm"
            }

            val outFile = File(modelsDir, safeFileName)

            inputStream.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            saveModelPath(outFile.absolutePath)
            close()

            Log.d(TAG, "Model copied to app storage: ${outFile.absolutePath}")
            outFile.absolutePath
        }
    }

    suspend fun initializeModel(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (isInitialized()) {
                    Log.d(TAG, "Gemma model already initialized")
                    return@withContext Result.success(Unit)
                }

                if (initializing) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemma model is already initializing")
                    )
                }

                initializing = true

                val path = getModelPath()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Gemma model has not been imported yet")
                    )

                val modelFile = File(path)

                if (!modelFile.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemma model file not found at $path")
                    )
                }

                if (!modelFile.extension.equals("litertlm", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemma model must be a .litertlm file")
                    )
                }

                close()

                Log.d(TAG, "Initializing LiteRT-LM Gemma model at $path")

                val engineConfig = EngineConfig(
                    modelPath = path,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )

                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                val newConversation = newEngine.createConversation(
                    ConversationConfig()
                )

                engine = newEngine
                conversation = newConversation

                Log.d(TAG, "Gemma model initialized successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Gemma initialization failed", e)
                Result.failure(e)
            } finally {
                initializing = false
            }
        }
    }

    suspend fun generate(prompt: String): String = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            val activeConversation = conversation
                ?: throw IllegalStateException("Gemma model is not initialized yet")

            val output = StringBuilder()

            try {
                activeConversation.sendMessageAsync(prompt).collect { message ->
                    val chunk = message.toString()
                    output.append(chunk)

                    val current = output.toString()
                    if (extractCompleteJson(current) != null) {
                        throw CompleteJsonException(current)
                    }
                }
            } catch (e: CompleteJsonException) {
                return@withContext e.text
            }

            output.toString()
        }
    }

    private fun extractCompleteJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')

        if (start < 0 || end <= start) return null

        val json = text.substring(start, end + 1)

        return try {
            JSONObject(json)
            json
        } catch (_: Exception) {
            null
        }
    }

    private class CompleteJsonException(val text: String) : RuntimeException()

    fun close() {
        try {
            conversation?.close()
        } catch (_: Exception) {
        }

        try {
            engine?.close()
        } catch (_: Exception) {
        }

        conversation = null
        engine = null
    }
}