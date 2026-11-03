package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
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

class ExposedForumStaffTest {
    private val directory =
        object : UserDirectory {
            private val names = mapOf(ALICE to "alice", BOB to "bob")

            override suspend fun usernames(ids: Collection<Long>) = names.filterKeys { it in ids }

            override suspend fun idOf(username: String) = names.entries.firstOrNull { it.value == username }?.key
        }
    private val forum = ExposedForumRepository(directory)
    private val staff = ExposedForumStaff(directory)

    @BeforeTest
    fun reset() {
        TestDatabase.reset()
        TestDatabase.execute("INSERT INTO users (id, username, password_hash) VALUES ($ALICE, 'alice', 'x'), ($BOB, 'bob', 'x')")
    }

    private suspend fun thread(title: String = "Hello") = assertNotNull(forum.createThread("general", ALICE, title, "Body"))

    private suspend fun reply(
        threadId: Long,
        body: String = "Reply",
    ) = assertIs<ReplyResult.Done>(forum.reply(threadId, BOB, body)).post

    @Test
    fun `pinned threads come first on the first page only`() =
        runBlocking {
            val old = thread("Old")
            val newer = thread("Newer")
            thread("Newest")
            assertTrue(staff.setThreadPinned(old.id, true))

            val first = assertNotNull(forum.threads("general", PageRequest(null, 1)))
            assertEquals(listOf(old.id), first.pinned.map { it.id })
            assertEquals(listOf(true), first.pinned.map { it.pinned })
            assertEquals(1, first.items.size)
            assertTrue(old.id !in first.items.map { it.id })

            val second = assertNotNull(forum.threads("general", PageRequest(first.next, 1)))
            assertTrue(second.pinned.isEmpty())
            assertEquals(listOf(newer.id), second.items.map { it.id })
            assertFalse(staff.setThreadPinned(999, true))
        }

    private fun assertFalse(value: Boolean) = assertTrue(!value)

    @Test
    fun `a locked thread takes no replies until it is unlocked`() =
        runBlocking {
            val thread = thread()
            assertTrue(staff.setThreadLocked(thread.id, true))
            assertEquals(ReplyResult.Locked, forum.reply(thread.id, BOB, "Late"))
            assertEquals(true, forum.thread(thread.id, FIRST_PAGE)?.locked)

            staff.setThreadLocked(thread.id, false)
            assertIs<ReplyResult.Done>(forum.reply(thread.id, BOB, "On time"))
            Unit
        }

    @Test
    fun `a hidden thread disappears from lists and reading until it is restored`() =
        runBlocking {
            val thread = thread()
            staff.setThreadHidden(thread.id, true)
            assertEquals(emptyList(), forum.threads("general", FIRST_PAGE)?.items)
            assertNull(forum.thread(thread.id, FIRST_PAGE))
            assertEquals(ReplyResult.NotFound, forum.reply(thread.id, BOB, "Hello?"))
            assertEquals(0, forum.activity(ALICE).threadCount)

            staff.setThreadHidden(thread.id, false)
            assertEquals(listOf(thread.id), forum.threads("general", FIRST_PAGE)?.items?.map { it.id })
        }

    @Test
    fun `moving a thread changes its category`() =
        runBlocking {
            val thread = thread()
            assertEquals(MoveResult.MOVED, staff.moveThread(thread.id, "help"))
            assertEquals(emptyList(), forum.threads("general", FIRST_PAGE)?.items)
            assertEquals(listOf(thread.id), forum.threads("help", FIRST_PAGE)?.items?.map { it.id })
            assertEquals(MoveResult.NO_CATEGORY, staff.moveThread(thread.id, "nope"))
            assertEquals(MoveResult.NO_THREAD, staff.moveThread(999, "help"))
        }

    @Test
    fun `hiding a post masks it and keeps the reply count right, also when restored`() =
        runBlocking {
            val thread = thread()
            val reply = reply(thread.id, "Rude")
            assertEquals(1, forum.thread(thread.id, FIRST_PAGE)?.replyCount)

            val hidden = assertIs<HideResult.Done>(staff.setPostHidden(reply.id, true)).post
            assertEquals("Rude", hidden.body)
            assertTrue(hidden.hidden)
            val shown = assertNotNull(forum.thread(thread.id, FIRST_PAGE))
            assertEquals(0, shown.replyCount)
            assertEquals(listOf("Body", ""), shown.posts.items.map { it.body })
            assertEquals(listOf(false, true), shown.posts.items.map { it.hidden })
            assertEquals(0, forum.activity(BOB).postCount)

            // Hiding twice must not take the count down twice.
            staff.setPostHidden(reply.id, true)
            assertEquals(0, forum.thread(thread.id, FIRST_PAGE)?.replyCount)

            staff.setPostHidden(reply.id, false)
            val back = assertNotNull(forum.thread(thread.id, FIRST_PAGE))
            assertEquals(1, back.replyCount)
            assertEquals(listOf("Body", "Rude"), back.posts.items.map { it.body })
        }

