package dev.nilsschlegel.forum.search

import dev.nilsschlegel.forum.accounts.accounts
import dev.nilsschlegel.forum.core.configureErrors
import dev.nilsschlegel.forum.core.configureRateLimits
import dev.nilsschlegel.forum.database.TestDatabase
import dev.nilsschlegel.forum.forum.forum
import dev.nilsschlegel.forum.mail.LogMailSender
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun quote(text: String) = Json.encodeToString(text)

private const val PASSWORD = "correct horse battery"

/** Search over threads and posts written through the real routes, on real Postgres. */
class SearchTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun ApplicationTestBuilder.app(searchPerMinute: Int = 1_000) {
        application {
            install(ContentNegotiation) { json() }
            configureErrors()
            configureRateLimits(authPerMinute = 1_000, writePerMinute = 1_000, searchPerMinute = searchPerMinute)
            val accounts = accounts(LogMailSender(), secureCookie = false, initialAdmin = null)
            forum(accounts)
            search(accounts)
        }
    }

    private suspend fun ApplicationTestBuilder.user(name: String): HttpClient {
        val client = createClient { install(HttpCookies) }
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"$name","password":"$PASSWORD","email":"$name@example.com"}""")
        }
        TestDatabase.execute("UPDATE users SET email_verified_at = now() WHERE username = '$name'")
        return client
    }

    private suspend fun HttpClient.thread(
        title: String,
        body: String,
        category: String = "general",
    ): Long {
        val response =
            post("/api/categories/$category/threads") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"title":${quote(title)},"body":${quote(body)}}""",
                )
            }.bodyAsText()
        return Regex(""""id":(\d+),"category"""").find(response)!!.groupValues[1].toLong()
    }

    private suspend fun HttpClient.reply(
        threadId: Long,
        body: String,
    ): Long =
        Regex(""""id":(\d+)""")
            .find(
                post("/api/threads/$threadId/posts") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"body":${quote(body)}}""")
                }.bodyAsText(),
            )!!
            .groupValues[1]
            .toLong()

    private suspend fun HttpClient.find(
        text: String,
        extra: String = "",
    ): JsonObject = Json.parseToJsonElement(search(text, extra).bodyAsText()).jsonObject

    private suspend fun HttpClient.search(
        text: String,
        extra: String = "",
    ): HttpResponse = get("/api/search?q=${text.encodeURLParameter()}$extra")

    private fun JsonObject.items(): JsonArray = getValue("items").jsonArray

    private fun JsonObject.titles() =
        items().map {
            it.jsonObject
                .getValue("threadTitle")
                .jsonPrimitive.content
        }

    @Test
    fun `finds threads by title and posts by text, for visitors too`() =
        testApplication {
            app()
            val alice = user("alice")
            val thread = alice.thread("Gardening tips", "Tomatoes need sun")
            alice.reply(thread, "Compost helps a lot")
            val visitor = createClient {}

            val byTitle =
                visitor
                    .find("gardening")
                    .items()
                    .single()
                    .jsonObject
            assertEquals("thread", byTitle.getValue("kind").jsonPrimitive.content)
            assertNull(byTitle["postId"]?.takeIf { it.toString() != "null" })
            assertEquals("alice", byTitle.getValue("author").jsonPrimitive.content)
            assertEquals("general", byTitle.getValue("category").jsonPrimitive.content)

            val byBody =
                visitor
                    .find("compost")
                    .items()
                    .single()
                    .jsonObject
            assertEquals("post", byBody.getValue("kind").jsonPrimitive.content)
            assertEquals(
                thread,
                byBody
                    .getValue("threadId")
                    .jsonPrimitive.content
                    .toLong(),
            )
            assertEquals("Gardening tips", byBody.getValue("threadTitle").jsonPrimitive.content)
        }

    @Test
    fun `matching ignores case and snippets mark the match without HTML`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.thread("Question", "How do I water TOMATOES in a dry summer?")
            val snippet =
                alice
                    .find("tomatoes")
                    .items()
                    .single()
                    .jsonObject
                    .getValue("snippet")
                    .jsonPrimitive.content
            assertTrue("${MATCH_START}TOMATOES$MATCH_END" in snippet, snippet)
            assertTrue("<" !in snippet && "mark" !in snippet, snippet)
        }

    @Test
    fun `hostile text comes back as written, with only our markers added`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.thread("Markup", "Try <script>alert(1)</script> and <b>bold</b> here")
            val snippet =
                alice
                    .find("alert")
                    .items()
                    .single()
                    .jsonObject
                    .getValue("snippet")
                    .jsonPrimitive.content
            // The match is marked with control characters, so a page never needs to treat the snippet as HTML.
            assertTrue("${MATCH_START}alert$MATCH_END" in snippet, snippet)
            assertTrue("<" !in snippet && ">" !in snippet, "tags are not part of a snippet: $snippet")
            assertTrue("bold" in snippet, snippet)
        }

    @Test
    fun `tag names themselves are not searchable`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.thread("Markup", "Try <script>alert(1)</script> here")
            assertTrue(alice.find("script").items().isEmpty())
        }

    @Test
    fun `quotes, minus and or work, and odd input cannot break the query`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.thread("Soil", "red clay soil")
            alice.thread("Sand", "red sand")
            alice.thread("Mud", "brown clay")

            assertEquals(setOf("Soil", "Sand"), alice.find("red").titles().toSet())
            assertEquals(listOf("Soil"), alice.find("\"red clay\"").titles())
            assertEquals(listOf("Sand"), alice.find("red -clay").titles())
            assertEquals(setOf("Sand", "Mud"), alice.find("sand OR brown").titles().toSet())
            for (nasty in listOf("'; DROP TABLE posts; --", "\"", "((((", "& | !", "-", "\\", "%", "a".repeat(150))) {
                assertEquals(HttpStatusCode.OK, alice.search(nasty).status, nasty)
            }
            assertEquals(listOf("Soil"), alice.find("clay soil").titles())
        }

    @Test
    fun `hidden and deleted content and hidden threads never match`() =
        testApplication {
            app()
            val alice = user("alice")
            val bob = user("bob")
            val thread = alice.thread("Visible thread", "First post")
            val deleted = bob.reply(thread, "secret deleted words")
            val hidden = bob.reply(thread, "secret hidden words")
            bob.reply(thread, "secret public words")
            TestDatabase.execute("UPDATE posts SET deleted_at = now() WHERE id = $deleted")
            TestDatabase.execute("UPDATE posts SET hidden_at = now() WHERE id = $hidden")

            val found = alice.find("secret")
            assertEquals(1, found.items().size, found.toString())
            assertTrue(
                "public" in
                    found
                        .items()
                        .single()
                        .jsonObject
                        .getValue("snippet")
                        .jsonPrimitive.content,
            )

            TestDatabase.execute("UPDATE threads SET hidden_at = now() WHERE id = $thread")
            assertTrue(alice.find("secret").items().isEmpty())
            assertTrue(alice.find("visible").items().isEmpty())
        }

    @Test
    fun `an empty search finds nothing and a bad one is refused`() =
        testApplication {
            app()
            val alice = user("alice")
            alice.thread("Anything", "text")
            assertTrue(alice.find("   ").items().isEmpty())
            assertTrue(alice.find("").items().isEmpty())
            assertEquals(HttpStatusCode.BadRequest, alice.search("a".repeat(201)).status)
            assertEquals(HttpStatusCode.BadRequest, alice.search("text", "&limit=lots").status)
            assertEquals(HttpStatusCode.BadRequest, alice.search("text", "&cursor=-1").status)
            assertEquals(HttpStatusCode.BadRequest, alice.search("text", "&cursor=abc").status)
        }

    @Test
    fun `results are paged and stop at a limit`() =
        testApplication {
            app()
            val alice = user("alice")
            repeat(5) { alice.thread("Gardening part $it", "text") }

            val first = alice.find("gardening", "&limit=2")
            assertEquals(2, first.items().size)
            val cursor = first.getValue("next").jsonPrimitive.content
            val second = alice.find("gardening", "&limit=2&cursor=$cursor")
            val third = alice.find("gardening", "&limit=2&cursor=${second.getValue("next").jsonPrimitive.content}")
            assertEquals(1, third.items().size)
            assertEquals("null", third.getValue("next").toString())

            val all = (first.titles() + second.titles() + third.titles())
            assertEquals(5, all.toSet().size, "no repeats or gaps: $all")
            assertTrue(alice.find("gardening", "&cursor=100000").items().isEmpty())
        }

    @Test
    fun `a title match ranks above a body match, even when the body match is newer`() =
        testApplication {
            app()
            val alice = user("alice")
            // The title match is older. Without a boost, equal scores would put the newer body match first.
            alice.thread("Orchids", "a thread about plants")
            alice.thread("Unrelated", "orchids are lovely")
            val titles = alice.find("orchids").titles()
            assertEquals(listOf("Orchids", "Unrelated"), titles)
        }

    @Test
    fun `searching is rate limited per address`() =
        testApplication {
            app(searchPerMinute = 3)
            val visitor = createClient {}
            repeat(3) { assertEquals(HttpStatusCode.OK, visitor.search("x").status) }
            assertEquals(HttpStatusCode.TooManyRequests, visitor.search("x").status)
        }
}
