package dev.nilsschlegel.forum

import io.github.nijasc.ktor.svelte.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class Problem(
    val message: String,
)

class ApiException(
    val status: HttpStatusCode,
    override val message: String,
) : RuntimeException(message)

fun Application.configureErrors() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, Problem(cause.message))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, Problem("The request is not valid."))
        }
        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, Problem("That does not exist."))
        }
        exception<Exception> { call, cause ->
            if (cause is ValidationException) throw cause
            call.application.log.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, Problem("Something went wrong."))
        }
    }
}
