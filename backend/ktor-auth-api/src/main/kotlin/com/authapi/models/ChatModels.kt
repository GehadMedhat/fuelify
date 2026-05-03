package com.authapi.models

import kotlinx.serialization.Serializable

// ─── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class SendMessageRequest(
    val message: String
)

// ─── Response DTOs ────────────────────────────────────────────────────────────

@Serializable
data class ChatMessageResponse(
    val chatId: Int,
    val message: String,
    val sender: String,     // "user" | "aura"
    val timestamp: String
)

@Serializable
data class ChatResponse(
    val userMessage: ChatMessageResponse,
    val auraReply: ChatMessageResponse
)

@Serializable
data class ChatHistoryResponse(
    val messages: List<ChatMessageResponse>,
    val total: Int
)

// ─── Quick Questions ──────────────────────────────────────────────────────────

@Serializable
data class QuickQuestionItem(
    val label: String,
    val message: String     // the actual message sent to Aura when tapped
)

@Serializable
data class QuickQuestionCategory(
    val category: String,
    val questions: List<QuickQuestionItem>
)

@Serializable
data class QuickQuestionsResponse(
    val categories: List<QuickQuestionCategory>
)
