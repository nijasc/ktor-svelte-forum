package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `finds ids and summaries ignoring case`() =
        runBlocking {
            val user = assertNotNull(store.create("Alice", "hash"))
            assertEquals(user.id, store.idOf("ALICE"))
            assertNull(store.idOf("bob"))
            assertEquals(UserSummary(user.id, "Alice", Role.MEMBER, UserStatus.ACTIVE), store.find("alice"))
        }

    @Test
    fun `changes status and role`() =
        runBlocking {
            val user = assertNotNull(store.create("Alice", "hash"))
            store.setStatus(user.id, UserStatus.BANNED)
            store.setRole(user.id, Role.MODERATOR)
            assertEquals(UserSummary(user.id, "Alice", Role.MODERATOR, UserStatus.BANNED), store.find("Alice"))
            store.setStatus(user.id, UserStatus.ACTIVE)
            assertEquals(UserStatus.ACTIVE, store.find("Alice")?.status)
        }

    @Test
    fun `the configured user becomes an admin when there is none`() =
        runBlocking {
            val withAdmin = DatabaseUserStore(initialAdmin = "Root")
            assertEquals(Role.MEMBER, assertNotNull(withAdmin.create("alice", "hash")).role)
            assertEquals(Role.ADMIN, assertNotNull(withAdmin.create("ROOT", "hash")).role)
        }

    @Test
    fun `the configured name gets no role once an admin exists`() =
        runBlocking {
            val withAdmin = DatabaseUserStore(initialAdmin = "root")
            val boss = assertNotNull(withAdmin.create("boss", "hash"))
            withAdmin.setRole(boss.id, Role.ADMIN)
            assertEquals(Role.MEMBER, assertNotNull(withAdmin.create("root", "hash")).role)
        }

    @Test
    fun `no admin is made without the setting`() =
        runBlocking {
            assertEquals(Role.MEMBER, assertNotNull(store.create("root", "hash")).role)
        }
}

class RolesTest {
    @Test
    fun `roles are ordered from member to admin`() {
        assertTrue(Role.MEMBER < Role.MODERATOR && Role.MODERATOR < Role.ADMIN)
    }

    @Test
    fun `moderators ban members, admins ban moderators, nobody bans admins`() {
        assertTrue(canBan(Role.MODERATOR, Role.MEMBER))
        assertFalse(canBan(Role.MEMBER, Role.MEMBER))
        assertFalse(canBan(Role.MODERATOR, Role.MODERATOR))
        assertTrue(canBan(Role.ADMIN, Role.MODERATOR))
        assertFalse(canBan(Role.ADMIN, Role.ADMIN))
        assertFalse(canBan(Role.MODERATOR, Role.ADMIN))
    }
}
