package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.Problem
import dev.nilsschlegel.forum.core.RateLimits
import dev.nilsschlegel.forum.core.env
import dev.nilsschlegel.forum.mail.MailSender
import io.github.nijasc.ktor.svelte.Validated
import io.github.nijasc.ktor.svelte.Validator
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.Serializable

/** Name of the session authentication. Wrap routes in `authenticate(SESSION_AUTH)` to require a signed-in user. */
const val SESSION_AUTH = "session"

private const val DEFAULT_PUBLIC_URL = "http://localhost:8080"
private const val MIN_SECRET_LENGTH = 32
internal const val MIN_PASSWORD_LENGTH = 12
internal const val MAX_PASSWORD_LENGTH = 200
private const val MAX_USERNAME_LENGTH = 64
internal const val MAX_EMAIL_LENGTH = 254
private const val SESSION_SECONDS = 60L * 60 * 24 * 7
private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

/**
 * [issuedAt] is when the session started, in epoch milliseconds. A password change ends every earlier session.
 *
 * It has no default on purpose. A default is left out of the cookie when it equals the value, and is then made up
 * again as "now" when the cookie is read, which would make every session look newer than a password change.
 * Make sessions with [newSession].
 */
@Serializable
data class UserSession(
    val userId: Long,
    val issuedAt: Long,
)

internal fun newSession(userId: Long) = UserSession(userId, System.currentTimeMillis())

@Serializable
data class CurrentUser(
    val id: Long,
    val username: String,
    val role: Role,
    val email: String? = null,
    val emailVerified: Boolean = false,
)

/** What signing in needs. */
@Serializable
data class Credentials(
    val username: String,
    val password: String,
) : Validated {
    override fun Validator.rules() {
        check("username", username.isNotBlank() && username.length <= MAX_USERNAME_LENGTH) {
            "Use a username of up to $MAX_USERNAME_LENGTH characters."
        }
        check("password", password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Use a password of $MIN_PASSWORD_LENGTH to $MAX_PASSWORD_LENGTH characters."
        }
    }
}

/** What creating an account needs. The address gets a confirmation link, and writing needs it confirmed. */
@Serializable
data class Registration(
    val username: String,
    val password: String,
    val email: String,
) : Validated {
    override fun Validator.rules() {
        check("username", username.isNotBlank() && username.length <= MAX_USERNAME_LENGTH) {
            "Use a username of up to $MAX_USERNAME_LENGTH characters."
        }
        check("password", password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "Use a password of $MIN_PASSWORD_LENGTH to $MAX_PASSWORD_LENGTH characters."
        }
        check("email", isEmail(email)) { "Enter a valid email address." }
    }
}

internal fun isEmail(value: String) = value.length <= MAX_EMAIL_LENGTH && EMAIL.matches(value.trim())

/**
 * Installs sessions and the [SESSION_AUTH] authentication, and serves the `/api/auth` and `/api/users` routes.
 * [publicUrl] is the address links in emails point to. The other settings default to `PUBLIC_URL`, `COOKIE_SECURE`
 * and `INITIAL_ADMIN`, and tests pass their own.
 */
fun Application.accounts(
    mail: MailSender,
    publicUrl: String = env("PUBLIC_URL") ?: DEFAULT_PUBLIC_URL,
    secureCookie: Boolean = env("COOKIE_SECURE")?.toBoolean() ?: true,
    initialAdmin: String? = env("INITIAL_ADMIN"),
): Accounts {
    val store = DatabaseUserStore(initialAdmin)
    val recovery = Recovery(store, TokenStore(), mail, publicUrl.trimEnd('/'), log)
    configureAuth(store, secureCookie)
    routing {
        authRoutes(store, recovery)
        recoveryRoutes(store, recovery)
        profileRoutes(store)
    }
    return store
}

internal fun Application.configureAuth(
    users: UserStore,
    secureCookie: Boolean,
) {
    val secret = env("SESSION_SECRET") ?: temporarySecret()
    require(secret.length >= MIN_SECRET_LENGTH) { "SESSION_SECRET needs at least $MIN_SECRET_LENGTH characters." }
    install(Sessions) {
        cookie<UserSession>("session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = secureCookie
            cookie.maxAgeInSeconds = SESSION_SECONDS
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(secret.toByteArray()))
        }
    }
    install(Authentication) {
        session<UserSession>(SESSION_AUTH) {
            // Load the user on every request, so a changed role, a ban or a new password applies at once.
            validate { session ->
                users
                    .findById(session.userId)
                    ?.takeIf { it.status == UserStatus.ACTIVE && !it.endedSessionsBefore(session.issuedAt) }
                    ?.toAuthUser()
            }
            challenge { call.respond(HttpStatusCode.Unauthorized, Problem("Sign in first.")) }
        }
    }
}

private fun UserRecord.endedSessionsBefore(issuedAt: Long) = passwordChangedAt?.let { issuedAt < it.toEpochMilli() } == true

internal fun Route.authRoutes(
    users: UserStore,
    recovery: Recovery,
) {
    val unknownUserHash = newPasswordHash(randomToken())
    route("/api/auth") {
        rateLimit(RateLimits.AUTH) {
            post("/register") {
                val registration = call.receive<Registration>()
                val email = registration.email.trim()
                when (val result = users.create(registration.username.trim(), email, hashPassword(registration.password))) {
                    CreateResult.UsernameTaken -> call.respond(HttpStatusCode.Conflict, Problem("That username is taken."))
                    CreateResult.EmailTaken -> call.respond(HttpStatusCode.Conflict, Problem("That email address is already in use."))
                    is CreateResult.Created -> {
                        if (!result.user.emailVerified) recovery.sendVerification(result.user.id, email)
                        call.sessions.set(newSession(result.user.id))
                        call.respond(HttpStatusCode.Created, result.user.toCurrentUser())
                    }
                }
            }
            post("/login") {
                val credentials = call.receive<Credentials>()
                val user = users.findByUsername(credentials.username.trim())
                val matches = verifyPassword(credentials.password, user?.passwordHash ?: unknownUserHash)
                if (user == null || !matches) {
                    call.respond(HttpStatusCode.Unauthorized, Problem("Wrong username or password."))
                    return@post
                }
                if (user.status != UserStatus.ACTIVE) {
                    call.respond(HttpStatusCode.Forbidden, Problem("This account is suspended."))
                    return@post
                }
                call.sessions.set(newSession(user.id))
                call.respond(user.toCurrentUser())
            }
        }
        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.NoContent)
        }
        authenticate(SESSION_AUTH) {
            get("/me") {
                val auth = call.principal<AuthUser>()!!
                call.respond(users.findById(auth.id)!!.toCurrentUser())
            }
        }
    }
}

internal fun UserRecord.toCurrentUser() = CurrentUser(id, username, role, email, emailVerified)

private fun Application.temporarySecret(): String {
    log.warn("SESSION_SECRET is not set, so everyone is signed out when the app restarts.")
    return randomToken()
}
