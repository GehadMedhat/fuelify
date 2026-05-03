package com.authapi.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Payload
import io.ktor.server.application.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class JwtUtils(private val application: Application) {

    private val secret          = application.environment.config.property("jwt.secret").getString()
    private val issuer          = application.environment.config.property("jwt.issuer").getString()
    private val audience        = application.environment.config.property("jwt.audience").getString()
    private val accessExpiry    = application.environment.config.property("jwt.accessTokenExpiry").getString().toLong()
    private val refreshExpiry   = application.environment.config.property("jwt.refreshTokenExpiry").getString().toLong()
    private val algorithm       = Algorithm.HMAC256(secret)
    private val guestExpiry = 30L * 24 * 60 * 60 * 1000

    fun generateAccessToken(userId: Int, email: String, isAdmin: Boolean = false): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("email", email)
        .withClaim("type", "access")
        .withClaim("isAdmin", isAdmin)
        .withExpiresAt(Date(System.currentTimeMillis() + accessExpiry))
        .sign(algorithm)

    fun generateRefreshToken(userId: Int, email: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("userId", userId).withClaim("email", email).withClaim("type", "refresh")
        .withExpiresAt(Date(System.currentTimeMillis() + refreshExpiry))
        .sign(algorithm)

    fun validateRefreshToken(token: String): Payload? = try {
        JWT.require(algorithm).withIssuer(issuer).withAudience(audience)
            .withClaim("type", "refresh").build().verify(token)
    } catch (e: Exception) { null }

    fun getTokenExpiry(token: String): LocalDateTime = try {
        JWT.decode(token).expiresAt.toInstant()
            .atZone(ZoneId.systemDefault()).toLocalDateTime()
    } catch (e: Exception) { LocalDateTime.now() }

    fun getVerifier() = JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()

    fun generateGuestToken(guestId: String): String = JWT.create()
        .withIssuer(issuer).withAudience(audience)
        .withClaim("guestId", guestId)
        .withClaim("type", "guest")
        .withExpiresAt(Date(System.currentTimeMillis() + guestExpiry))
        .sign(algorithm)

    fun validateGuestToken(token: String): Payload? = try {
        JWT.require(algorithm).withIssuer(issuer).withAudience(audience)
            .withClaim("type", "guest").build().verify(token)
    } catch (e: Exception) { null }
}
