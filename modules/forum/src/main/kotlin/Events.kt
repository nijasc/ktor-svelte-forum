package dev.nilsschlegel.forum.forum

/** A thread was started. Published on the [dev.nilsschlegel.forum.core.EventBus] once it is saved. */
data class ThreadCreated(
    val threadId: Long,
    val title: String,
    val authorId: Long,
    val body: String,
)

/** A reply was added to a thread. [threadAuthorId] is who started the thread. */
data class PostCreated(
    val threadId: Long,
    val threadTitle: String,
    val postId: Long,
    val authorId: Long,
    val threadAuthorId: Long,
    val body: String,
)
