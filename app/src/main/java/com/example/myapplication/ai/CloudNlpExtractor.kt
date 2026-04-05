package com.example.myapplication.ai

interface CloudNlpExtractor {
    suspend fun extract(normalizedText: String): AiParsedCommand
}