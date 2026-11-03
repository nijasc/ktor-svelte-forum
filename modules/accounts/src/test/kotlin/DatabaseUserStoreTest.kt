package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseUserStoreTest {
    private val store = DatabaseUserStore()

    @BeforeTest
    fun reset() = TestDatabase.reset()

    @Test
    fun `creates a member who is active`() =
        runBlocking {
            val user = assertNotNull(store.create("Alice", "hash"))
            assertEquals("Alice", user.username)
            assertEquals(Role.MEMBER, user.role)
            assertEquals(UserStatus.ACTIVE, user.status)
            assertEquals(user, store.findById(user.id))
        }

    @Test
    fun `usernames are unique ignoring case`() =
        runBlocking {
            assertNotNull(store.create("Alice", "hash"))
            assertNull(store.create("alice", "other"))
            assertNull(store.create("ALICE", "other"))
        }

    @Test
    fun `finds a user ignoring case`() =
        runBlocking {
            val user = assertNotNull(store.create("Alice", "hash"))
            assertEquals(user.id, store.findByUsername("aLiCe")?.id)
            assertNull(store.findByUsername("bob"))
        }

    @Test
    fun `looks up names by id and leaves out unknown ids`() =
        runBlocking {
            val alice = assertNotNull(store.create("Alice", "hash"))
            val bob = assertNotNull(store.create("Bob", "hash"))
            assertEquals(mapOf(alice.id to "Alice", bob.id to "Bob"), store.usernames(listOf(alice.id, bob.id, 999)))
            assertEquals(emptyMap(), store.usernames(emptyList()))
        }

    @Test
    fun `reads a banned user`() =
        runBlocking {
            val user = assertNotNull(store.create("Alice", "hash"))
            TestDatabase.execute("UPDATE users SET status = 'banned', role = 'moderator' WHERE id = ${user.id}")
            val stored = assertNotNull(store.findById(user.id))
            assertEquals(UserStatus.BANNED, stored.status)
            assertEquals(Role.MODERATOR, stored.role)
        }
}
