package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.AuthUser
import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.accounts.requireVerified
import dev.nilsschlegel.forum.core.ApiException
import dev.nilsschlegel.forum.core.EventBus
import dev.nilsschlegel.forum.core.RateLimits
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Serves the forum under `/api`: categories, threads and posts. Needs [dev.nilsschlegel.forum.accounts.accounts] installed first. */
fun Application.forum(
    users: UserDirectory,
    events: EventBus = EventBus(),
): ForumStaff {
    routing {
        forumRoutes(ExposedForumRepository(users, events), users)
    }
    return ExposedForumStaff(users)
}

internal fun Route.forumRoutes(
    forum: ForumRepository,
    users: UserDirectory,
) {
    route("/api") {
        get("/categories") {
            call.respond(forum.categories())
        }
        get("/categories/{slug}/threads") {
            call.respond(forum.threads(call.slug(), call.pageRequest()) ?: throw NotFoundException())
        }
        get("/users/{username}/activity") {
            val userId = users.idOf(call.parameters["username"] ?: throw NotFoundException()) ?: throw NotFoundException()
            call.respond(forum.activity(userId))
        }
        get("/threads/{id}") {
            call.respond(forum.thread(call.threadId(), call.pageRequest()) ?: throw NotFoundException())
        }
        authenticate(SESSION_AUTH) {
            rateLimit(RateLimits.WRITE) {
                post("/categories/{slug}/threads") {
                    val new = call.receive<NewThread>()
                    val thread =
                        forum.createThread(call.slug(), call.requireVerified().id, new.title.trim(), new.body.trim())
                            ?: throw NotFoundException()
                    call.respond(HttpStatusCode.Created, thread)
                }
                post("/threads/{id}/posts") {
                    val new = call.receive<NewPost>()
                    when (val result = forum.reply(call.threadId(), call.requireVerified().id, new.body.trim())) {
                        is ReplyResult.Done -> call.respond(HttpStatusCode.Created, result.post)
                        ReplyResult.NotFound -> throw NotFoundException()
                        ReplyResult.Locked -> throw ApiException(HttpStatusCode.Locked, "This thread is locked.")
                    }
                }
                patch("/posts/{id}") {
                    val new = call.receive<NewPost>()
                    call.respond(forum.editPost(call.postId(), call.userId(), new.body.trim()).post())
                }
                delete("/posts/{id}") {
                    forum.deletePost(call.postId(), call.userId()).post()
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private fun ApplicationCall.pageRequest(): PageRequest {
    val limit = request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw BadRequestException("Not a valid limit.") }
    return PageRequest(
        cursor = request.queryParameters["cursor"],
        limit = (limit ?: PageRequest.DEFAULT_LIMIT).coerceIn(1, PageRequest.MAX_LIMIT),
    )
}

private fun ApplicationCall.slug(): String = parameters["slug"] ?: throw NotFoundException()

private fun ApplicationCall.postId(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun PostChange.post(): Post =
    when (this) {
        is PostChange.Done -> post
        PostChange.NotFound -> throw NotFoundException()
        PostChange.NotYours -> throw ApiException(HttpStatusCode.Forbidden, "You can only change your own posts.")
        PostChange.IsFirstPost -> throw ApiException(HttpStatusCode.Forbidden, "The first post of a thread cannot be deleted.")
    }

private fun ApplicationCall.threadId(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun ApplicationCall.userId(): Long = principal<AuthUser>()!!.id
