package dev.nilsschlegel.forum.search

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.core.RateLimits
import io.ktor.server.application.Application
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

private const val MAX_QUERY_LENGTH = 200
private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 50

/** Deep pages are expensive and nobody reads them, so a search stops after this many hits. */
private const val MAX_OFFSET = 200

/**
 * Serves `GET /api/search?q=` over the titles of threads and the text of posts, for everyone.
 * It reads the forum's tables with SQL, which is the one place a module does that, because the search owns
 * the query and its indexes. Hidden and deleted content never matches.
 */
fun Application.search(users: UserDirectory) {
    val store = SearchStore(users)
    routing {
        rateLimit(RateLimits.SEARCH) {
            get("/api/search") {
                val text =
                    call.request.queryParameters["q"]
                        .orEmpty()
                        .trim()
                if (text.length > MAX_QUERY_LENGTH) throw BadRequestException("The search is too long.")
                val limit =
                    call.request.queryParameters["limit"]?.let {
                        it.toIntOrNull() ?: throw BadRequestException(
                            "Not a valid limit.",
                        )
                    }
                val offset =
                    call.request.queryParameters["cursor"]?.let {
                        it.toIntOrNull()?.takeIf { n -> n >= 0 }
                            ?: throw BadRequestException("Not a valid cursor.")
                    }
                if (text.isEmpty() || (offset ?: 0) >= MAX_OFFSET) {
                    call.respond(SearchPage(emptyList(), null))
                    return@get
                }
                call.respond(store.search(text, offset ?: 0, (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)))
            }
        }
    }
}
