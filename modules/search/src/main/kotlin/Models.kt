package dev.nilsschlegel.forum.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HitKind {
    /** The words are in the thread's title. */
    @SerialName("thread")
    THREAD,

    /** The words are in a post. */
    @SerialName("post")
    POST,
}

/**
 * One result. [snippet] is plain text with the matching words wrapped in [MATCH_START] and [MATCH_END], which are
 * control characters and not HTML, so a page can show it safely by splitting on them.
 */
@Serializable
data class SearchHit(
    val kind: HitKind,
    val threadId: Long,
    val postId: Long?,
    val threadTitle: String,
    val snippet: String,
    val author: String,
    val category: String,
    val createdAt: String,
)

@Serializable
data class SearchPage(
    val items: List<SearchHit>,
    val next: String?,
)

const val MATCH_START = "\u0001"
const val MATCH_END = "\u0002"
