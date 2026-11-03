package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.TestDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStoreTest {
    private val tokens = TokenStore()
    private val users = DatabaseUserStore()
    private var userId = 0L

    @BeforeTest
    fun reset() {
        TestDatabase.reset()
        userId = runBlocking { users.signUp("alice").id }
    }

    @Test
    fun `a token works once`() =
        runBlocking {
            val token = tokens.issue(userId, TokenPurpose.VERIFY, "alice@example.com")
            assertEquals(TokenOwner(userId, "alice@example.com"), tokens.consume(token, TokenPurpose.VERIFY))
            assertNull(tokens.consume(token, TokenPurpose.VERIFY))
        }

    @Test
    fun `a token only works for its purpose`() =
        runBlocking {
            val token = tokens.issue(userId, TokenPurpose.RESET, null)
            assertNull(tokens.consume(token, TokenPurpose.VERIFY))
            assertNotNull(tokens.consume(token, TokenPurpose.RESET))
            Unit
        }

    @Test
    fun `an expired token does not work`() =
        runBlocking {
            val token = tokens.issue(userId, TokenPurpose.RESET, null)
            TestDatabase.execute("UPDATE email_tokens SET expires_at = now() - interval '1 second'")
            assertNull(tokens.consume(token, TokenPurpose.RESET))
        }

    @Test
    fun `a guessed token does not work`() =
        runBlocking {
            tokens.issue(userId, TokenPurpose.RESET, null)
            assertNull(tokens.consume("not-the-token", TokenPurpose.RESET))
        }

    @Test
    fun `a newer token replaces the older one`() =
        runBlocking {
            val first = tokens.issue(userId, TokenPurpose.RESET, null)
            val second = tokens.issue(userId, TokenPurpose.RESET, null)
            assertNotEquals(first, second)
            assertNull(tokens.consume(first, TokenPurpose.RESET))
            assertNotNull(tokens.consume(second, TokenPurpose.RESET))
            Unit
        }

    @Test
    fun `the database only holds a hash of the token`() =
        runBlocking {
            val token = tokens.issue(userId, TokenPurpose.RESET, null)
            val stored =
                TestDatabase.connection { c ->
                    c.createStatement().use { s ->
                        s.executeQuery("SELECT token_hash FROM email_tokens").use { rs ->
                            rs.next()
                            rs.getString(1)
                        }
                    }
                }
            assertEquals(64, stored.length)
            assertTrue(token !in stored)
        }

    @Test
    fun `revoking uses up the unused tokens of a purpose`() =
        runBlocking {
            val token = tokens.issue(userId, TokenPurpose.RESET, null)
            tokens.revokeAll(userId, TokenPurpose.RESET)
            assertNull(tokens.consume(token, TokenPurpose.RESET))
        }
}
