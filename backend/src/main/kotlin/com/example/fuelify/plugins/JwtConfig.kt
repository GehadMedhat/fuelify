package com.example.fuelify.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureJWT() {
    val secret   = environment.config.property("jwt.secret").getString()
    val issuer   = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm    = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            this.realm = realm
            verifier(
                JWT.require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("type", "access")
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null)
                    JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
    call.respond(
        HttpStatusCode.Unauthorized,
        """{"success":false,"message":"Token missing or invalid","data":null}"""
    )
}
        }
    }
}

fun ApplicationCall.jwtUserId(): Int? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
