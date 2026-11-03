package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.AuthUser
import dev.nilsschlegel.forum.accounts.Role
import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.core.ApiException
import dev.nilsschlegel.forum.core.RateLimits
import dev.nilsschlegel.forum.core.configureRateLimits
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
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
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeForumRepository : ForumRepository {
    private val threads = mutableListOf<ThreadDetail>()

    /** The last page request a route passed on, so tests can check cursor and limit handling. */
    var lastPage: PageRequest? = null

    override suspend fun categories() = listOf(Category("general", "General", "Talk about anything."))

    override suspend fun threads(
        categorySlug: String,
        page: PageRequest,
    ): ThreadPage? {
        lastPage = page
        if (categorySlug != "general") return null
        val newestFirst = threads.reversed().filter { page.cursor == null || it.id < page.cursor.toLong() }
        val shown = newestFirst.take(page.limit)
        val items = shown.map { ThreadSummary(it.id, it.title, it.author, it.createdAt, it.replyCount, NOW) }
        return ThreadPage(items, if (newestFirst.size > page.limit) shown.last().id.toString() else null)
    }

    override suspend fun thread(
        id: Long,
        page: PageRequest,
    ): ThreadDetail? {
        lastPage = page
        return threads.find { it.id == id }
    }

    override suspend fun createThread(
        categorySlug: String,
        authorId: Long,
        title: String,
        body: String,
    ): ThreadDetail? {
        if (categorySlug != "general") return null
        val id = threads.size + 1L
        val posts = PostPage(listOf(Post(1, "user$authorId", body, NOW)), null)
        return ThreadDetail(id, categorySlug, title, "user$authorId", NOW, 0, posts).also { threads += it }
    }

    override suspend fun reply(
        threadId: Long,
        authorId: Long,
        body: String,
    ): Post? {
        val thread = threads.find { it.id == threadId } ?: return null
        val post = Post(thread.posts.items.size + 1L, "user$authorId", body, NOW)
        threads[threads.indexOf(thread)] =
            thread.copy(replyCount = thread.replyCount + 1, posts = PostPage(thread.posts.items + post, null))
        return post
    }

    /** Adds a reply by a given user without going through the routes, so tests can set up other people's posts. */
    suspend fun seedReply(
        threadId: Long,
        authorId: Long,
        body: String,
    ) = reply(threadId, authorId, body)

    override suspend fun editPost(
        postId: Long,
        userId: Long,
        body: String,
    ): PostChange {
        val (thread, post) = find(postId) ?: return PostChange.NotFound
        if (post.author != "user$userId") return PostChange.NotYours
        val edited = post.copy(body = body, editedAt = NOW)
        replace(thread, edited)
        return PostChange.Done(edited)
    }

    override suspend fun deletePost(
        postId: Long,
        userId: Long,
    ): PostChange {
        val (thread, post) = find(postId) ?: return PostChange.NotFound
        if (post.author != "user$userId") return PostChange.NotYours
        if (thread.posts.items
                .first()
                .id == postId
        ) {
            return PostChange.IsFirstPost
        }
        val deleted = post.copy(body = "", deleted = true)
        replace(thread, deleted)
        return PostChange.Done(deleted)
    }

    private fun find(postId: Long) =
        threads.firstNotNullOfOrNull { t ->
            t.posts.items.find { it.id == postId && !it.deleted }?.let {
                t to
                    it
            }
        }

    private fun replace(
        thread: ThreadDetail,
        post: Post,
    ) {
        val items = thread.posts.items.map { if (it.id == post.id) post else it }
        threads[threads.indexOf(thread)] = thread.copy(posts = PostPage(items, null))
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
            context.principal(AuthUser(1, "alice", Role.MEMBER))
        } else {
            context.challenge("test", AuthenticationFailedCause.NoCredentials) { challenge, call ->
                call.respond(HttpStatusCode.Unauthorized)
                challenge.complete()
            }
        }
    }
}

private fun ApplicationTestBuilder.forumApp(
    repository: FakeForumRepository = FakeForumRepository(),
    writesPerMinute: Int = RateLimits.WRITE_PER_MINUTE,
) {
    application { forumModule(repository, writesPerMinute) }
}

