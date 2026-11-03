package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val ALICE = 1L
private const val BOB = 2L
private val FIRST_PAGE = PageRequest(null, 20)

class ExposedForumRepositoryTest {
    private val directory =
        object : UserDirectory {
            override suspend fun usernames(ids: Collection<Long>) = mapOf(ALICE to "alice", BOB to "bob").filterKeys { it in ids }
        }
    private val forum = ExposedForumRepository(directory)

    @BeforeTest
    fun reset() {
        TestDatabase.reset()
        TestDatabase.execute("INSERT INTO users (id, username, password_hash) VALUES ($ALICE, 'alice', 'x'), ($BOB, 'bob', 'x')")
    }

    @Test
    fun `lists the seeded categories in order`() =
        runBlocking {
            assertEquals(listOf("general", "help"), forum.categories().map { it.slug })
        }

    @Test
    fun `a new thread has its body as the first post`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "First post"))
            assertEquals("alice", thread.author)
            assertEquals(0, thread.replyCount)
            assertEquals(listOf("First post"), thread.posts.items.map { it.body })
            assertNull(forum.createThread("nope", ALICE, "Hello", "Body"))
        }

    @Test
    fun `replying counts the reply and moves the thread to the top`() =
        runBlocking {
            val older = assertNotNull(forum.createThread("general", ALICE, "Older", "a"))
            val newer = assertNotNull(forum.createThread("general", ALICE, "Newer", "b"))
            assertEquals(listOf(newer.id, older.id), forum.threads("general", FIRST_PAGE)?.items?.map { it.id })

            val reply = assertNotNull(forum.reply(older.id, BOB, "Reply"))
            assertEquals("bob", reply.author)

            val threads = assertNotNull(forum.threads("general", FIRST_PAGE)).items
            assertEquals(listOf(older.id, newer.id), threads.map { it.id })
            assertEquals(listOf(1, 0), threads.map { it.replyCount })
            assertNull(forum.reply(999, BOB, "Nowhere"))
        }

    @Test
    fun `threads are paged without gaps or repeats`() =
        runBlocking {
            val ids = (1..5).map { assertNotNull(forum.createThread("general", ALICE, "T$it", "b")).id }
            val seen = mutableListOf<Long>()
            var cursor: String? = null
            do {
                val page = assertNotNull(forum.threads("general", PageRequest(cursor, 2)))
                seen += page.items.map { it.id }
                cursor = page.next
            } while (cursor != null)
            assertEquals(ids.reversed(), seen)
            assertNull(forum.threads("nope", FIRST_PAGE))
        }

    @Test
    fun `threads created in the same instant still page correctly`() =
        runBlocking {
            TestDatabase.execute(
                """
                INSERT INTO threads (category_id, title, author_id, created_at, last_post_at)
                SELECT 1, 'T' || n, $ALICE, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z' FROM generate_series(1, 5) AS n
                """.trimIndent(),
            )
            val first = assertNotNull(forum.threads("general", PageRequest(null, 2)))
            val second = assertNotNull(forum.threads("general", PageRequest(first.next, 2)))
            val third = assertNotNull(forum.threads("general", PageRequest(second.next, 2)))
            val all = (first.items + second.items + third.items).map { it.id }
            assertEquals(all.distinct(), all)
            assertEquals(5, all.size)
            assertNull(third.next)
        }

    @Test
    fun `posts are paged oldest first`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "p0"))
            (1..4).forEach { forum.reply(thread.id, BOB, "p$it") }

            val first = assertNotNull(forum.thread(thread.id, PageRequest(null, 2))).posts
            assertEquals(listOf("p0", "p1"), first.items.map { it.body })
            val second = assertNotNull(forum.thread(thread.id, PageRequest(first.next, 2))).posts
            assertEquals(listOf("p2", "p3"), second.items.map { it.body })
            val third = assertNotNull(forum.thread(thread.id, PageRequest(second.next, 2))).posts
            assertEquals(listOf("p4"), third.items.map { it.body })
            assertNull(third.next)
            assertEquals(4, forum.thread(thread.id, FIRST_PAGE)?.replyCount)
        }

    @Test
    fun `shows deleted for an author with no user`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val withoutNames = ExposedForumRepository(UserDirectory { emptyMap() })
            assertEquals("deleted", withoutNames.thread(thread.id, FIRST_PAGE)?.author)
        }
}
