package dev.nilsschlegel.forum.forum

import io.github.nijasc.ktor.svelte.Validated
import io.github.nijasc.ktor.svelte.Validator
import kotlinx.serialization.Serializable

private const val MAX_TITLE_LENGTH = 200
private const val MAX_BODY_LENGTH = 20_000

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
)

@Serializable
data class Post(
    val id: Long,
    val author: String,
    val body: String,
    val createdAt: String,
)

@Serializable
data class ThreadDetail(
    val id: Long,
    val category: String,
    val title: String,
    val author: String,
    val createdAt: String,
    val posts: List<Post>,
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