private fun Application.forumModule(
    repository: ForumRepository,
    writesPerMinute: Int,
) {
    configureRateLimits(writePerMinute = writesPerMinute)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest) }
        exception<ApiException> { call, cause -> call.respond(cause.status, cause.message) }
    }
    install(Authentication) { register(TestAuth(TestAuth.Config())) }
    routing { forumRoutes(repository) }
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

    @Test
    fun `thread lists are paged with a cursor`() =
        testApplication {
            forumApp()
            repeat(3) {
                client.post("/api/categories/general/threads") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"title":"Thread $it","body":"Body"}""")
                }
            }
            val first = client.get("/api/categories/general/threads?limit=2").bodyAsText()
            assertTrue("Thread 2" in first && "Thread 1" in first && "Thread 0" !in first)
            assertTrue(""""next":"2"""" in first)
            val second = client.get("/api/categories/general/threads?limit=2&cursor=2").bodyAsText()
            assertTrue("Thread 0" in second && """"next":null""" in second)
        }

    @Test
    fun `limit defaults and is clamped`() =
        testApplication {
            val repository = FakeForumRepository()
            forumApp(repository)
            client.get("/api/categories/general/threads")
            assertEquals(PageRequest.DEFAULT_LIMIT, repository.lastPage?.limit)
            client.get("/api/categories/general/threads?limit=100000")
            assertEquals(PageRequest.MAX_LIMIT, repository.lastPage?.limit)
            client.get("/api/categories/general/threads?limit=0")
            assertEquals(1, repository.lastPage?.limit)
            assertEquals(HttpStatusCode.BadRequest, client.get("/api/categories/general/threads?limit=lots").status)
        }

    @Test
    fun `writing is rate limited per user`() =
        testApplication {
            forumApp(writesPerMinute = 2)

            suspend fun reply() =
                client.post("/api/threads/1/posts") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"body":"Hi"}""")
                }
            client.post("/api/categories/general/threads") {
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Hi","body":"Hi"}""")
            }
            assertEquals(HttpStatusCode.Created, reply().status)
            val limited = reply()
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertTrue(limited.headers["Retry-After"] != null)
            assertEquals(HttpStatusCode.OK, client.get("/api/categories").status)
        }

    private suspend fun ApplicationTestBuilder.startThread() {
        client.post("/api/categories/general/threads") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Hi","body":"First"}""")
        }
    }

    private suspend fun ApplicationTestBuilder.edit(
        postId: Long,
        body: String,
    ) = client.patch("/api/posts/$postId") {
        contentType(ContentType.Application.Json)
        setBody("""{"body":"$body"}""")
    }

    @Test
    fun `an author can edit their own post`() =
        testApplication {
            forumApp()
            startThread()
            val response = edit(1, "  Better  ")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(""""body":"Better"""" in response.bodyAsText())
            assertTrue(""""editedAt":""" in response.bodyAsText())
        }

    @Test
    fun `nobody can change someone else's post`() =
        testApplication {
            val repository = FakeForumRepository()
            forumApp(repository)
            startThread()
            repository.seedReply(1, authorId = 2, body = "Theirs")
            assertEquals(HttpStatusCode.Forbidden, edit(2, "Mine now").status)
            assertEquals(HttpStatusCode.Forbidden, client.delete("/api/posts/2").status)
            assertEquals(HttpStatusCode.NotFound, edit(99, "Nowhere").status)
        }

    @Test
    fun `a reply can be deleted but the first post cannot`() =
        testApplication {
            val repository = FakeForumRepository()
            forumApp(repository)
            startThread()
            repository.seedReply(1, authorId = 1, body = "Mine")
            assertEquals(HttpStatusCode.Forbidden, client.delete("/api/posts/1").status)
            assertEquals(HttpStatusCode.NoContent, client.delete("/api/posts/2").status)
            assertEquals(HttpStatusCode.NotFound, client.delete("/api/posts/2").status)
        }
}
