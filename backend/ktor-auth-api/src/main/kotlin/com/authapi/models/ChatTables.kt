package com.authapi.models

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

// ─── AI Chat ──────────────────────────────────────────────────────────────────
// Maps to existing "ai_chat" table
// sender = "user" or "aura"

object AiChat : Table("ai_chat") {
    val chatId    = integer("chat_id").autoIncrement()
    val userId    = integer("user_id").references(Users.id)
    val message   = text("message")
    val sender    = varchar("sender", 50)   // "user" | "aura"
    val timestamp = datetime("timestamp")

    override val primaryKey = PrimaryKey(chatId)
}
