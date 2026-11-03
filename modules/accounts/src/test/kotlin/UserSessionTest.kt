package dev.nilsschlegel.forum.accounts

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UserSessionTest {
    @Test
    fun `the start time is always in the cookie, even when it equals the current time`() {
        // A property with a default would be left out when it equals the default, and read back as "now".
        repeat(1_000) {
            val now = System.currentTimeMillis()
            val text = Json.encodeToString(UserSession.serializer(), UserSession(7, now))
            assertTrue(""""issuedAt":$now""" in text, text)
        }
    }

    @Test
    fun `a cookie without a start time is not a session`() {
        assertFailsWith<SerializationException> { Json.decodeFromString(UserSession.serializer(), """{"userId":7}""") }
    }

    @Test
    fun `a session keeps its start time`() {
        val session = Json.decodeFromString(UserSession.serializer(), Json.encodeToString(UserSession.serializer(), UserSession(7, 1234)))
        assertEquals(UserSession(7, 1234), session)
    }
}
