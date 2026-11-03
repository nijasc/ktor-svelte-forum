package dev.nilsschlegel.forum.forum

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The frontend repeats the length limits so the editor can count characters. This keeps the two copies in step. */
class FrontendLimitsTest {
    private val limits =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { it.resolve("app/frontend/src/lib/limits.ts") }
            .firstOrNull { it.isFile }

    private fun frontendValue(name: String): Int {
        val text = assertNotNull(limits, "app/frontend/src/lib/limits.ts not found").readText()
        val value = Regex("""export const $name = ([0-9_]+);""").find(text)?.groupValues?.get(1)
        return assertNotNull(value, "$name is missing in limits.ts").replace("_", "").toInt()
    }

    @Test
    fun `title limit matches the frontend`() = assertEquals(MAX_TITLE_LENGTH, frontendValue("MAX_TITLE_LENGTH"))

    @Test
    fun `body limit matches the frontend`() = assertEquals(MAX_BODY_LENGTH, frontendValue("MAX_BODY_LENGTH"))
}
