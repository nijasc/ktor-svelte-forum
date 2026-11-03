package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.database.TestDatabase
import dev.nilsschlegel.forum.mail.MailException
import dev.nilsschlegel.forum.mail.MailMessage
import dev.nilsschlegel.forum.mail.MailSender
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
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
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PASSWORD = "correct horse battery"

private class Outbox : MailSender {
    val sent = mutableListOf<MailMessage>()
    var failing = false

    override suspend fun send(message: MailMessage) {
        if (failing) throw MailException("provider is down")
        sent += message
    }

    /** The token in the last link sent to [to]. */
    fun tokenFor(to: String): String =
        Regex("token=([A-Za-z0-9_-]+)")
            .find(sent.last { it.to == to }.text)!!
            .groupValues[1]
}

/** The account flows over HTTP, with real sessions and Postgres, and a mail sender that keeps what it was given. */
class RecoveryTest {
    private val outbox = Outbox()

    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun ApplicationTestBuilder.app() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            configureRateLimits(authPerMinute = 1_000, writePerMinute = 1_000)
            accounts(outbox, publicUrl = "https://forum.test/", secureCookie = false, initialAdmin = "root")
            routing {
                authenticate(SESSION_AUTH) {
                    post("/write") {
                        call.requireVerified()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }
        }
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
            else -> post(path) { json() }
        }
    }

    private fun ApplicationTestBuilder.client() = createClient { install(HttpCookies) }

    private suspend fun HttpClient.register(
        name: String,
        email: String = "$name@example.com",
    ) = send("POST", "/api/auth/register", """{"username":"$name","password":"$PASSWORD","email":"$email"}""")

    @Test
    fun `signing up sends a link and following it confirms the address once`() =
        testApplication {
            app()
            val alice = client()
            val created = alice.register("alice")
            assertEquals(HttpStatusCode.Created, created.status)
            assertTrue(""""emailVerified":false""" in created.bodyAsText())

            assertEquals(1, outbox.sent.size)
            assertEquals("alice@example.com", outbox.sent.single().to)
            assertTrue("https://forum.test/verify?token=" in outbox.sent.single().text)

            val token = outbox.tokenFor("alice@example.com")
            assertEquals(HttpStatusCode.NoContent, alice.send("POST", "/api/auth/verify", """{"token":"$token"}""").status)
            val me = alice.send("GET", "/api/auth/me").bodyAsText()
            assertTrue(""""emailVerified":true""" in me && """"email":"alice@example.com"""" in me, me)
            assertEquals(HttpStatusCode.BadRequest, alice.send("POST", "/api/auth/verify", """{"token":"$token"}""").status)
        }

    @Test
    fun `writing needs a confirmed address`() =
        testApplication {
            app()
            val alice = client()
            alice.register("alice")
            val denied = alice.send("POST", "/write")
            assertEquals(HttpStatusCode.Forbidden, denied.status)
            assertTrue("Confirm your email address" in denied.bodyAsText())

            alice.send("POST", "/api/auth/verify", """{"token":"${outbox.tokenFor("alice@example.com")}"}""")
            assertEquals(HttpStatusCode.OK, alice.send("POST", "/write").status)
        }

    @Test
    fun `an address cannot be used twice, ignoring case`() =
        testApplication {
            app()
            client().register("alice", "Alice@Example.com")
            val second = client().register("bob", "alice@example.com")
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue("email" in second.bodyAsText())
            assertEquals(HttpStatusCode.Conflict, client().register("ALICE", "other@example.com").status)
        }

    @Test
    fun `the first admin is confirmed and gets no mail`() =
        testApplication {
            app()
            val root = client()
            val created = root.register("root").bodyAsText()
            assertTrue(""""emailVerified":true""" in created && """"role":"admin"""" in created, created)
            assertTrue(outbox.sent.isEmpty())
            assertEquals(HttpStatusCode.OK, root.send("POST", "/write").status)
        }

    @Test
    fun `a mail failure does not stop sign up and the link can be asked for again`() =
        testApplication {
            app()
            outbox.failing = true
            val alice = client()
            assertEquals(HttpStatusCode.Created, alice.register("alice").status)
            assertTrue(outbox.sent.isEmpty())

            outbox.failing = false
            assertEquals(HttpStatusCode.NoContent, alice.send("POST", "/api/auth/verification/resend").status)
            assertEquals(1, outbox.sent.size)
            alice.send("POST", "/api/auth/verify", """{"token":"${outbox.tokenFor("alice@example.com")}"}""")
            assertEquals(HttpStatusCode.OK, alice.send("POST", "/write").status)
        }

    @Test
    fun `asking for a new link makes the old one useless`() =
        testApplication {
            app()
            val alice = client()
            alice.register("alice")
            val old = outbox.tokenFor("alice@example.com")
            alice.send("POST", "/api/auth/verification/resend")
            val fresh = outbox.tokenFor("alice@example.com")
            assertEquals(HttpStatusCode.BadRequest, alice.send("POST", "/api/auth/verify", """{"token":"$old"}""").status)
            assertEquals(HttpStatusCode.NoContent, alice.send("POST", "/api/auth/verify", """{"token":"$fresh"}""").status)
            val before = outbox.sent.size
            alice.send("POST", "/api/auth/verification/resend")
            assertEquals(before, outbox.sent.size, "a confirmed user gets no more mail")
        }

    @Test
    fun `resetting a password signs out old sessions and the link works once`() =
        testApplication {
            app()
            val laptop = client()
            laptop.register("alice")
            outbox.sent.clear()
            assertEquals(HttpStatusCode.OK, laptop.send("GET", "/api/auth/me").status)

            assertEquals(
                HttpStatusCode.NoContent,
                client().send("POST", "/api/auth/forgot-password", """{"email":"ALICE@example.com"}""").status,
            )
            assertEquals(1, outbox.sent.size)
            assertTrue("https://forum.test/reset-password?token=" in outbox.sent.single().text)
            val token = outbox.tokenFor("alice@example.com")

            val newPassword = "a completely new password"
            assertEquals(
                HttpStatusCode.NoContent,
                client().send("POST", "/api/auth/reset-password", """{"token":"$token","password":"$newPassword"}""").status,
            )
            assertEquals(
                HttpStatusCode.BadRequest,
                client().send("POST", "/api/auth/reset-password", """{"token":"$token","password":"another password 123"}""").status,
            )

            assertEquals(HttpStatusCode.Unauthorized, laptop.send("GET", "/api/auth/me").status)
            val phone = client()
            assertEquals(
                HttpStatusCode.Unauthorized,
                phone.send("POST", "/api/auth/login", """{"username":"alice","password":"$PASSWORD"}""").status,
            )
            assertEquals(
                HttpStatusCode.OK,
                phone.send("POST", "/api/auth/login", """{"username":"alice","password":"$newPassword"}""").status,
            )
            val me = phone.send("GET", "/api/auth/me").bodyAsText()
            assertTrue(""""emailVerified":true""" in me, "following the reset link proves the address: $me")
        }

    @Test
    fun `asking to reset gives the same answer for unknown addresses and sends nothing`() =
        testApplication {
            app()
            client().register("alice")
            outbox.sent.clear()
            val known = client().send("POST", "/api/auth/forgot-password", """{"email":"alice@example.com"}""")
            val unknown = client().send("POST", "/api/auth/forgot-password", """{"email":"nobody@example.com"}""")
            assertEquals(known.status, unknown.status)
            assertEquals(known.bodyAsText(), unknown.bodyAsText())
            assertEquals(listOf("alice@example.com"), outbox.sent.map { it.to })
        }

    @Test
    fun `a banned user gets no reset mail and an expired link does not work`() =
        testApplication {
            app()
            client().register("alice")
            outbox.sent.clear()
            client().send("POST", "/api/auth/forgot-password", """{"email":"alice@example.com"}""")
            val token = outbox.tokenFor("alice@example.com")
            TestDatabase.execute("UPDATE email_tokens SET expires_at = now() - interval '1 minute'")
            assertEquals(
                HttpStatusCode.BadRequest,
                client().send("POST", "/api/auth/reset-password", """{"token":"$token","password":"$PASSWORD"}""").status,
            )

            TestDatabase.execute("UPDATE users SET status = 'banned'")
            outbox.sent.clear()
            client().send("POST", "/api/auth/forgot-password", """{"email":"alice@example.com"}""")
            assertTrue(outbox.sent.isEmpty())
        }

    @Test
    fun `changing the address needs the password, an unused address, and a new confirmation`() =
        testApplication {
            app()
            val alice = client()
            alice.register("alice")
            client().register("bob")
            val oldToken = outbox.tokenFor("alice@example.com")
            alice.send("POST", "/api/auth/verify", """{"token":"$oldToken"}""")
            outbox.sent.clear()

            assertEquals(
                HttpStatusCode.Forbidden,
                alice.send("PUT", "/api/auth/email", """{"email":"new@example.com","password":"wrong password here"}""").status,
            )
            assertEquals(
                HttpStatusCode.Conflict,
                alice.send("PUT", "/api/auth/email", """{"email":"BOB@example.com","password":"$PASSWORD"}""").status,
            )
            assertTrue(outbox.sent.isEmpty())

            val changed = alice.send("PUT", "/api/auth/email", """{"email":"new@example.com","password":"$PASSWORD"}""").bodyAsText()
            assertTrue(""""email":"new@example.com"""" in changed && """"emailVerified":false""" in changed, changed)
            assertEquals(HttpStatusCode.Forbidden, alice.send("POST", "/write").status)
            assertEquals(listOf("new@example.com"), outbox.sent.map { it.to })

            alice.send("POST", "/api/auth/verify", """{"token":"${outbox.tokenFor("new@example.com")}"}""")
            assertEquals(HttpStatusCode.OK, alice.send("POST", "/write").status)
        }

    @Test
    fun `a link for the old address stops working after the address changes`() =
        testApplication {
            app()
            val alice = client()
            alice.register("alice")
            val forOldAddress = outbox.tokenFor("alice@example.com")
            alice.send("PUT", "/api/auth/email", """{"email":"new@example.com","password":"$PASSWORD"}""")
            // Issuing a link for the new address already replaced the old one, and even if it had not, the address no longer matches.
            assertEquals(HttpStatusCode.BadRequest, alice.send("POST", "/api/auth/verify", """{"token":"$forOldAddress"}""").status)
        }

    @Test
    fun `visitors cannot use the signed in routes`() =
        testApplication {
            app()
            val visitor = client()
            assertEquals(HttpStatusCode.Unauthorized, visitor.send("POST", "/api/auth/verification/resend").status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                visitor.send("PUT", "/api/auth/email", """{"email":"a@b.co","password":"x"}""").status,
            )
        }
}
