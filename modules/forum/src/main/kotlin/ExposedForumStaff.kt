package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal class ExposedForumStaff(
    private val users: UserDirectory,
) : ForumStaff {
    override suspend fun setThreadPinned(
        threadId: Long,
        pinned: Boolean,
    ): Boolean = dbQuery { Threads.update({ Threads.id eq threadId }) { it[Threads.pinned] = pinned } > 0 }

    override suspend fun setThreadLocked(
        threadId: Long,
        locked: Boolean,
    ): Boolean = dbQuery { Threads.update({ Threads.id eq threadId }) { it[Threads.locked] = locked } > 0 }

    override suspend fun setThreadHidden(
        threadId: Long,
        hidden: Boolean,
    ): Boolean =
        dbQuery {
            Threads.update({ Threads.id eq threadId }) {
                if (hidden) it[hiddenAt] = CurrentTimestamp else it[hiddenAt] = null
            } > 0
        }

    override suspend fun moveThread(
        threadId: Long,
        categorySlug: String,
    ): MoveResult =
        dbQuery {
            if (Threads.selectAll().where { Threads.id eq threadId }.empty()) return@dbQuery MoveResult.NO_THREAD
            val category =
                Categories.select(Categories.id).where { Categories.slug eq categorySlug }.singleOrNull()
                    ?: return@dbQuery MoveResult.NO_CATEGORY
            Threads.update({ Threads.id eq threadId }) { it[categoryId] = category[Categories.id] }
            MoveResult.MOVED
        }

    override suspend fun setPostHidden(
        postId: Long,
        hidden: Boolean,
    ): HideResult {
        val failure =
            dbQuery {
                val post = Posts.selectAll().where { Posts.id eq postId }.singleOrNull() ?: return@dbQuery HideResult.NotFound
                val threadId = post[Posts.threadId]
                val firstId =
                    Posts
                        .select(Posts.id)
                        .where { Posts.threadId eq threadId }
                        .orderBy(Posts.id)
                        .limit(1)
                        .single()[Posts.id]
                if (firstId == postId) return@dbQuery HideResult.IsFirstPost
                val alreadyHidden = post[Posts.hiddenAt] != null
                if (alreadyHidden != hidden) {
                    Posts.update({ Posts.id eq postId }) { if (hidden) it[hiddenAt] = CurrentTimestamp else it[hiddenAt] = null }
                    // A post its author deleted was already taken off the count.
                    if (post[Posts.deletedAt] == null) {
                        Threads.update({ Threads.id eq threadId }) {
                            if (hidden) it.update(replyCount, replyCount - 1) else it.update(replyCount, replyCount + 1)
                        }
                    }
                }
                null
            }
        return failure ?: HideResult.Done(checkNotNull(post(postId)))
    }

    override suspend fun post(id: Long): StaffPost? = posts(listOf(id))[id]

    override suspend fun posts(ids: Collection<Long>): Map<Long, StaffPost> {
        if (ids.isEmpty()) return emptyMap()
        val rows =
            dbQuery {
                (Posts innerJoin Threads).selectAll().where { Posts.id inList ids.toSet() }.toList()
            }
        val names = users.usernames(rows.map { it[Posts.authorId] })
        return rows.associate { it[Posts.id] to it.toStaffPost(names) }
    }

    override suspend fun categories(): List<StaffCategory> =
        dbQuery {
            Categories.selectAll().orderBy(Categories.position).map { it.toStaffCategory() }
        }

    override suspend fun createCategory(
        slug: String,
        title: String,
        description: String,
    ): StaffCategory? =
        dbQuery {
            val next = (Categories.select(Categories.position).maxOfOrNull { it[Categories.position] } ?: -1) + 1
            val inserted =
                Categories.insertIgnore {
                    it[Categories.slug] = slug
                    it[Categories.title] = title
                    it[Categories.description] = description
                    it[position] = next
                }
            if (inserted.insertedCount == 0) null else bySlug(slug)
        }

    override suspend fun updateCategory(
        slug: String,
        title: String?,
        description: String?,
        archived: Boolean?,
    ): StaffCategory? =
        dbQuery {
            bySlug(slug) ?: return@dbQuery null
            Categories.update({ Categories.slug eq slug }) {
                if (title != null) it[Categories.title] = title
                if (description != null) it[Categories.description] = description
                when (archived) {
                    true -> it[archivedAt] = CurrentTimestamp
                    false -> it[archivedAt] = null
                    null -> Unit
                }
            }
            bySlug(slug)
        }

    override suspend fun reorderCategories(slugs: List<String>): Boolean =
        dbQuery {
            val existing = Categories.select(Categories.slug).map { it[Categories.slug] }
            if (slugs.size != existing.size || slugs.toSet() != existing.toSet()) return@dbQuery false
            slugs.forEachIndexed { index, slug -> Categories.update({ Categories.slug eq slug }) { it[position] = index } }
            true
        }

    private fun bySlug(slug: String): StaffCategory? =
        Categories
            .selectAll()
            .where { Categories.slug eq slug }
            .singleOrNull()
            ?.toStaffCategory()

    private fun ResultRow.toStaffCategory() =
        StaffCategory(
            this[Categories.slug],
            this[Categories.title],
            this[Categories.description],
            this[Categories.position],
            this[Categories.archivedAt] != null,
        )

    private fun ResultRow.toStaffPost(names: Map<Long, String>) =
        StaffPost(
            id = this[Posts.id],
            threadId = this[Posts.threadId],
            threadTitle = this[Threads.title],
            authorId = this[Posts.authorId],
            author = names[this[Posts.authorId]] ?: "deleted",
            body = this[Posts.body],
            createdAt = this[Posts.createdAt].toString(),
            hidden = this[Posts.hiddenAt] != null,
            deleted = this[Posts.deletedAt] != null,
        )
}
