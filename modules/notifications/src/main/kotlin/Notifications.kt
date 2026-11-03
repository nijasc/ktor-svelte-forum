package dev.nilsschlegel.forum.notifications

import dev.nilsschlegel.forum.accounts.Accounts
import dev.nilsschlegel.forum.accounts.Role
import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.requireRole
import dev.nilsschlegel.forum.core.EventBus
import dev.nilsschlegel.forum.core.RateLimits
import dev.nilsschlegel.forum.forum.PostCreated
import dev.nilsschlegel.forum.forum.ThreadCreated
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException

private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 100
private const val FOREIGN_KEY_VIOLATION = "23503"

/**
 * Turns forum events into notifications and serves them under `/api/notifications`, plus watching a thread under
 * `/api/threads/{id}/watch`. Whoever starts a thread or replies to it watches it, and watchers hear about replies.
 * Needs [dev.nilsschlegel.forum.accounts.accounts] installed first, and [events] must be the bus the forum publishes on.
 */
fun Application.notifications(
    accounts: Accounts,
    events: EventBus,
) {
    val store = NotificationStore(accounts)

    events.on<ThreadCreated> { thread ->
        store.watch(thread.authorId, thread.threadId)
        for (userId in mentionedUsers(accounts, thread.body, except = thread.authorId)) {
            store.notify(userId, Kind.MENTION, thread.authorId, thread.threadId, thread.title, postId = 0)
        }
    }

    events.on<PostCreated> { post ->
        store.watch(post.authorId, post.threadId)
        val mentioned = mentionedUsers(accounts, post.body, except = post.authorId)
        for (userId in mentioned) {
            store.notify(userId, Kind.MENTION, post.authorId, post.threadId, post.threadTitle, post.postId)
        }
        // Someone who is both watching and mentioned gets the mention only.
        for (userId in store.watchers(post.threadId) - post.authorId - mentioned) {
            store.notify(userId, Kind.REPLY, post.authorId, post.threadId, post.threadTitle, post.postId)
        }
    }

    routing { notificationRoutes(store) }
}

private suspend fun mentionedUsers(
    accounts: Accounts,
    body: String,
    except: Long,
): Set<Long> = mentionsIn(body).mapNotNull { accounts.idOf(it) }.toSet() - except

internal fun Route.notificationRoutes(store: NotificationStore) {
    authenticate(SESSION_AUTH) {
        route("/api/notifications") {
            get {
                val user = call.requireRole(Role.MEMBER)
                call.respond(store.page(user.id, call.cursor(), call.limit()))
            }
            get("/unread") {
                call.respond(UnreadCount(store.unread(call.requireRole(Role.MEMBER).id)))
            }
            post("/read") {
                store.markAllRead(call.requireRole(Role.MEMBER).id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/read") {
                val id = call.parameters["id"]?.toLongOrNull() ?: throw NotFoundException()
                if (!store.markRead(call.requireRole(Role.MEMBER).id, id)) throw NotFoundException()
                call.respond(HttpStatusCode.NoContent)
            }
        }
        route("/api/threads/{id}/watch") {
            get {
                call.respond(WatchState(store.isWatching(call.requireRole(Role.MEMBER).id, call.threadId())))
            }
            rateLimit(RateLimits.WRITE) {
                put {
                    val user = call.requireRole(Role.MEMBER)
                    try {
                        store.watch(user.id, call.threadId())
                    } catch (failure: ExposedSQLException) {
                        // There is no such thread.
                        if (failure.sqlState == FOREIGN_KEY_VIOLATION) throw NotFoundException() else throw failure
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
                delete {
                    store.unwatch(call.requireRole(Role.MEMBER).id, call.threadId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun ApplicationCall.threadId(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun ApplicationCall.cursor(): Long? =
    request.queryParameters["cursor"]?.let {
        it.toLongOrNull()
            ?: throw BadRequestException("Not a valid cursor.")
    }

private fun ApplicationCall.limit(): Int {
    val value = request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw BadRequestException("Not a valid limit.") }
    return (value ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
}
