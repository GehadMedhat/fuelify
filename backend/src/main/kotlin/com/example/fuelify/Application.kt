package com.example.fuelify

import com.example.fuelify.db.DatabaseFactory
import com.example.fuelify.plugins.configureCORS
import com.example.fuelify.plugins.configureJWT
import com.example.fuelify.plugins.configureRouting
import com.example.fuelify.plugins.configureSerialization
import com.example.fuelify.plugins.configureStatusPages
import com.example.fuelify.plugins.configureMonitoring
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.defaultheaders.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
    }
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureJWT()
    val metricsRegistry = configureMonitoring()
    configureRouting(metricsRegistry)
}

