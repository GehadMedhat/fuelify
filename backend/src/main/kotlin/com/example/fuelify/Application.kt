package com.example.fuelify

import com.example.fuelify.db.DatabaseFactory
import com.example.fuelify.plugins.configureCORS
import com.example.fuelify.plugins.configureRouting
import com.example.fuelify.plugins.configureSerialization
import com.example.fuelify.plugins.configureStatusPages
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureRouting()
}
