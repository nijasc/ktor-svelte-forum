package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.UserSession
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeForumRepository : ForumRepository {
    private val threads = mutableListOf<ThreadDetail>()

    override suspend fun categories() = listOf(Category("general", "General", "Talk about anything."))

    override suspend fun threads(categorySlug: String) =
        if (categorySlug == "general") threads.map { ThreadSummary(it.id, it.title, it.author, it.createdAt) } else null

    override suspend fun thread(id: Long) = threads.find { it.id == id }

    override suspend fun createThread(
        categorySlug: String,
        author: String,
        title: String,
        body: String,
    ): ThreadDetail? {
        if (categorySlug != "general") return null
        val id = threads.size + 1L
        return ThreadDetail(id, categorySlug, title, author, NOW, listOf(Post(1, author, body, NOW))).also { threads += it }
    }

    override suspend fun reply(
        threadId: Long,
        author: String,
        body: String,
    ): Post? {
        val thread = thread(threadId) ?: return null
        val post = Post(thread.posts.size + 1L, author, body, NOW)
        threads[threads.indexOf(thread)] = thread.copy(posts = thread.posts + post)
        return post
    }

    private companion object {
        const val NOW = "2026-01-01T00:00:00Z"
    }
}

/** Signs everyone in as `alice`, unless the request has an `X-Anonymous` header. */
private class TestAuth(
    config: Config,
) : AuthenticationProvider(config) {
    class Config : AuthenticationProvider.Config(SESSION_AUTH)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        if (context.call.request.headers["X-Anonymous"] == null) {
            context.principal(UserSession("alice"))
        } else {
            context.challenge("test", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
        }
    }
}

private fun ApplicationTestBuilder.forumApp() {
    application { forumModule() }
}

private fun Application.forumModule() {
    install(ContentNegotiation) { json() }
    install(Authentication) { register(TestAuth(TestAuth.Config())) }
    routing { forumRoutes(FakeForumRepository()) }
}

class ForumRoutesTest {
    @Test
    fun `lists categories without signing in`() =
        testApplication {
            forumApp()
            val response = client.get("/api/categories")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("General" in response.bodyAsText())
        }

    @Test
    fun `unknown category is not found`() =
        testApplication {
            forumApp()
            assertEquals(HttpStatusCode.NotFound, client.get("/api/categories/nope/threads").status)
        }

    @Test
    fun `a signed in user can start a thread and reply`() =
        testApplication {
            forumApp()
            val created =
                client.post("/api/categories/general/threads") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"  Hello  ","body":"First post"}""")
                }
            assertEquals(HttpStatusCode.Created, created.status)
            assertTrue(""""title":"Hello"""" in created.bodyAsText())

            val reply =
                client.post("/api/threads/1/posts") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"body":"Welcome"}""")
                }
            assertEquals(HttpStatusCode.Created, reply.status)
            assertTrue("Welcome" in client.get("/api/threads/1").bodyAsText())
        }

    @Test
    fun `writing needs a session`() =
        testApplication {
            forumApp()
            val response =
                client.post("/api/categories/general/threads") {
                    headers.append("X-Anonymous", "1")
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Hi","body":"Hi"}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
}
