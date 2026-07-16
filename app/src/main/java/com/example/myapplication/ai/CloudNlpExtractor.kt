package com.example.myapplication.ai

@Deprecated("CloudNlpExtractor is legacy FYP1 extraction plumbing. FYP2 production uses the Task Agent protocol.")
interface CloudNlpExtractor {
    suspend fun extract(normalizedText: String): AiParsedCommand
}
