package dev.nilsschlegel.forum.forum

import io.ktor.server.plugins.BadRequestException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** A thread list position: where the last returned thread sits in the (last_post_at, id) order. */
internal data class ThreadCursor(
    val lastPostAt: Instant,
    val id: Long,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$lastPostAt|$id".toByteArray())

    companion object {
        fun decode(value: String): ThreadCursor =
            try {
                val (at, id) = String(Base64.getUrlDecoder().decode(value)).split('|')
                ThreadCursor(Instant.parse(at), id.toLong())
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("Not a valid cursor.", e)
            } catch (e: DateTimeParseException) {
                throw BadRequestException("Not a valid cursor.", e)
            } catch (e: IndexOutOfBoundsException) {
                throw BadRequestException("Not a valid cursor.", e)
            }
    }
}

/** A post list position: the id of the last returned post. */
internal fun decodePostCursor(value: String): Long = value.toLongOrNull() ?: throw BadRequestException("Not a valid cursor.")
