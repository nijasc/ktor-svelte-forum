package dev.nilsschlegel.forum.forum

/** Which page of a list to return. [cursor] is the `next` value of the previous page, null for the first page. */
internal data class PageRequest(
    val cursor: String?,
    val limit: Int,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}

/** What the routes need from storage. [ExposedForumRepository] is the real one, tests use a fake. */
internal interface ForumRepository {
    suspend fun categories(): List<Category>

    /** Most recently active first, or null if there is no such category. */
    suspend fun threads(
        categorySlug: String,
        page: PageRequest,
    ): ThreadPage?

    /** The thread with one page of its posts, or null if there is no such thread. */
    suspend fun thread(
        id: Long,
        page: PageRequest,
    ): ThreadDetail?

    /** Creates the thread with [body] as its first post, or returns null if there is no such category. */
    suspend fun createThread(
        categorySlug: String,
        authorId: Long,
        title: String,
        body: String,
    ): ThreadDetail?

    /** Adds a post to the thread. */
    suspend fun reply(
        threadId: Long,
        authorId: Long,
        body: String,
    ): ReplyResult

    /** The user's recent threads and posts that are visible to everyone. */
    suspend fun activity(userId: Long): Activity

    /** Changes the text of the author's own post. */
    suspend fun editPost(
        postId: Long,
        userId: Long,
        body: String,
    ): PostChange

    /** Deletes the author's own post, except the first post of a thread. The text is kept but no longer shown. */
    suspend fun deletePost(
        postId: Long,
        userId: Long,
    ): PostChange
}

/** The outcome of replying to a thread. */
internal sealed interface ReplyResult {
    data class Done(
        val post: Post,
    ) : ReplyResult

    data object NotFound : ReplyResult

    data object Locked : ReplyResult
}

/** The outcome of editing or deleting a post. */
internal sealed interface PostChange {
    data class Done(
        val post: Post,
    ) : PostChange

    data object NotFound : PostChange

    data object NotYours : PostChange

    data object IsFirstPost : PostChange
}
