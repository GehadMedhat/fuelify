package com.authapi.services

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

class SupabaseStorageService(
    private val supabaseUrl: String,
    private val supabaseKey: String,
    private val bucket: String
) {
    private val logger = LoggerFactory.getLogger(SupabaseStorageService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, folder: String = "general"): String {
        val fileName = "$folder/${UUID.randomUUID()}.jpg"

        logger.info("Uploading image to Supabase: $fileName")

        val response = client.put("$supabaseUrl/storage/v1/object/$bucket/$fileName") {
            header("Authorization", "Bearer $supabaseKey")
            header("x-upsert", "true")
            contentType(ContentType.Image.JPEG)
            setBody(imageBytes)
        }

        logger.info("Supabase upload status: ${response.status}")

        if (response.status != HttpStatusCode.OK && response.status.value != 200) {
            val errorBody = response.bodyAsText()
            logger.error("Supabase upload error: $errorBody")
            throw Exception("Image upload failed: $errorBody")
        }

        val publicUrl = "$supabaseUrl/storage/v1/object/public/$bucket/$fileName"
        logger.info("Image uploaded successfully: $publicUrl")
        return publicUrl
    }
}