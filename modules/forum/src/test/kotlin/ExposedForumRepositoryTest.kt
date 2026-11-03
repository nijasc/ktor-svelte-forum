package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.core.EventBus
import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ALICE = 1L
private const val BOB = 2L
private val FIRST_PAGE = PageRequest(null, 20)

class ExposedForumRepositoryTest {
    private val directory =
        object : UserDirectory {
            private val names = mapOf(ALICE to "alice", BOB to "bob")

            override suspend fun usernames(ids: Collection<Long>) = names.filterKeys { it in ids }

            override suspend fun idOf(username: String) = names.entries.firstOrNull { it.value == username }?.key
        }
    private val events = mutableListOf<Any>()
    private val bus = EventBus().also { it.on<Any> { event -> events += event } }
    private val forum = ExposedForumRepository(directory, bus)

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

            val reply = assertIs<ReplyResult.Done>(forum.reply(older.id, BOB, "Reply")).post
            assertEquals("bob", reply.author)

            val threads = assertNotNull(forum.threads("general", FIRST_PAGE)).items
            assertEquals(listOf(older.id, newer.id), threads.map { it.id })
            assertEquals(listOf(1, 0), threads.map { it.replyCount })
            assertEquals(ReplyResult.NotFound, forum.reply(999, BOB, "Nowhere"))
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
            val withoutNames =
                ExposedForumRepository(
                    object : UserDirectory {
                        override suspend fun usernames(ids: Collection<Long>) = emptyMap<Long, String>()

                        override suspend fun idOf(username: String): Long? = null
                    },
                )
            assertEquals("deleted", withoutNames.thread(thread.id, FIRST_PAGE)?.author)
        }

    @Test
    fun `editing marks the post as edited and only the author may do it`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val postId =
                thread.posts.items
                    .single()
                    .id

            assertEquals(PostChange.NotYours, forum.editPost(postId, BOB, "Hijacked"))
            assertEquals(PostChange.NotFound, forum.editPost(999, ALICE, "Nowhere"))

            val edited = assertIs<PostChange.Done>(forum.editPost(postId, ALICE, "Better")).post
            assertEquals("Better", edited.body)
            assertNotNull(edited.editedAt)
            assertEquals(
                "Better",
                forum
                    .thread(thread.id, FIRST_PAGE)
                    ?.posts
                    ?.items
                    ?.single()
                    ?.body,
            )
        }

    @Test
    fun `deleting hides the text and lowers the reply count`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val reply = assertIs<ReplyResult.Done>(forum.reply(thread.id, BOB, "Secret")).post

            assertEquals(PostChange.NotYours, forum.deletePost(reply.id, ALICE))
            val deleted = assertIs<PostChange.Done>(forum.deletePost(reply.id, BOB)).post
            assertTrue(deleted.deleted)
            assertEquals("", deleted.body)

            val loaded = assertNotNull(forum.thread(thread.id, FIRST_PAGE))
            assertEquals(0, loaded.replyCount)
            assertEquals(listOf("Body", ""), loaded.posts.items.map { it.body })
            assertEquals(listOf(false, true), loaded.posts.items.map { it.deleted })
            assertEquals(PostChange.NotFound, forum.deletePost(reply.id, BOB))
            assertEquals(PostChange.NotFound, forum.editPost(reply.id, BOB, "Undelete"))
        }

    @Test
    fun `the first post of a thread cannot be deleted`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            assertEquals(
                PostChange.IsFirstPost,
                forum.deletePost(
                    thread.posts.items
                        .single()
                        .id,
                    ALICE,
                ),
            )
        }

    @Test
    fun `starting a thread and replying publish events`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Hi @bob"))
            val reply = assertIs<ReplyResult.Done>(forum.reply(thread.id, BOB, "Thanks")).post

            assertEquals(
                listOf<Any>(
                    ThreadCreated(thread.id, "Hello", ALICE, "Hi @bob"),
                    PostCreated(thread.id, "Hello", reply.id, BOB, ALICE, "Thanks"),
                ),
                events,
            )
        }

    @Test
    fun `a failed reply publishes nothing`() =
        runBlocking {
            forum.reply(999, BOB, "Nowhere")
            assertTrue(events.isEmpty())
        }

    @Test
    fun `a person has one reaction per post and can change or remove it`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val postId =
                thread.posts.items
                    .single()
                    .id

            assertEquals(PostReactions(mapOf("like" to 1), "like"), forum.react(postId, ALICE, Reaction.LIKE))
            assertEquals(PostReactions(mapOf("like" to 1, "love" to 1), "love"), forum.react(postId, BOB, Reaction.LOVE))
            assertEquals(PostReactions(mapOf("love" to 2), "love"), forum.react(postId, ALICE, Reaction.LOVE))
            assertEquals(PostReactions(mapOf("love" to 1), null), forum.unreact(postId, ALICE))
            assertEquals(PostReactions(mapOf("love" to 1), null), forum.unreact(postId, ALICE))
        }

    @Test
    fun `readers see the counts and visitors see no choice of their own`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val postId =
                thread.posts.items
                    .single()
                    .id
            forum.react(postId, ALICE, Reaction.LAUGH)
            forum.react(postId, BOB, Reaction.LAUGH)

            val asAlice = assertNotNull(forum.thread(thread.id, FIRST_PAGE, viewerId = ALICE)).posts.items.single()
            assertEquals(mapOf("laugh" to 2), asAlice.reactions)
            assertEquals("laugh", asAlice.myReaction)

            val asVisitor = assertNotNull(forum.thread(thread.id, FIRST_PAGE)).posts.items.single()
            assertEquals(mapOf("laugh" to 2), asVisitor.reactions)
            assertNull(asVisitor.myReaction)

            forum.unreact(postId, BOB)
            val asBob = assertNotNull(forum.thread(thread.id, FIRST_PAGE, viewerId = BOB)).posts.items.single()
            assertNull(asBob.myReaction)
            assertEquals(mapOf("laugh" to 1), asBob.reactions)
        }

    @Test
    fun `reactions are counted per post`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val reply = assertIs<ReplyResult.Done>(forum.reply(thread.id, BOB, "Reply")).post
            forum.react(
                thread.posts.items
                    .single()
                    .id,
                BOB,
                Reaction.LIKE,
            )
            forum.react(reply.id, ALICE, Reaction.CELEBRATE)

            val posts = assertNotNull(forum.thread(thread.id, FIRST_PAGE)).posts.items
            assertEquals(listOf(mapOf("like" to 1), mapOf("celebrate" to 1)), posts.map { it.reactions })
        }

    @Test
    fun `hidden, deleted and missing posts take no reactions and show none`() =
        runBlocking {
            val thread = assertNotNull(forum.createThread("general", ALICE, "Hello", "Body"))
            val reply = assertIs<ReplyResult.Done>(forum.reply(thread.id, BOB, "Reply")).post
            forum.react(reply.id, ALICE, Reaction.LIKE)

            ExposedForumStaff(directory).setPostHidden(reply.id, true)
            assertNull(forum.react(reply.id, ALICE, Reaction.LOVE))
            assertNull(forum.unreact(reply.id, ALICE))
            val hidden = assertNotNull(forum.thread(thread.id, FIRST_PAGE, ALICE)).posts.items.last()
            assertEquals(emptyMap(), hidden.reactions)
            assertNull(hidden.myReaction)

            ExposedForumStaff(directory).setPostHidden(reply.id, false)
            assertEquals(
                mapOf("like" to 1),
                assertNotNull(forum.thread(thread.id, FIRST_PAGE))
                    .posts.items
                    .last()
                    .reactions,
            )

            forum.deletePost(reply.id, BOB)
            assertNull(forum.react(reply.id, ALICE, Reaction.LOVE))
            assertNull(forum.react(999, ALICE, Reaction.LIKE))
        }
}
