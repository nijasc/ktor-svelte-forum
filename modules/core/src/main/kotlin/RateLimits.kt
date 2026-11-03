package dev.nilsschlegel.forum.core

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/** A principal that rate limits can tell apart. `AuthUser` in `accounts` is one. */
interface Actor {
    val id: Long
}

/** The named limits. Wrap routes in `rateLimit(RateLimits.AUTH)` or `rateLimit(RateLimits.WRITE)`. */
object RateLimits {
    /** Register and login, counted per client address. */
    val AUTH = RateLimitName("auth")

    /** Starting threads and posting, counted per signed-in user. Put it inside `authenticate`. */
    val WRITE = RateLimitName("write")

    const val AUTH_PER_MINUTE = 10
    const val WRITE_PER_MINUTE = 20
}

/**
 * Installs the limits. A client over its limit gets 429 with a `Retry-After` header.
 * The address is the direct peer, so behind a reverse proxy every client looks the same until forwarded headers are set up.
 */
fun Application.configureRateLimits(
    authPerMinute: Int = RateLimits.AUTH_PER_MINUTE,
    writePerMinute: Int = RateLimits.WRITE_PER_MINUTE,
) {
    install(RateLimit) {
        register(RateLimits.AUTH) {
            rateLimiter(limit = authPerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.clientAddress() }
        }
        register(RateLimits.WRITE) {
            rateLimiter(limit = writePerMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<Actor>()?.let { "user:${it.id}" } ?: call.clientAddress() }
        }
    }
}

private fun ApplicationCall.clientAddress(): String = request.origin.remoteAddress
