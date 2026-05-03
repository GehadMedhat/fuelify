package com.authapi.plugins

import com.authapi.services.AuthService
import com.authapi.utils.JwtUtils
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*

fun Application.configureAuthentication(authService: AuthService) {
    val jwtUtils = JwtUtils(this)
    val realm    = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("jwt-auth") {
            this.realm = realm
            verifier(jwtUtils.getVerifier())
            validate { credential ->
                val userId    = credential.payload.getClaim("userId").asInt()
                val tokenType = credential.payload.getClaim("type").asString()
                val token     = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: ""
                val blacklisted = authService.isTokenBlacklisted(token)
                if (userId != null && tokenType == "access" && !blacklisted)
                    JWTPrincipal(credential.payload)
                else null
            }
        }
    }
}
