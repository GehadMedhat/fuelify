package com.authapi.utils

import com.authapi.models.SetNewPasswordRequest
import com.authapi.models.SignUpRequest

object ValidationUtils {

    fun validateSignUp(request: SignUpRequest): List<String> {
        val errors = mutableListOf<String>()
        when {
            request.username.isBlank() -> errors.add("Username is required")
            request.username.length < 3 -> errors.add("Username must be at least 3 characters")
            request.username.length > 30 -> errors.add("Username must be at most 30 characters")
            !request.username.matches(Regex("^[a-zA-Z0-9_]+$")) ->
                errors.add("Username can only contain letters, numbers, and underscores")
        }
        if (request.email.isBlank()) errors.add("Email is required")
        else if (!isValidEmail(request.email)) errors.add("Invalid email format")
        errors.addAll(validatePassword(request.password))
        return errors
    }

    fun validatePasswordReset(request: SetNewPasswordRequest): List<String> {
        val errors = mutableListOf<String>()
        if (request.resetToken.isBlank()) errors.add("Reset token is required")
        errors.addAll(validatePassword(request.newPassword))
        if (request.newPassword != request.confirmPassword) errors.add("Passwords do not match")
        return errors
    }

    fun validatePassword(password: String): List<String> {
        val errors = mutableListOf<String>()
        if (password.isBlank()) {
            errors.add("Password is required")
        } else {
            if (password.length < 8) errors.add("Password must be at least 8 characters")
            if (!password.any { it.isUpperCase() }) errors.add("Password must contain at least one uppercase letter")
            if (!password.any { it.isDigit() }) errors.add("Password must contain at least one number")
        }
        return errors
    }

    private fun isValidEmail(email: String): Boolean {
        val allowedDomains = listOf(
            "gmail.com",
            "outlook.com",
            "hotmail.com",
            "outlook.sa",
            "hotmail.sa",
            "live.com"
        )
        val emailRegex = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
        if (!email.matches(emailRegex)) return false
        val domain = email.substringAfter("@").lowercase()
        return domain in allowedDomains
    }

    fun isValidVisibility(visibility: String): Boolean =
        visibility in listOf("PUBLIC", "PRIVATE", "FRIENDS")
}


fun String.sanitize(): String = this.trim()
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#x27;")
