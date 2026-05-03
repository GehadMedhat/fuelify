package com.authapi.services

import com.authapi.models.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class ChatService(
    private val groqApiKey: String
) {
    private val logger = LoggerFactory.getLogger(ChatService::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"

    // ─────────────────────────────────────────────────────────────────────────
    // Send Message
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun sendMessage(userId: Int, req: SendMessageRequest): ApiResponse<ChatResponse> {
        if (req.message.isBlank())
            throw ValidationException(listOf("Message cannot be empty"))

        val now = LocalDateTime.now()

        // 1. Save user message
        val userChatId = transaction {
            AiChat.insert {
                it[AiChat.userId]    = userId
                it[AiChat.message]   = req.message
                it[AiChat.sender]    = "user"
                it[AiChat.timestamp] = now
            } get AiChat.chatId
        }

        // 2. Build health context
        val healthContext = buildHealthContext(userId)

        // 3. Get last 10 messages for conversation history
        val history = transaction {
            AiChat.selectAll()
                .where { AiChat.userId eq userId }
                .orderBy(AiChat.timestamp, SortOrder.DESC)
                .limit(10)
                .toList()
                .reversed()
                .map {
                    GroqMessage(
                        role    = if (it[AiChat.sender] == "user") "user" else "assistant",
                        content = it[AiChat.message]
                    )
                }
        }

        // 4. Call Groq
        val auraReply = callGroq(req.message, healthContext, history)

        // 5. Save Aura reply
        val auraNow = LocalDateTime.now()
        val auraChatId = transaction {
            AiChat.insert {
                it[AiChat.userId]    = userId
                it[AiChat.message]   = auraReply
                it[AiChat.sender]    = "aura"
                it[AiChat.timestamp] = auraNow
            } get AiChat.chatId
        }

        return ApiResponse(
            true, "Message sent",
            ChatResponse(
                userMessage = ChatMessageResponse(
                    chatId    = userChatId,
                    message   = req.message,
                    sender    = "user",
                    timestamp = now.toString()
                ),
                auraReply = ChatMessageResponse(
                    chatId    = auraChatId,
                    message   = auraReply,
                    sender    = "aura",
                    timestamp = auraNow.toString()
                )
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat History
    // ─────────────────────────────────────────────────────────────────────────

    fun getChatHistory(userId: Int, limit: Int = 50, offset: Long = 0): ApiResponse<ChatHistoryResponse> {
        val messages = transaction {
            AiChat.selectAll()
                .where { AiChat.userId eq userId }
                .orderBy(AiChat.timestamp, SortOrder.DESC)
                .limit(limit)
                .toList()
                .reversed()
                .map {
                    ChatMessageResponse(
                        chatId    = it[AiChat.chatId],
                        message   = it[AiChat.message],
                        sender    = it[AiChat.sender],
                        timestamp = it[AiChat.timestamp].toString()
                    )
                }
        }

        return ApiResponse(
            true, "Chat history retrieved",
            ChatHistoryResponse(messages = messages, total = messages.size)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear Chat History
    // ─────────────────────────────────────────────────────────────────────────

    fun clearHistory(userId: Int): ApiResponse<Unit> {
        transaction {
            AiChat.deleteWhere { AiChat.userId eq userId }
        }
        return ApiResponse(success = true, message = "Chat history cleared", data = Unit)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Quick Questions
    // ─────────────────────────────────────────────────────────────────────────

    fun getQuickQuestions(): ApiResponse<QuickQuestionsResponse> {
        val categories = listOf(
            QuickQuestionCategory(
                category  = "Wellness",
                questions = listOf(
                    QuickQuestionItem("Daily health summary", "Give me a summary of my health today"),
                    QuickQuestionItem("Mood Tracking", "How can I track and improve my mood?")
                )
            ),
            QuickQuestionCategory(
                category  = "Fitness",
                questions = listOf(
                    QuickQuestionItem("Workout tips", "Give me some workout tips for today"),
                    QuickQuestionItem("Symptom check", "I want to do a quick symptom check")
                )
            ),
            QuickQuestionCategory(
                category  = "Your Favorites",
                questions = listOf(
                    QuickQuestionItem("Show last week's sleep data", "Show me my sleep data from last week"),
                    QuickQuestionItem("Log my 5k run", "I just completed a 5k run, help me log it"),
                    QuickQuestionItem("Remind me to stretch at 3 PM", "Set a reminder for me to stretch at 3 PM")
                )
            )
        )
        return ApiResponse(true, "Quick questions retrieved", QuickQuestionsResponse(categories))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Groq API Call
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun callGroq(
        userMessage: String,
        healthContext: String,
        conversationHistory: List<GroqMessage>
    ): String {
        val systemPrompt = """
            You are Aura, a friendly and knowledgeable health and wellness AI assistant
            specialized in diet, nutrition, fitness, and workout planning.
            
            Your expertise includes:
            - Personalized meal plans and nutrition advice
            - Workout routines for all fitness levels
            - Calorie tracking and macronutrient guidance
            - Sleep optimization and recovery tips
            - Hydration and supplement recommendations
            - Weight management strategies
            - Healthy habit formation
            
            Always be supportive, motivating, and provide practical evidence-based advice.
            Keep responses concise, friendly, and actionable.
            Use the user's health data to personalize your responses.
            
            Here is the user's current health data:
            $healthContext
        """.trimIndent()

        // Build messages: system + history + new user message
        val messages = mutableListOf<GroqMessage>()
        messages.add(GroqMessage(role = "system", content = systemPrompt))
        messages.addAll(conversationHistory)
        messages.add(GroqMessage(role = "user", content = userMessage))

        return try {
            logger.info("Calling Groq API...")

            val response = httpClient.post(groqUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $groqApiKey")
                setBody(
                    GroqRequest(
                        model       = "llama-3.1-8b-instant",
                        messages    = messages,
                        max_tokens  = 1024,
                        temperature = 0.7
                    )
                )
            }

            logger.info("Groq response status: ${response.status}")

            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                logger.error("Groq error body: $errorBody")
                return "I'm sorry, I couldn't generate a response. Please try again."
            }

            val groqResponse = response.body<GroqResponse>()
            groqResponse.choices
                .firstOrNull()
                ?.message
                ?.content
                ?: "I'm sorry, I couldn't generate a response. Please try again."

        } catch (e: Exception) {
            logger.error("Groq API error: ${e.message}", e)
            "I'm sorry, I'm having trouble connecting right now. Please try again in a moment."
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Build Health Context
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildHealthContext(userId: Int): String {
        val today = LocalDate.now()

        return transaction {

            val user = Users.selectAll()
                .where { Users.id eq userId }
                .singleOrNull()

            buildString {
                if (user != null) {
                    appendLine("User Profile:")
                    appendLine("- Name: ${user[Users.firstName] ?: "User"}")
                    appendLine("- Points: ${user[Users.points]}")
                }

            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Groq API Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable data class GroqRequest(
    val model       : String,
    val messages    : List<GroqMessage>,
    val max_tokens  : Int    = 1024,
    val temperature : Double = 0.7
)

@Serializable data class GroqMessage(
    val role    : String,
    val content : String
)

@Serializable data class GroqResponse(
    val choices: List<GroqChoice>
)

@Serializable data class GroqChoice(
    val message: GroqMessage
)