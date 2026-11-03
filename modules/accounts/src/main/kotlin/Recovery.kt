package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.ApiException
import dev.nilsschlegel.forum.core.Problem
import dev.nilsschlegel.forum.core.RateLimits
import dev.nilsschlegel.forum.mail.MailException
import dev.nilsschlegel.forum.mail.MailMessage
import dev.nilsschlegel.forum.mail.MailSender
import io.github.nijasc.ktor.svelte.Validated
import io.github.nijasc.ktor.svelte.Validator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.Logger

private const val INVALID_LINK = "This link is not valid or has expired."

@Serializable
data class VerifyEmail(
    val token: String,
)

@Serializable
data class ForgotPassword(
    val email: String,
)

@Serializable
data class ResetPassword(
    val token: String,
    val password: String,
) : Validated {
    override fun Validator.rules() {
        check("password", password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Use a password of $MIN_PASSWORD_LENGTH to $MAX_PASSWORD_LENGTH characters."
        }
    }
}

@Serializable
data class ChangeEmail(
    val email: String,
    val password: String,
) : Validated {
    override fun Validator.rules() {
        check("email", isEmail(email)) { "Enter a valid email address." }
    }
}

/** Confirming addresses and resetting passwords. Mail that fails to send is logged, and the user can ask again. */
internal class Recovery(
    private val users: UserStore,
    private val tokens: TokenStore,
    private val mail: MailSender,
    private val publicUrl: String,
    private val log: Logger,
) {
    suspend fun sendVerification(
        userId: Long,
        email: String,
    ) {
        val token = tokens.issue(userId, TokenPurpose.VERIFY, email)
        send(
            MailMessage(
                to = email,
                subject = "Confirm your email address",
                text =
                    "Open this link to confirm your email address:\n\n$publicUrl/verify?token=$token\n\n" +
                        "The link works for 3 days. If you did not sign up, ignore this message.",
            ),
        )
    }

    suspend fun sendReset(
        userId: Long,
        email: String,
    ) {
        val token = tokens.issue(userId, TokenPurpose.RESET, null)
        send(
            MailMessage(
                to = email,
                subject = "Reset your password",
                text =
                    "Open this link to choose a new password:\n\n$publicUrl/reset-password?token=$token\n\n" +
                        "The link works for 1 hour. If you did not ask for this, ignore this message and your password stays as it is.",
            ),
        )
    }

    suspend fun verify(token: String): Boolean {
        val owner = tokens.consume(token, TokenPurpose.VERIFY) ?: return false
        val user = users.findById(owner.userId) ?: return false
        // The address may have been changed since the link was sent.
        if (!user.email.equals(owner.email, ignoreCase = true)) return false
        users.markVerified(user.id)
        return true
    }

    suspend fun reset(
        token: String,
        passwordHash: String,
    ): Boolean {
        val owner = tokens.consume(token, TokenPurpose.RESET) ?: return false
        users.setPassword(owner.userId, passwordHash)
        // Receiving the link proves the address belongs to them.
        users.markVerified(owner.userId)
        tokens.revokeAll(owner.userId, TokenPurpose.RESET)
        return true
    }

    private suspend fun send(message: MailMessage) {
        try {
            mail.send(message)
        } catch (failure: MailException) {
            log.warn("Could not send \"{}\" to {}: {}", message.subject, message.to, failure.message)
        }
    }
}

internal fun Route.recoveryRoutes(
    users: UserStore,
    recovery: Recovery,
) {
    route("/api/auth") {
        rateLimit(RateLimits.AUTH) {
            post("/verify") {
                val body = call.receive<VerifyEmail>()
                if (!recovery.verify(body.token)) throw ApiException(HttpStatusCode.BadRequest, INVALID_LINK)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/forgot-password") {
                val email = call.receive<ForgotPassword>().email.trim()
                // The answer is the same whether or not the address belongs to someone, so it cannot be used to look people up.
                // The response time can still differ slightly, because sending mail takes a moment.
                val user = users.findByEmail(email)
                if (user != null && user.status == UserStatus.ACTIVE) recovery.sendReset(user.id, checkNotNull(user.email))
                call.respond(HttpStatusCode.NoContent)
            }
            post("/reset-password") {
                val body = call.receive<ResetPassword>()
                if (!recovery.reset(body.token, hashPassword(body.password))) throw ApiException(HttpStatusCode.BadRequest, INVALID_LINK)
                call.respond(HttpStatusCode.NoContent)
            }
        }
        authenticate(SESSION_AUTH) {
            rateLimit(RateLimits.AUTH) {
                post("/verification/resend") {
                    val user = users.findById(call.requireUser().id)!!
                    val email = user.email
                    if (!user.emailVerified && email != null) recovery.sendVerification(user.id, email)
                    call.respond(HttpStatusCode.NoContent)
                }
                put("/email") {
                    val auth = call.requireUser()
                    val body = call.receive<ChangeEmail>()
                    val user = users.findById(auth.id)!!
                    if (!verifyPassword(body.password, user.passwordHash)) {
                        call.respond(HttpStatusCode.Forbidden, Problem("Wrong password."))
                        return@put
                    }
                    val email = body.email.trim()
                    if (!users.changeEmail(
                            user.id,
                            email,
                        )
                    ) {
                        throw ApiException(HttpStatusCode.Conflict, "That email address is already in use.")
                    }
                    recovery.sendVerification(user.id, email)
                    call.respond(users.findById(user.id)!!.toCurrentUser())
                }
            }
        }
    }
}

private fun ApplicationCall.requireUser(): AuthUser =
    principal<AuthUser>() ?: throw ApiException(HttpStatusCode.Unauthorized, "Sign in first.")
