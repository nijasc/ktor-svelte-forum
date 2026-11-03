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
)

/** One page of threads, most recently active first. Pass [next] as `cursor` to get the following page. */
@Serializable
data class ThreadPage(
    val items: List<ThreadSummary>,
    val next: String?,
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
    /** Empty when the post is [deleted]. */
    val body: String,
    val createdAt: String,
    val editedAt: String? = null,
    val deleted: Boolean = false,
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
