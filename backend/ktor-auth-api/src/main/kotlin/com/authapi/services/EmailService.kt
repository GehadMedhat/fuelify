package com.authapi.services

import io.ktor.server.application.*
import org.slf4j.LoggerFactory
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailService(private val application: Application) {

    private val logger   = LoggerFactory.getLogger(EmailService::class.java)
    private val useMock  = application.environment.config.propertyOrNull("email.mock")?.getString()?.toBoolean() ?: true
    private val host     = application.environment.config.propertyOrNull("email.host")?.getString() ?: "smtp.gmail.com"
    private val port     = application.environment.config.propertyOrNull("email.port")?.getString() ?: "587"
    private val username = application.environment.config.propertyOrNull("email.username")?.getString() ?: ""
    private val password = application.environment.config.propertyOrNull("email.password")?.getString() ?: ""
    private val fromName = application.environment.config.propertyOrNull("email.fromName")?.getString() ?: "Auth API"

    // ── Email Verification OTP ────────────────────────────────────────────────
    fun sendVerificationCode(toEmail: String, code: String) {
        sendEmail(
            to      = toEmail,
            subject = "✉️ Your verification code",
            body    = otpTemplate(
                title   = "Verify your email 👋",
                message = "Use the code below to verify your email address. It expires in <strong>15 minutes</strong>.",
                code    = code,
                footer  = "If you didn't create an account, you can safely ignore this email."
            )
        )
    }

    // ── Password Reset OTP ────────────────────────────────────────────────────
    fun sendPasswordResetCode(toEmail: String, code: String) {
        sendEmail(
            to      = toEmail,
            subject = "🔐 Your password reset code",
            body    = otpTemplate(
                title   = "Reset your password 🔑",
                message = "Use the code below to reset your password. It expires in <strong>15 minutes</strong>.",
                code    = code,
                footer  = "If you didn't request a password reset, you can safely ignore this email."
            )
        )
    }

    // ── send EmailChange OTP ────────────────────────────────────────────────────

    fun sendEmailChangeOtp(toEmail: String, code: String) {
        sendEmail(
            to      = toEmail,
            subject = "📧 Confirm your new email address",
            body    = otpTemplate(
                title   = "Confirm Email Change",
                message = "Use the code below to confirm your new email address. It expires in <strong>15 minutes</strong>.",
                code    = code,
                footer  = "If you didn't request this change, please secure your account immediately."
            )
        )
    }

    // ── OTP Template ──────────────────────────────────────────────────────────
    private fun otpTemplate(
        title: String,
        message: String,
        code: String,
        footer: String
    ) = """
        <html><body style="font-family:Arial,sans-serif;background:#f0f2f5;padding:30px">
        <div style="max-width:560px;margin:auto;background:white;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1)">
            <div style="background:#4CAF50;padding:30px;text-align:center">
                <h1 style="color:white;margin:0;font-size:24px">$title</h1>
            </div>
            <div style="padding:30px">
                <p style="color:#555;font-size:16px;line-height:1.6">$message</p>
                <div style="text-align:center;margin:30px 0">
                    <div style="display:inline-block;background:#f0f2f5;border-radius:12px;padding:20px 40px">
                        <span style="font-size:42px;font-weight:bold;letter-spacing:12px;color:#2c2c2c;font-family:monospace">$code</span>
                    </div>
                </div>
                <p style="color:#888;font-size:13px;text-align:center">Enter this code in the app to continue.</p>
            </div>
            <div style="background:#f8f8f8;padding:20px;text-align:center;border-top:1px solid #eee">
                <p style="color:#aaa;font-size:12px;margin:0">$footer</p>
            </div>
        </div>
        </body></html>
    """.trimIndent()

    // ── Core Send ─────────────────────────────────────────────────────────────
    private fun sendEmail(to: String, subject: String, body: String) {
        if (useMock) {
            logger.info("📧 [MOCK EMAIL] To: $to | Subject: $subject")
            logger.info("📧 [MOCK EMAIL BODY]\n$body")
            return
        }
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", host)
                put("mail.smtp.port", port)
                put("mail.smtp.ssl.trust", host)
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })
            MimeMessage(session).apply {
                setFrom(InternetAddress(username, fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setContent(body, "text/html; charset=utf-8")
                Transport.send(this)
            }
            logger.info("✅ Email sent to $to")
        } catch (e: Exception) {
            logger.error("❌ Failed to send email to $to: ${e.message}")
            throw RuntimeException("Failed to send email: ${e.message}")
        }
    }
}
