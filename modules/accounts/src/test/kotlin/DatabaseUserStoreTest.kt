package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal suspend fun DatabaseUserStore.signUp(
    name: String,
    email: String = "${name.lowercase()}@example.com",
): UserRecord = assertIs<CreateResult.Created>(create(name, email, "hash")).user

class DatabaseUserStoreTest {
    private val store = DatabaseUserStore()

    @BeforeTest
    fun reset() = TestDatabase.reset()

    @Test
    fun `creates a member who is active and has not confirmed the address`() =
        runBlocking {
            val user = store.signUp("Alice")
            assertEquals("Alice", user.username)
            assertEquals("alice@example.com", user.email)
            assertEquals(Role.MEMBER, user.role)
            assertEquals(UserStatus.ACTIVE, user.status)
            assertFalse(user.emailVerified)
            assertEquals(user, store.findById(user.id))
        }

    @Test
    fun `usernames are unique ignoring case`() =
        runBlocking {
            store.signUp("Alice")
            assertEquals(CreateResult.UsernameTaken, store.create("alice", "other@example.com", "x"))
            assertEquals(CreateResult.UsernameTaken, store.create("ALICE", "other@example.com", "x"))
        }

    @Test
    fun `email addresses are unique ignoring case`() =
        runBlocking {
            store.signUp("Alice", "Alice@Example.com")
            assertEquals(CreateResult.EmailTaken, store.create("bob", "alice@example.com", "x"))
            assertEquals("Alice", store.findByEmail("ALICE@EXAMPLE.COM")?.username)
            assertNull(store.findByEmail("nobody@example.com"))
        }

    @Test
    fun `finds a user ignoring case`() =
        runBlocking {
            val user = store.signUp("Alice")
            assertEquals(user.id, store.findByUsername("aLiCe")?.id)
            assertNull(store.findByUsername("bob"))
        }

    @Test
    fun `looks up names by id and leaves out unknown ids`() =
        runBlocking {
            val alice = store.signUp("Alice")
            val bob = store.signUp("Bob")
            assertEquals(mapOf(alice.id to "Alice", bob.id to "Bob"), store.usernames(listOf(alice.id, bob.id, 999)))
            assertEquals(emptyMap(), store.usernames(emptyList()))
        }

    @Test
    fun `reads a banned user`() =
        runBlocking {
            val user = store.signUp("Alice")
            TestDatabase.execute("UPDATE users SET status = 'banned', role = 'moderator' WHERE id = ${user.id}")
            val stored = assertNotNull(store.findById(user.id))
            assertEquals(UserStatus.BANNED, stored.status)
            assertEquals(Role.MODERATOR, stored.role)
        }

    @Test
    fun `finds ids and summaries ignoring case`() =
        runBlocking {
            val user = store.signUp("Alice")
            assertEquals(user.id, store.idOf("ALICE"))
            assertNull(store.idOf("bob"))
            assertEquals(UserSummary(user.id, "Alice", Role.MEMBER, UserStatus.ACTIVE), store.find("alice"))
        }

    @Test
    fun `changes status and role`() =
        runBlocking {
            val user = store.signUp("Alice")
            store.setStatus(user.id, UserStatus.BANNED)
            store.setRole(user.id, Role.MODERATOR)
            assertEquals(UserSummary(user.id, "Alice", Role.MODERATOR, UserStatus.BANNED), store.find("Alice"))
            store.setStatus(user.id, UserStatus.ACTIVE)
            assertEquals(UserStatus.ACTIVE, store.find("Alice")?.status)
        }

    @Test
    fun `verifying and changing the address`() =
        runBlocking {
            val alice = store.signUp("Alice")
            val bob = store.signUp("Bob")
            store.markVerified(alice.id)
            assertTrue(assertNotNull(store.findById(alice.id)).emailVerified)

            assertFalse(store.changeEmail(alice.id, "BOB@example.com"))
            assertTrue(store.changeEmail(alice.id, "new@example.com"))
            val changed = assertNotNull(store.findById(alice.id))
            assertEquals("new@example.com", changed.email)
            assertFalse(changed.emailVerified)
            assertTrue(store.changeEmail(bob.id, "bob@example.com"))
        }

    @Test
    fun `a new password is recorded`() =
        runBlocking {
            val user = store.signUp("Alice")
            assertNull(user.passwordChangedAt)
            store.setPassword(user.id, "new-hash")
            val stored = assertNotNull(store.findById(user.id))
            assertEquals("new-hash", stored.passwordHash)
            assertNotNull(stored.passwordChangedAt)
        }

    @Test
    fun `the configured user becomes a verified admin when there is none`() =
        runBlocking {
            val withAdmin = DatabaseUserStore(initialAdmin = "Root")
            val member = withAdmin.signUp("alice")
            assertEquals(Role.MEMBER, member.role)
            assertFalse(member.emailVerified)
            val admin = withAdmin.signUp("ROOT")
            assertEquals(Role.ADMIN, admin.role)
            assertTrue(admin.emailVerified)
        }

    @Test
    fun `the configured name gets no role once an admin exists`() =
        runBlocking {
            val withAdmin = DatabaseUserStore(initialAdmin = "root")
            val boss = withAdmin.signUp("boss")
            withAdmin.setRole(boss.id, Role.ADMIN)
            assertEquals(Role.MEMBER, withAdmin.signUp("root").role)
        }

    @Test
    fun `no admin is made without the setting`() =
        runBlocking {
            assertEquals(Role.MEMBER, store.signUp("root").role)
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
