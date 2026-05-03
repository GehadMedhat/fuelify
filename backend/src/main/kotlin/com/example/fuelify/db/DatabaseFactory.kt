package com.example.fuelify.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import com.example.fuelify.models.*

object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val dbUrl      = config.property("database.url").getString()
        val dbUser     = config.property("database.user").getString()
        val dbPassword = config.property("database.password").getString()
        val maxPool    = config.property("database.maxPoolSize").getString().toInt()

        val hikari = HikariConfig().apply {
            jdbcUrl              = dbUrl
            username             = dbUser
            password             = dbPassword
            maximumPoolSize      = maxPool
            driverClassName      = "org.postgresql.Driver"
            isAutoCommit         = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        Database.connect(HikariDataSource(hikari))

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                DailyLogs,
                MealPlans
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
