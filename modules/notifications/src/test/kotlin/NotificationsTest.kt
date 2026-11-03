package dev.nilsschlegel.forum.notifications

import dev.nilsschlegel.forum.accounts.accounts
import dev.nilsschlegel.forum.core.EventBus
import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.database.TestDatabase
import dev.nilsschlegel.forum.forum.forum
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

/** The whole stack: sessions, accounts, forum, the event bus, notifications and Postgres. */
class NotificationsTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun ApplicationTestBuilder.app() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            configureRateLimits(authPerMinute = 1_000, writePerMinute = 1_000)
            val events = EventBus()
            val accounts = accounts(LogMailSender(), secureCookie = false, initialAdmin = null)
            forum(accounts, events)
            notifications(accounts, events)
        }
    }

    private suspend fun ApplicationTestBuilder.user(name: String): HttpClient {
        val client = createClient { install(HttpCookies) }
        client.send("POST", "/api/auth/register", """{"username":"$name","password":"$PASSWORD","email":"$name@example.com"}""")
        TestDatabase.execute("UPDATE users SET email_verified_at = now() WHERE username = '$name'")
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

    private suspend fun HttpClient.startThread(
        title: String = "Topic",
        body: String = "First",
    ): Long =
        Regex(""""id":(\d+),"category"""")
            .find(send("POST", "/api/categories/general/threads", """{"title":"$title","body":"$body"}""").bodyAsText())!!
            .groupValues[1]
            .toLong()

    private suspend fun HttpClient.reply(
        threadId: Long,
        body: String,
    ) = send("POST", "/api/threads/$threadId/posts", """{"body":"$body"}""")

    private suspend fun HttpClient.unread() =
        Regex(""""count":(\d+)""").find(send("GET", "/api/notifications/unread").bodyAsText())!!.groupValues[1].toInt()

    @Test
    fun `a reply tells the thread author but not the person who wrote it`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val thread = alice.startThread("Rules")
            assertEquals(HttpStatusCode.Created, bob.reply(thread, "Thanks").status)

            assertEquals(1, alice.unread())
            assertEquals(0, bob.unread())
            val list = alice.send("GET", "/api/notifications").bodyAsText()
            assertTrue(
                """"kind":"reply"""" in list &&
                    """"actor":"bob"""" in list &&
                    """"threadTitle":"Rules"""" in list &&
                    """"read":false""" in list,
                list,
            )
            assertTrue(""""unread":1""" in list)
        }

    @Test
    fun `several replies fold into one notification until it is read`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val thread = alice.startThread()
            bob.reply(thread, "One")
            bob.reply(thread, "Two")
            bob.reply(thread, "Three")

            assertEquals(1, alice.unread())
            assertTrue(""""count":3""" in alice.send("GET", "/api/notifications").bodyAsText())

            assertEquals(HttpStatusCode.NoContent, alice.send("POST", "/api/notifications/read").status)
            assertEquals(0, alice.unread())
            bob.reply(thread, "Four")
            assertEquals(1, alice.unread())
            val list = alice.send("GET", "/api/notifications").bodyAsText()
            assertEquals(2, Regex(""""id":\d+,"kind"""").findAll(list).count(), list)
        }

    @Test
    fun `replying subscribes you to the thread`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val carol = user("carol")
            val thread = alice.startThread()
            bob.reply(thread, "Me too")
            carol.reply(thread, "And me")

            assertEquals(1, bob.unread())
            assertEquals(1, alice.unread(), "bob's and carol's replies fold into one notification")
            assertTrue(""""count":2""" in alice.send("GET", "/api/notifications").bodyAsText())
            assertTrue(""""watching":true""" in bob.send("GET", "/api/threads/$thread/watch").bodyAsText())
            assertEquals(0, carol.unread())
        }

    @Test
    fun `unwatching stops the notifications and watching starts them`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val carol = user("carol")
            val thread = alice.startThread()
            bob.reply(thread, "Hi")

            assertEquals(HttpStatusCode.NoContent, alice.send("DELETE", "/api/threads/$thread/watch").status)
            assertTrue(""""watching":false""" in alice.send("GET", "/api/threads/$thread/watch").bodyAsText())
            alice.send("POST", "/api/notifications/read")
            carol.reply(thread, "Anyone there?")
            assertEquals(0, alice.unread())

            assertEquals(HttpStatusCode.NoContent, alice.send("PUT", "/api/threads/$thread/watch").status)
            carol.reply(thread, "Again")
            assertEquals(1, alice.unread())
            assertEquals(HttpStatusCode.NotFound, alice.send("PUT", "/api/threads/999/watch").status)
        }

    @Test
    fun `a mention tells that person, once, and not instead of nobody`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val dave = user("dave")
            val thread = alice.startThread()

            bob.reply(thread, "Hey @dave and @Dave, also @alice, @nobody and @bob")

            val daves = dave.send("GET", "/api/notifications").bodyAsText()
            assertEquals(1, Regex(""""kind":"mention"""").findAll(daves).count(), daves)
            assertEquals(1, dave.unread())

            // alice is watching and mentioned, so she gets the mention and not a reply on top of it
            val alices = alice.send("GET", "/api/notifications").bodyAsText()
            assertTrue(""""kind":"mention"""" in alices && """"kind":"reply"""" !in alices, alices)
            assertEquals(1, alice.unread())
            assertEquals(0, bob.unread())
        }

    @Test
    fun `a mention in a new thread tells that person`() =
        testApplication {
            app()
            val alice = user("alice")
            val dave = user("dave")
            alice.startThread("Question", "What do you think, @dave and @alice?")
            assertEquals(1, dave.unread())
            assertEquals(0, alice.unread())
            assertTrue(""""threadTitle":"Question"""" in dave.send("GET", "/api/notifications").bodyAsText())
        }

    @Test
    fun `people only see and change their own notifications`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val thread = alice.startThread()
            bob.reply(thread, "Hi")
            val id = Regex(""""id":(\d+),"kind"""").find(alice.send("GET", "/api/notifications").bodyAsText())!!.groupValues[1]

            assertEquals(HttpStatusCode.NotFound, bob.send("POST", "/api/notifications/$id/read").status)
            assertEquals(1, alice.unread())
            assertEquals(HttpStatusCode.NoContent, alice.send("POST", "/api/notifications/$id/read").status)
            assertEquals(0, alice.unread())
            assertTrue(""""items":[]""" in bob.send("GET", "/api/notifications").bodyAsText())
        }

    @Test
    fun `notifications are paged newest first`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val first = alice.startThread("First")
            val second = alice.startThread("Second")
            val third = alice.startThread("Third")
            bob.reply(first, "a")
            bob.reply(second, "b")
            bob.reply(third, "c")

            val page = alice.send("GET", "/api/notifications?limit=2").bodyAsText()
            assertTrue("Third" in page && "Second" in page && "First" !in page, page)
            val cursor = Regex(""""next":"(\d+)"""").find(page)!!.groupValues[1]
            val rest = alice.send("GET", "/api/notifications?limit=2&cursor=$cursor").bodyAsText()
            assertTrue("First" in rest && """"next":null""" in rest, rest)
            assertEquals(HttpStatusCode.BadRequest, alice.send("GET", "/api/notifications?cursor=zzz").status)
        }

    @Test
    fun `visitors cannot read notifications`() =
        testApplication {
            app()
            val visitor = createClient {}
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("GET", "/api/notifications").status)
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("GET", "/api/notifications/unread").status)
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("PUT", "/api/threads/1/watch").status)
        }
}
