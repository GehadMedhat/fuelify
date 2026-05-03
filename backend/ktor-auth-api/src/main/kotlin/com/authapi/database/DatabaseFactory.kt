package com.authapi.database

import com.authapi.models.AiChat
import com.authapi.models.GroceryItems
import com.authapi.models.GroceryLists
import com.authapi.models.GuestSessions
import com.authapi.models.LoginAttempts
import com.authapi.models.NotificationSettings
import com.authapi.models.Notifications
import com.authapi.models.SavedRecipes
import com.authapi.models.TokenBlacklist
import com.authapi.models.Users
import com.authapi.models.VerificationTokens
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(application: Application) {
        val dbMode = application.environment.config
            .propertyOrNull("database.mode")?.getString() ?: "h2"

        val database = if (dbMode == "postgres") initPostgres(application) else initH2()

        transaction(database) {
            // ✅ SAFE — only creates MISSING tables/columns
            // NEVER drops, deletes, or modifies existing tables or data
            SchemaUtils.createMissingTablesAndColumns(
                Users, VerificationTokens, TokenBlacklist, LoginAttempts,
                NotificationSettings, Notifications,
                GuestSessions, SavedRecipes, GroceryLists, GroceryItems, AiChat
            )
        }
        logger.info("✅ Database initialized in $dbMode mode")
    }

    private fun initH2(): Database {
        logger.info("📦 Using H2 local database (test mode)")
        return Database.connect(
            url = "jdbc:h2:file:./data/authdb;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "sa",
            password = ""
        )
    }

    private fun initPostgres(application: Application): Database {
        logger.info("🐘 Connecting to PostgreSQL...")
        val config = application.environment.config
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.property("database.url").getString()
            username = config.property("database.user").getString()
            password = config.property("database.password").getString()
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.propertyOrNull("database.poolSize")
                ?.getString()?.toInt() ?: 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return Database.connect(HikariDataSource(hikariConfig))
    }
}
