package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.accounts
import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.database.TestDatabase
import dev.nilsschlegel.forum.mail.LogMailSender
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PASSWORD = "correct horse battery"

/** Reactions through the real routes, with real sessions, so who is reading and who may react is checked for real. */
class ReactionsHttpTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun ApplicationTestBuilder.app() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            configureRateLimits(authPerMinute = 1_000, writePerMinute = 1_000)
            forum(accounts(LogMailSender(), secureCookie = false, initialAdmin = null))
        }
    }

    private suspend fun ApplicationTestBuilder.user(
        name: String,
        confirmed: Boolean = true,
    ): HttpClient {
        val client = createClient { install(HttpCookies) }
        client.send("POST", "/api/auth/register", """{"username":"$name","password":"$PASSWORD","email":"$name@example.com"}""")
        if (confirmed) TestDatabase.execute("UPDATE users SET email_verified_at = now() WHERE username = '$name'")
        return client
    }

    private suspend fun HttpClient.send(
        method: String,
        path: String,
        body: String? = null,
    ): HttpResponse {
        fun io.ktor.client.request.HttpRequestBuilder.json() {
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (method) {
            "GET" -> get(path)
            "PUT" -> put(path) { json() }
            "DELETE" -> delete(path) { json() }
            else -> post(path) { json() }
        }
    }

    private suspend fun HttpClient.startThread(): Long =
        Regex(""""id":(\d+),"category"""")
            .find(send("POST", "/api/categories/general/threads", """{"title":"T","body":"First"}""").bodyAsText())!!
            .groupValues[1]
            .toLong()

    @Test
    fun `members react, change their mind and take it back`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val thread = alice.startThread()

            val first = bob.send("PUT", "/api/posts/1/reaction", """{"kind":"like"}""")
            assertEquals(HttpStatusCode.OK, first.status)
            assertTrue(""""like":1""" in first.bodyAsText() && """"myReaction":"like"""" in first.bodyAsText(), first.bodyAsText())

            val changed = bob.send("PUT", "/api/posts/1/reaction", """{"kind":"celebrate"}""").bodyAsText()
            assertTrue(""""celebrate":1""" in changed && """"like"""" !in changed, changed)

            val seenByBob = bob.send("GET", "/api/threads/$thread").bodyAsText()
            assertTrue(""""myReaction":"celebrate"""" in seenByBob, seenByBob)
            val seenByAlice = alice.send("GET", "/api/threads/$thread").bodyAsText()
            assertTrue(""""celebrate":1""" in seenByAlice && """"myReaction":null""" in seenByAlice, seenByAlice)

            val removed = bob.send("DELETE", "/api/posts/1/reaction").bodyAsText()
            assertTrue(""""reactions":{}""" in removed && """"myReaction":null""" in removed, removed)
        }

    @Test
    fun `visitors see the counts but cannot react`() =
        testApplication {
            app()
            val alice = user("alice")
            val thread = alice.startThread()
            alice.send("PUT", "/api/posts/1/reaction", """{"kind":"love"}""")

            val visitor = createClient {}
            val read = visitor.send("GET", "/api/threads/$thread")
            assertEquals(HttpStatusCode.OK, read.status)
            assertTrue(""""love":1""" in read.bodyAsText() && """"myReaction":null""" in read.bodyAsText(), read.bodyAsText())
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("PUT", "/api/posts/1/reaction", """{"kind":"like"}""").status)
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("DELETE", "/api/posts/1/reaction").status)
        }

    @Test
    fun `an unconfirmed address cannot react`() =
        testApplication {
            app()
            val alice = user("alice")
            val newcomer = user("newcomer", confirmed = false)
            alice.startThread()
            assertEquals(HttpStatusCode.Forbidden, newcomer.send("PUT", "/api/posts/1/reaction", """{"kind":"like"}""").status)
        }

    @Test
    fun `only the offered reactions are accepted, and only on posts that exist`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.startThread()
            assertEquals(HttpStatusCode.BadRequest, alice.send("PUT", "/api/posts/1/reaction", """{"kind":"angry"}""").status)
            assertEquals(HttpStatusCode.NotFound, alice.send("PUT", "/api/posts/999/reaction", """{"kind":"like"}""").status)
            assertEquals(HttpStatusCode.NotFound, alice.send("DELETE", "/api/posts/999/reaction").status)
        }
}
