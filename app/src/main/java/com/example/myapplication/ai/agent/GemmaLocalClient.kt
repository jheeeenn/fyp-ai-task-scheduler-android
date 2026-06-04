package com.example.myapplication.ai.agent

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

class GemmaLocalClient(
    private val context: Context,
    private val modelPath: String,
    private val timeoutMs: Long = 20_000L
) {
    private var llmInference: LlmInference? = null

    suspend fun generate(prompt: String): String = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                throw IllegalStateException("Gemma model file not found at $modelPath")
            }

            val inference = llmInference ?: createInference().also { llmInference = it }
            inference.generateResponse(prompt)
        }
    }

    private fun createInference(): LlmInference {
        Log.d("GEMMA_LOCAL", "Initializing Gemma model at $modelPath")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setTopK(40)
            .setTemperature(0.2f)
            .setRandomSeed(42)
            .build()

        return LlmInference.createFromOptions(context.applicationContext, options)
    }

    fun close() {
        llmInference?.close()
        llmInference = null
    }
}
