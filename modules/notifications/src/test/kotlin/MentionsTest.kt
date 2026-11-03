package dev.nilsschlegel.forum.notifications

import kotlin.test.Test
import kotlin.test.assertEquals

class MentionsTest {
    @Test
    fun `finds names after an at sign`() {
        assertEquals(listOf("bob", "carol-2", "dave_x"), mentionsIn("Hi @bob, and @carol-2! Also (@dave_x)."))
    }

    @Test
    fun `ignores email addresses and doubled signs`() {
        assertEquals(emptyList(), mentionsIn("write to bob@example.com or @@bob"))
    }

    @Test
    fun `counts a name once, ignoring case`() {
        assertEquals(listOf("Bob"), mentionsIn("@Bob @bob @BOB"))
    }

    @Test
    fun `stops at ten names`() {
        assertEquals(10, mentionsIn((1..30).joinToString(" ") { "@user$it" }).size)
    }

    @Test
    fun `finds a name at the start and at the end`() {
        assertEquals(listOf("a", "b"), mentionsIn("@a hello @b"))
    }
}
