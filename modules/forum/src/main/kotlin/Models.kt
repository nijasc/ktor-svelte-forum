package dev.nilsschlegel.forum.forum

import io.github.nijasc.ktor.svelte.Validated
import io.github.nijasc.ktor.svelte.Validator
import kotlinx.serialization.Serializable

internal const val MAX_TITLE_LENGTH = 200
internal const val MAX_BODY_LENGTH = 20_000

@Serializable
data class Category(
    val slug: String,
    val title: String,
    val description: String,
)

@Serializable
data class ThreadSummary(
    val id: Long,
    val title: String,
    val author: String,
    val createdAt: String,
    val replyCount: Int,
    val lastPostAt: String,
    val pinned: Boolean = false,
    val locked: Boolean = false,
)

/**
 * One page of threads, most recently active first. Pass [next] as `cursor` to get the following page.
 * [pinned] holds the pinned threads and is only filled on the first page, [items] never repeats them.
 */
@Serializable
data class ThreadPage(
    val items: List<ThreadSummary>,
    val next: String?,
    val pinned: List<ThreadSummary> = emptyList(),
)

/** One page of posts, oldest first. Pass [next] as `cursor` to get the following page. */
@Serializable
data class PostPage(
    val items: List<Post>,
    val next: String?,
)

@Serializable
data class Post(
    val id: Long,
    val author: String,
    /** Empty when the post is [deleted] or [hidden]. */
    val body: String,
    val createdAt: String,
    val editedAt: String? = null,
    /** Deleted by its author. */
    val deleted: Boolean = false,
    /** Hidden by a moderator. */
    val hidden: Boolean = false,
)

@Serializable
data class ThreadDetail(
    val id: Long,
    val category: String,
    val title: String,
    val author: String,
    val createdAt: String,
    val replyCount: Int,
    val posts: PostPage,
    val pinned: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
data class NewThread(
    val title: String,
    val body: String,
) : Validated {
    override fun Validator.rules() {
        check("title", title.isNotBlank() && title.length <= MAX_TITLE_LENGTH) {
            "Use a title of up to $MAX_TITLE_LENGTH characters."
        }
        check("body", body.isNotBlank() && body.length <= MAX_BODY_LENGTH) {
            "Write a message of up to $MAX_BODY_LENGTH characters."
        }
    }
}

@Serializable
data class NewPost(
    val body: String,
) : Validated {
    override fun Validator.rules() {
        check("body", body.isNotBlank() && body.length <= MAX_BODY_LENGTH) {
            "Write a message of up to $MAX_BODY_LENGTH characters."
        }
    }
}

/** A user's recent public activity, for their profile. */
@Serializable
data class Activity(
    val threadCount: Long,
    val postCount: Long,
    val threads: List<ThreadSummary>,
    val posts: List<ActivityPost>,
)

@Serializable
data class ActivityPost(
    val id: Long,
    val threadId: Long,
    val threadTitle: String,
    /** The start of the post, cut at [EXCERPT_LENGTH] characters. */
    val excerpt: String,
    val createdAt: String,
)

internal const val EXCERPT_LENGTH = 200
