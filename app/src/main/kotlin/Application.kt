package dev.nilsschlegel.forum

import dev.nilsschlegel.forum.accounts.accounts
import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.core.env
import dev.nilsschlegel.forum.database.configureDatabase
import dev.nilsschlegel.forum.forum.forum
import dev.nilsschlegel.forum.moderation.moderation
import io.github.nijasc.ktor.svelte.Svelte
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class Greeting(
    val message: String,
)

fun main() {
    val port = env("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, module = Application::module).start(wait = true)
}

/** The only place that knows every module. Order matters: errors, rate limits and the database come before the features that use them. */
fun Application.module() {
    install(Svelte) {
        generateTs = true
        transport {
            json()
            compression()
        }
    }
    configureErrors()
    configureRateLimits()
    configureDatabase()
    val accounts = accounts()
    val forum = forum(accounts)
    moderation(accounts, forum)

    routing {
        get("/api/hello") {
            call.respond(Greeting("Hello from Ktor"))
        }
    }
}
