package dev.nilsschlegel.forum.accounts

import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.profileRoutes(users: DatabaseUserStore) {
    get("/api/users/{username}") {
        val username = call.parameters["username"] ?: throw NotFoundException()
        call.respond(users.profile(username) ?: throw NotFoundException())
    }
}
