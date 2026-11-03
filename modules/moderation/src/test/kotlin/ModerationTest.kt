package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.accounts
import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.database.TestDatabase
import dev.nilsschlegel.forum.forum.forum
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.patch
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

/** The whole stack: real sessions, real accounts and forum, real Postgres. Only the Svelte plugin is left out. */
class ModerationTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun ApplicationTestBuilder.app() {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            configureRateLimits(authPerMinute = 1_000, writePerMinute = 1_000)
            val accounts = accounts(secureCookie = false, initialAdmin = null)
            val forum = forum(accounts)
            moderation(accounts, forum)
        }
    }

    /** A signed-in client for a new user with the given role. */
    private suspend fun ApplicationTestBuilder.user(
        name: String,
        role: String = "member",
    ): HttpClient {
        val client = createClient { install(HttpCookies) }
        val response = client.send("POST", "/api/auth/register", """{"username":"$name","password":"$PASSWORD"}""")
        assertEquals(HttpStatusCode.Created, response.status)
        if (role != "member") TestDatabase.execute("UPDATE users SET role = '$role' WHERE username = '$name'")
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
            "POST" -> post(path) { json() }
            "PUT" -> put(path) { json() }
            else -> patch(path) { json() }
        }
    }

    private val reason = """{"reason":"Not welcome here"}"""

    /** Starts a thread as [author] and returns its id, and the id of the reply [replier] makes. */
    private suspend fun thread(
        author: HttpClient,
        replier: HttpClient,
    ): Pair<Long, Long> {
        val created = author.send("POST", "/api/categories/general/threads", """{"title":"Topic","body":"First"}""").bodyAsText()
        val threadId = Regex(""""id":(\d+),"category"""").find(created)!!.groupValues[1].toLong()
        val reply = replier.send("POST", "/api/threads/$threadId/posts", """{"body":"Rude words"}""").bodyAsText()
        return threadId to Regex(""""id":(\d+)""").find(reply)!!.groupValues[1].toLong()
    }

    @Test
    fun `members and visitors cannot use staff routes`() =
        testApplication {
            app()
            val member = user("member")
            assertEquals(HttpStatusCode.Forbidden, member.send("GET", "/api/staff/reports").status)
            assertEquals(HttpStatusCode.Forbidden, member.send("GET", "/api/staff/audit").status)
            assertEquals(HttpStatusCode.Forbidden, member.send("POST", "/api/staff/users/member/ban", reason).status)
            assertEquals(HttpStatusCode.Unauthorized, createClient {}.send("GET", "/api/staff/reports").status)
            val moderator = user("mod", "moderator")
            assertEquals(HttpStatusCode.Forbidden, moderator.send("GET", "/api/staff/categories").status)
        }

    @Test
    fun `a reported post reaches the queue and resolving it clears the queue and is logged`() =
        testApplication {
            app()
            val author = user("alice")
            val replier = user("bob")
            val reporter = user("carol")
            val moderator = user("mod", "moderator")
            val (_, postId) = thread(author, replier)

            assertEquals(HttpStatusCode.Created, reporter.send("POST", "/api/posts/$postId/reports", """{"reason":"Insulting"}""").status)
            assertEquals(HttpStatusCode.Conflict, reporter.send("POST", "/api/posts/$postId/reports", """{"reason":"Again"}""").status)
            assertEquals(HttpStatusCode.NotFound, reporter.send("POST", "/api/posts/999/reports", """{"reason":"Nothing"}""").status)

            val queue = moderator.send("GET", "/api/staff/reports").bodyAsText()
            assertTrue("Rude words" in queue && "carol" in queue && "Insulting" in queue && """"author":"bob"""" in queue, queue)

            val reportId = Regex(""""id":(\d+),"post"""").find(queue)!!.groupValues[1]
            assertEquals(
                HttpStatusCode.NoContent,
                moderator.send("POST", "/api/staff/reports/$reportId/resolve", """{"outcome":"actioned","note":"Hid it"}""").status,
            )
            assertEquals(
                HttpStatusCode.NotFound,
                moderator.send("POST", "/api/staff/reports/$reportId/resolve", """{"outcome":"dismissed"}""").status,
            )
            assertTrue(""""items":[]""" in moderator.send("GET", "/api/staff/reports").bodyAsText())

            val audit = moderator.send("GET", "/api/staff/audit").bodyAsText()
            assertTrue(""""action":"report.resolve"""" in audit && """"actor":"mod"""" in audit && "actioned: Hid it" in audit, audit)
        }

    @Test
    fun `hiding a post masks it for readers and restoring brings it back`() =
        testApplication {
            app()
            val author = user("alice")
            val replier = user("bob")
            val moderator = user("mod", "moderator")
            val (threadId, postId) = thread(author, replier)

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/posts/$postId/hide", reason).status)
            val hidden = author.send("GET", "/api/threads/$threadId").bodyAsText()
            assertTrue("Rude words" !in hidden && """"hidden":true""" in hidden && """"replyCount":0""" in hidden, hidden)

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/posts/$postId/restore").status)
            val shown = author.send("GET", "/api/threads/$threadId").bodyAsText()
            assertTrue("Rude words" in shown && """"replyCount":1""" in shown, shown)

            assertEquals(HttpStatusCode.Conflict, moderator.send("POST", "/api/staff/posts/1/hide", reason).status)
            assertEquals(HttpStatusCode.NotFound, moderator.send("POST", "/api/staff/posts/999/hide", reason).status)
            assertEquals(HttpStatusCode.Forbidden, replier.send("POST", "/api/staff/posts/$postId/hide", reason).status)

            val audit = moderator.send("GET", "/api/staff/audit").bodyAsText()
            assertTrue("post.hide" in audit && "Not welcome here" in audit && "post.restore" in audit, audit)
        }

    @Test
    fun `moderators can pin, lock, move and hide threads`() =
        testApplication {
            app()
            val author = user("alice")
            val replier = user("bob")
            val moderator = user("mod", "moderator")
            val (threadId, _) = thread(author, replier)

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/threads/$threadId/pin").status)
            assertTrue(""""pinned":[{"id":$threadId""" in author.send("GET", "/api/categories/general/threads").bodyAsText())

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/threads/$threadId/lock").status)
            assertEquals(HttpStatusCode.Locked, replier.send("POST", "/api/threads/$threadId/posts", """{"body":"More"}""").status)
            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/threads/$threadId/unlock").status)
            assertEquals(HttpStatusCode.Created, replier.send("POST", "/api/threads/$threadId/posts", """{"body":"More"}""").status)

            assertEquals(
                HttpStatusCode.NoContent,
                moderator.send("POST", "/api/staff/threads/$threadId/move", """{"category":"help"}""").status,
            )
            assertEquals(
                HttpStatusCode.UnprocessableEntity,
                moderator.send("POST", "/api/staff/threads/$threadId/move", """{"category":"nope"}""").status,
            )
            assertTrue("Topic" in author.send("GET", "/api/categories/help/threads").bodyAsText())

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/threads/$threadId/hide", reason).status)
            assertEquals(HttpStatusCode.NotFound, author.send("GET", "/api/threads/$threadId").status)
            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/threads/$threadId/restore").status)
            assertEquals(HttpStatusCode.OK, author.send("GET", "/api/threads/$threadId").status)
            assertEquals(HttpStatusCode.NotFound, moderator.send("POST", "/api/staff/threads/999/pin").status)
        }

    @Test
    fun `a banned user is signed out at once and cannot sign in or write`() =
        testApplication {
            app()
            val bob = user("bob")
            val moderator = user("mod", "moderator")
            assertEquals(HttpStatusCode.OK, bob.send("GET", "/api/auth/me").status)

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/users/bob/ban", reason).status)
            assertEquals(HttpStatusCode.Unauthorized, bob.send("GET", "/api/auth/me").status)
            assertEquals(
                HttpStatusCode.Unauthorized,
                bob.send("POST", "/api/categories/general/threads", """{"title":"x","body":"y"}""").status,
            )
            val login = createClient {}.send("POST", "/api/auth/login", """{"username":"bob","password":"$PASSWORD"}""")
            assertEquals(HttpStatusCode.Forbidden, login.status)

            assertEquals(HttpStatusCode.NoContent, moderator.send("POST", "/api/staff/users/bob/unban").status)
            assertEquals(HttpStatusCode.OK, bob.send("GET", "/api/auth/me").status)
            assertTrue("user.ban" in moderator.send("GET", "/api/staff/audit").bodyAsText())
        }

    @Test
    fun `who may ban whom follows the roles`() =
        testApplication {
            app()
            user("member")
            val moderator = user("mod", "moderator")
            user("other-mod", "moderator")
            val admin = user("boss", "admin")
            user("other-admin", "admin")

            assertEquals(HttpStatusCode.Forbidden, moderator.send("POST", "/api/staff/users/other-mod/ban", reason).status)
            assertEquals(HttpStatusCode.Forbidden, moderator.send("POST", "/api/staff/users/boss/ban", reason).status)
            assertEquals(HttpStatusCode.NoContent, admin.send("POST", "/api/staff/users/other-mod/ban", reason).status)
            assertEquals(HttpStatusCode.Forbidden, admin.send("POST", "/api/staff/users/other-admin/ban", reason).status)
            assertEquals(HttpStatusCode.Forbidden, admin.send("POST", "/api/staff/users/boss/ban", reason).status)
            assertEquals(HttpStatusCode.NotFound, admin.send("POST", "/api/staff/users/nobody/ban", reason).status)
        }

    @Test
    fun `only admins change roles, and not their own`() =
        testApplication {
            app()
            user("member")
            val moderator = user("mod", "moderator")
            val admin = user("boss", "admin")

            assertEquals(HttpStatusCode.Forbidden, moderator.send("PUT", "/api/staff/users/member/role", """{"role":"admin"}""").status)
            assertEquals(HttpStatusCode.NoContent, admin.send("PUT", "/api/staff/users/member/role", """{"role":"moderator"}""").status)
            assertTrue(""""role":"moderator"""" in admin.send("GET", "/api/staff/users/member").bodyAsText())
            assertEquals(HttpStatusCode.Forbidden, admin.send("PUT", "/api/staff/users/boss/role", """{"role":"member"}""").status)
            assertTrue("user.role" in admin.send("GET", "/api/staff/audit").bodyAsText())
        }

    @Test
    fun `admins manage categories and moderators cannot`() =
        testApplication {
            app()
            val moderator = user("mod", "moderator")
            val admin = user("boss", "admin")
            val member = user("member")

            assertEquals(
                HttpStatusCode.Created,
                admin.send("POST", "/api/staff/categories", """{"slug":"news","title":"News","description":"Announcements"}""").status,
            )
            assertEquals(HttpStatusCode.Conflict, admin.send("POST", "/api/staff/categories", """{"slug":"news","title":"Again"}""").status)
            assertEquals(HttpStatusCode.Forbidden, moderator.send("POST", "/api/staff/categories", """{"slug":"x","title":"X"}""").status)

            assertTrue("Updates" in admin.send("PATCH", "/api/staff/categories/news", """{"title":"Updates"}""").bodyAsText())
            assertEquals(HttpStatusCode.NotFound, admin.send("PATCH", "/api/staff/categories/nope", """{"title":"x"}""").status)
            assertEquals(
                HttpStatusCode.NoContent,
                admin.send("PUT", "/api/staff/categories/order", """{"slugs":["news","help","general"]}""").status,
            )
            assertEquals(
                HttpStatusCode.UnprocessableEntity,
                admin.send("PUT", "/api/staff/categories/order", """{"slugs":["news"]}""").status,
            )
            val order = member.send("GET", "/api/categories").bodyAsText()
            assertTrue(order.indexOf("Updates") in 0 until order.indexOf("Help"), "news should now come before help: $order")

            admin.send("PATCH", "/api/staff/categories/news", """{"archived":true}""")
            assertTrue("Updates" !in member.send("GET", "/api/categories").bodyAsText())
            assertTrue("Updates" in admin.send("GET", "/api/staff/categories").bodyAsText())
        }

    @Test
    fun `the audit log is paged newest first`() =
        testApplication {
            app()
            val author = user("alice")
            val moderator = user("mod", "moderator")
            val (threadId, _) = thread(author, author)
            moderator.send("POST", "/api/staff/threads/$threadId/pin")
            moderator.send("POST", "/api/staff/threads/$threadId/lock")
            moderator.send("POST", "/api/staff/threads/$threadId/unlock")

            val first = moderator.send("GET", "/api/staff/audit?limit=2").bodyAsText()
            assertTrue("thread.unlock" in first && "thread.lock" in first && "thread.pin" !in first, first)
            val cursor = Regex(""""next":"(\d+)"""").find(first)!!.groupValues[1]
            val second = moderator.send("GET", "/api/staff/audit?limit=2&cursor=$cursor").bodyAsText()
            assertTrue("thread.pin" in second && """"next":null""" in second, second)
            assertEquals(HttpStatusCode.BadRequest, moderator.send("GET", "/api/staff/audit?cursor=zzz").status)
        }

    @Test
    fun `profiles show recent activity`() =
        testApplication {
            app()
            val author = user("alice")
            val replier = user("bob")
            thread(author, replier)
            val activity = author.send("GET", "/api/users/bob/activity").bodyAsText()
            assertTrue(""""postCount":1""" in activity && "Rude words" in activity && "Topic" in activity, activity)
            assertTrue("bob" in author.send("GET", "/api/users/bob").bodyAsText())
            assertEquals(HttpStatusCode.NotFound, author.send("GET", "/api/users/nobody").status)
            assertEquals(HttpStatusCode.NotFound, author.send("GET", "/api/users/nobody/activity").status)
        }
}
