package dev.nilsschlegel.forum.forum

import kotlinx.serialization.Serializable

/**
 * What moderators and admins can do to the forum's content. The caller checks the role and writes the audit log,
 * this only changes data. Every method answers whether the thread, post or category existed.
 */
interface ForumStaff {
    suspend fun setThreadPinned(
        threadId: Long,
        pinned: Boolean,
    ): Boolean

    suspend fun setThreadLocked(
        threadId: Long,
        locked: Boolean,
    ): Boolean

    suspend fun setThreadHidden(
        threadId: Long,
        hidden: Boolean,
    ): Boolean

    suspend fun moveThread(
        threadId: Long,
        categorySlug: String,
    ): MoveResult

    /** Hides or restores a post. The first post of a thread stays, hide the thread instead. */
    suspend fun setPostHidden(
        postId: Long,
        hidden: Boolean,
    ): HideResult

    /** A post with its text, even when it is hidden or deleted. */
    suspend fun post(id: Long): StaffPost?

    suspend fun posts(ids: Collection<Long>): Map<Long, StaffPost>

    /** Every category, archived ones too, in display order. */
    suspend fun categories(): List<StaffCategory>

    /** Null if the slug is taken. */
    suspend fun createCategory(
        slug: String,
        title: String,
        description: String,
    ): StaffCategory?

    /** Changes only what is given. Null if there is no such category. */
    suspend fun updateCategory(
        slug: String,
        title: String?,
        description: String?,
        archived: Boolean?,
    ): StaffCategory?

    /** Puts the categories in the order given, which must name every category once. */
    suspend fun reorderCategories(slugs: List<String>): Boolean
}

enum class MoveResult { MOVED, NO_THREAD, NO_CATEGORY }

sealed interface HideResult {
    data class Done(
        val post: StaffPost,
    ) : HideResult

    data object NotFound : HideResult

    data object IsFirstPost : HideResult
}

data class StaffPost(
    val id: Long,
    val threadId: Long,
    val threadTitle: String,
    val authorId: Long,
    val author: String,
    val body: String,
    val createdAt: String,
    val hidden: Boolean,
    val deleted: Boolean,
)

@Serializable
data class StaffCategory(
    val slug: String,
    val title: String,
    val description: String,
    val position: Int,
    val archived: Boolean,
)
