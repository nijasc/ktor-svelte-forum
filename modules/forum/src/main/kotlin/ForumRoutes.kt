package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.AuthUser
import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.UserDirectory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Serves the forum under `/api`: categories, threads and posts. Needs [dev.nilsschlegel.forum.accounts.accounts] installed first. */
fun Application.forum(users: UserDirectory) {
    routing {
        forumRoutes(ExposedForumRepository(users))
    }
}

internal fun Route.forumRoutes(forum: ForumRepository) {
    route("/api") {
        get("/categories") {
            call.respond(forum.categories())
        }
        get("/categories/{slug}/threads") {
            call.respond(forum.threads(call.slug(), call.pageRequest()) ?: throw NotFoundException())
        }
        get("/threads/{id}") {
            call.respond(forum.thread(call.threadId(), call.pageRequest()) ?: throw NotFoundException())
        }
        authenticate(SESSION_AUTH) {
            post("/categories/{slug}/threads") {
                val new = call.receive<NewThread>()
                val thread =
                    forum.createThread(call.slug(), call.userId(), new.title.trim(), new.body.trim()) ?: throw NotFoundException()
                call.respond(HttpStatusCode.Created, thread)
            }
            post("/threads/{id}/posts") {
                val new = call.receive<NewPost>()
                val post = forum.reply(call.threadId(), call.userId(), new.body.trim()) ?: throw NotFoundException()
                call.respond(HttpStatusCode.Created, post)
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

private fun ApplicationCall.threadId(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun ApplicationCall.userId(): Long = principal<AuthUser>()!!.id
