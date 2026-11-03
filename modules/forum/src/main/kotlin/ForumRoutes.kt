package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.UserSession
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

/** Serves the forum under `/api`: categories, threads and posts. Needs [dev.nilsschlegel.forum.accounts.accounts] installed. */
fun Application.forum() {
    routing {
        forumRoutes(ExposedForumRepository())
    }
}

internal fun Route.forumRoutes(forum: ForumRepository) {
    route("/api") {
        get("/categories") {
            call.respond(forum.categories())
        }
        get("/categories/{slug}/threads") {
            val threads = forum.threads(call.slug()) ?: throw NotFoundException()
            call.respond(threads)
        }
        get("/threads/{id}") {
            call.respond(forum.thread(call.threadId()) ?: throw NotFoundException())
        }
        authenticate(SESSION_AUTH) {
            post("/categories/{slug}/threads") {
                val new = call.receive<NewThread>()
                val thread =
                    forum.createThread(call.slug(), call.username(), new.title.trim(), new.body.trim()) ?: throw NotFoundException()
                call.respond(HttpStatusCode.Created, thread)
            }
            post("/threads/{id}/posts") {
                val new = call.receive<NewPost>()
                val post = forum.reply(call.threadId(), call.username(), new.body.trim()) ?: throw NotFoundException()
                call.respond(HttpStatusCode.Created, post)
            }
        }
    }
}

private fun ApplicationCall.slug(): String = parameters["slug"] ?: throw NotFoundException()

private fun ApplicationCall.threadId(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun ApplicationCall.username(): String = principal<UserSession>()!!.username
