package dev.nilsschlegel.forum.forum

/** What the routes need from storage. [ExposedForumRepository] is the real one, tests use a fake. */
internal interface ForumRepository {
    suspend fun categories(): List<Category>

    /** Newest first, or null if there is no such category. */
    suspend fun threads(categorySlug: String): List<ThreadSummary>?

    suspend fun thread(id: Long): ThreadDetail?

    /** Creates the thread with [body] as its first post, or returns null if there is no such category. */
    suspend fun createThread(
        categorySlug: String,
        author: String,
        title: String,
        body: String,
    ): ThreadDetail?

    /** Adds a post to the thread, or returns null if there is no such thread. */
    suspend fun reply(
        threadId: Long,
        author: String,
        body: String,
    ): Post?
}
