package dev.nilsschlegel.forum.notifications

private const val MAX_MENTIONS = 10
private val MENTION = Regex("(?<![\\w@])@([A-Za-z0-9_-]{1,64})")

/**
 * The names written after an `@` in [text], without repeats and at most ten, so one post cannot notify the whole forum.
 * Only names made of letters, digits, `_` and `-` can be mentioned.
 */
internal fun mentionsIn(text: String): List<String> =
    MENTION
        .findAll(text)
        .map { it.groupValues[1] }
        .distinctBy { it.lowercase() }
        .take(MAX_MENTIONS)
        .toList()