    @Test
    fun `hiding a post its author already deleted does not change the count twice`() =
        runBlocking {
            val thread = thread()
            val reply = reply(thread.id)
            forum.deletePost(reply.id, BOB)
            assertEquals(0, forum.thread(thread.id, FIRST_PAGE)?.replyCount)
            staff.setPostHidden(reply.id, true)
            staff.setPostHidden(reply.id, false)
            assertEquals(0, forum.thread(thread.id, FIRST_PAGE)?.replyCount)
        }

    @Test
    fun `the first post cannot be hidden and unknown posts are not found`() =
        runBlocking {
            val thread = thread()
            assertEquals(
                HideResult.IsFirstPost,
                staff.setPostHidden(
                    thread.posts.items
                        .single()
                        .id,
                    true,
                ),
            )
            assertEquals(HideResult.NotFound, staff.setPostHidden(999, true))
            assertNull(staff.post(999))
        }

    @Test
    fun `staff read posts with their text and thread`() =
        runBlocking {
            val thread = thread("Topic")
            val reply = reply(thread.id, "Text")
            val post = assertNotNull(staff.post(reply.id))
            assertEquals(StaffPost(reply.id, thread.id, "Topic", BOB, "bob", "Text", post.createdAt, hidden = false, deleted = false), post)
            assertEquals(setOf(reply.id), staff.posts(listOf(reply.id, 999)).keys)
        }

    @Test
    fun `archived categories leave the public list and take no new threads`() =
        runBlocking {
            val before = thread("Before")
            assertEquals("help", staff.updateCategory("help", null, null, archived = true)?.slug)
            assertEquals(listOf("general"), forum.categories().map { it.slug })
            assertEquals(listOf("general", "help"), staff.categories().map { it.slug })
            assertNull(forum.createThread("help", ALICE, "New", "Body"))

            staff.updateCategory("general", null, null, archived = true)
            assertEquals(listOf(before.id), forum.threads("general", FIRST_PAGE)?.items?.map { it.id })
            assertNotNull(forum.thread(before.id, FIRST_PAGE))

            staff.updateCategory("help", null, null, archived = false)
            assertNotNull(forum.createThread("help", ALICE, "New", "Body"))
        }

    @Test
    fun `categories can be created, renamed and put in order`() =
        runBlocking {
            val created = assertNotNull(staff.createCategory("news", "News", "Announcements."))
            assertEquals(2, created.position)
            assertNull(staff.createCategory("news", "Again", ""))

            val renamed = assertNotNull(staff.updateCategory("news", "Updates", null, null))
            assertEquals("Updates", renamed.title)
            assertEquals("Announcements.", renamed.description)
            assertNull(staff.updateCategory("nope", "x", null, null))

            assertTrue(staff.reorderCategories(listOf("news", "help", "general")))
            assertEquals(listOf("news", "help", "general"), staff.categories().map { it.slug })
            assertEquals(listOf("news", "help", "general"), forum.categories().map { it.slug })
            assertTrue(!staff.reorderCategories(listOf("news", "help")))
            assertTrue(!staff.reorderCategories(listOf("news", "help", "general", "extra")))
        }

    @Test
    fun `activity lists a user's visible threads and posts with counts`() =
        runBlocking {
            val thread = thread("Mine")
            reply(thread.id, "x".repeat(300))
            val activity = forum.activity(ALICE)
            assertEquals(1, activity.threadCount)
            assertEquals(1, activity.postCount)
            assertEquals(listOf("Mine"), activity.threads.map { it.title })
            assertEquals(listOf("Body"), activity.posts.map { it.excerpt })

            val bobs = forum.activity(BOB)
            assertEquals(
                EXCERPT_LENGTH,
                bobs.posts
                    .single()
                    .excerpt.length,
            )
            assertEquals("Mine", bobs.posts.single().threadTitle)
        }
}
