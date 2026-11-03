package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.Problem
import dev.nilsschlegel.forum.core.env
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Name of the session authentication. Wrap routes in `authenticate(SESSION_AUTH)` to require a signed-in user. */
const val SESSION_AUTH = "session"

private const val MIN_SECRET_LENGTH = 32
private const val MIN_PASSWORD_LENGTH = 12
private const val MAX_PASSWORD_LENGTH = 200
private const val MAX_USERNAME_LENGTH = 64
private const val SESSION_SECONDS = 60L * 60 * 24 * 7
private const val HASH_ITERATIONS = 600_000
private const val HASH_BITS = 256
private const val SALT_BYTES = 16

@Serializable
data class UserSession(
    val username: String,
)

@Serializable
data class CurrentUser(
    val username: String,
)

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

internal interface UserStore {
    suspend fun create(
        username: String,
        passwordHash: String,
    ): Boolean

    suspend fun passwordHash(username: String): String?
}

/** Installs sessions and the [SESSION_AUTH] authentication, and serves the `/api/auth` routes. */
fun Application.accounts() {
    configureAuth()
    routing {
        authRoutes(DatabaseUserStore())
    }
}

internal fun Application.configureAuth() {
    val secret = env("SESSION_SECRET") ?: temporarySecret()
    require(secret.length >= MIN_SECRET_LENGTH) { "SESSION_SECRET needs at least $MIN_SECRET_LENGTH characters." }
    install(Sessions) {
        cookie<UserSession>("session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = env("COOKIE_SECURE")?.toBoolean() ?: true
            cookie.maxAgeInSeconds = SESSION_SECONDS
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(secret.toByteArray()))
        }
    }
    install(Authentication) {
        session<UserSession>(SESSION_AUTH) {
            validate { it }
            challenge { call.respond(HttpStatusCode.Unauthorized, Problem("Sign in first.")) }
        }
    }
}

internal fun Route.authRoutes(users: UserStore) {
    val unknownUserHash = newPasswordHash(randomToken())
    route("/api/auth") {
        post("/register") {
            val credentials = call.receive<Credentials>()
            if (!users.create(credentials.username, hashPassword(credentials.password))) {
                call.respond(HttpStatusCode.Conflict, Problem("That username is taken."))
                return@post
            }
            call.sessions.set(UserSession(credentials.username))
            call.respond(HttpStatusCode.Created, CurrentUser(credentials.username))
        }
        post("/login") {
            val credentials = call.receive<Credentials>()
            val stored = users.passwordHash(credentials.username)
            val matches = verifyPassword(credentials.password, stored ?: unknownUserHash)
            if (stored == null || !matches) {
                call.respond(HttpStatusCode.Unauthorized, Problem("Wrong username or password."))
                return@post
            }
            call.sessions.set(UserSession(credentials.username))
            call.respond(CurrentUser(credentials.username))
        }
        post("/logout") {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.NoContent)
        }
        authenticate(SESSION_AUTH) {
            get("/me") {
                call.respond(CurrentUser(call.principal<UserSession>()!!.username))
            }
        }
    }
}

private fun Application.temporarySecret(): String {
    log.warn("SESSION_SECRET is not set, so everyone is signed out when the app restarts.")
    return randomToken()
}

private fun randomToken(): String {
    val bytes = ByteArray(MIN_SECRET_LENGTH)
    SecureRandom().nextBytes(bytes)
    return Base64.getEncoder().encodeToString(bytes)
}

private fun newPasswordHash(password: String): String {
    val salt = ByteArray(SALT_BYTES)
    SecureRandom().nextBytes(salt)
    val encoder = Base64.getEncoder()
    return "$HASH_ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(derive(password, salt, HASH_ITERATIONS))}"
}

private suspend fun hashPassword(password: String): String = withContext(Dispatchers.Default) { newPasswordHash(password) }

private suspend fun verifyPassword(
    password: String,
    stored: String,
): Boolean =
    withContext(Dispatchers.Default) {
        val (iterations, salt, hash) = stored.split(':')
        val decoder = Base64.getDecoder()
        MessageDigest.isEqual(decoder.decode(hash), derive(password, decoder.decode(salt), iterations.toInt()))
    }

private fun derive(
    password: String,
    salt: ByteArray,
    iterations: Int,
): ByteArray =
    SecretKeyFactory
        .getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS))
        .encoded
