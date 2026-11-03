package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.core.EventBus
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

private const val DELETED_USER = "deleted"
private const val ACTIVITY_SIZE = 10

internal class ExposedForumRepository(
    private val users: UserDirectory,
    private val events: EventBus = EventBus(),
) : ForumRepository {
    override suspend fun categories(): List<Category> =
        dbQuery {
            Categories
                .selectAll()
                .where { Categories.archivedAt.isNull() }
                .orderBy(Categories.position)
                .map { Category(it[Categories.slug], it[Categories.title], it[Categories.description]) }
        }

    override suspend fun threads(
        categorySlug: String,
        page: PageRequest,
    ): ThreadPage? {
        val cursor = page.cursor?.let(ThreadCursor::decode)
        val rows =
            dbQuery {
                val categoryId = categoryId(categorySlug) ?: return@dbQuery null
                val visible = (Threads.categoryId eq categoryId) and Threads.hiddenAt.isNull()
                val query = Threads.selectAll().where { visible and (Threads.pinned eq false) }
                if (cursor != null) {
                    query.andWhere {
                        (Threads.lastPostAt less cursor.lastPostAt) or
                            ((Threads.lastPostAt eq cursor.lastPostAt) and (Threads.id less cursor.id))
                    }
                }
                val pinned =
                    if (cursor != null) {
                        emptyList()
                    } else {
                        Threads
                            .selectAll()
                            .where { visible and (Threads.pinned eq true) }
                            .orderBy(Threads.lastPostAt, SortOrder.DESC)
                            .orderBy(Threads.id, SortOrder.DESC)
                            .toList()
                    }
                val regular =
                    query
                        .orderBy(Threads.lastPostAt, SortOrder.DESC)
                        .orderBy(Threads.id, SortOrder.DESC)
                        .limit(page.limit + 1)
                        .toList()
                pinned to regular
            } ?: return null
        val (pinnedRows, regularRows) = rows
        val shown = regularRows.take(page.limit)
        val names = users.usernames((shown + pinnedRows).map { it[Threads.authorId] })
        val items = shown.map { it.toSummary(names) }
        val pinned = pinnedRows.map { it.toSummary(names) }
        val next =
            if (regularRows.size >
                page.limit
            ) {
                ThreadCursor(shown.last()[Threads.lastPostAt], shown.last()[Threads.id]).encode()
            } else {
                null
            }
        return ThreadPage(items, next, pinned)
    }

    override suspend fun thread(
        id: Long,
        page: PageRequest,
        viewerId: Long?,
    ): ThreadDetail? = loadThread(id, page, viewerId)

    override suspend fun createThread(
        categorySlug: String,
        authorId: Long,
        title: String,
        body: String,
    ): ThreadDetail? {
        val threadId =
            dbQuery {
                val categoryId = categoryId(categorySlug, archivedToo = false) ?: return@dbQuery null
                val id =
                    Threads.insert {
                        it[Threads.categoryId] = categoryId
                        it[Threads.title] = title
                        it[Threads.authorId] = authorId
                    }[Threads.id]
                insertPost(id, authorId, body)
                id
            } ?: return null
        val created = loadThread(threadId, PageRequest(null, PageRequest.DEFAULT_LIMIT)) ?: return null
        events.publish(ThreadCreated(created.id, created.title, authorId, body))
        return created
    }

    override suspend fun reply(
        threadId: Long,
        authorId: Long,
        body: String,
    ): ReplyResult {
        val row =
            dbQuery {
                val thread =
                    Threads.selectAll().where { (Threads.id eq threadId) and Threads.hiddenAt.isNull() }.singleOrNull()
                        ?: return@dbQuery ReplyResult.NotFound to null
                if (thread[Threads.locked]) return@dbQuery ReplyResult.Locked to null
                val postId = insertPost(threadId, authorId, body)
                Threads.update({ Threads.id eq threadId }) {
                    it.update(replyCount, replyCount + 1)
                    it[lastPostAt] = CurrentTimestamp
                }
                null to (Posts.selectAll().where { Posts.id eq postId }.single() to thread)
            }
        val (failure, created) = row
        if (failure != null) return failure
        val (postRow, threadRow) = created!!
        val post = postRow.toPost(users.usernames(listOf(authorId)))
        events.publish(PostCreated(threadId, threadRow[Threads.title], post.id, authorId, threadRow[Threads.authorId], body))
        return ReplyResult.Done(post)
    }

    override suspend fun activity(userId: Long): Activity {
        val data =
            dbQuery {
                val visibleThreads = (Threads.authorId eq userId) and Threads.hiddenAt.isNull()
                val visiblePosts = (Posts.authorId eq userId) and Posts.deletedAt.isNull() and Posts.hiddenAt.isNull()
                val threads =
                    Threads
                        .selectAll()
                        .where { visibleThreads }
                        .orderBy(Threads.createdAt, SortOrder.DESC)
                        .orderBy(Threads.id, SortOrder.DESC)
                        .limit(ACTIVITY_SIZE)
                        .toList()
                val posts =
                    (Posts innerJoin Threads)
                        .selectAll()
                        .where { visiblePosts and Threads.hiddenAt.isNull() }
                        .orderBy(Posts.id, SortOrder.DESC)
                        .limit(ACTIVITY_SIZE)
                        .toList()
                Triple(
                    threads,
                    posts,
                    Threads.selectAll().where { visibleThreads }.count() to Posts.selectAll().where { visiblePosts }.count(),
                )
            }
        val (threads, posts, counts) = data
        val names = users.usernames(listOf(userId))
        return Activity(
            threadCount = counts.first,
            postCount = counts.second,
            threads = threads.map { it.toSummary(names) },
            posts =
                posts.map {
                    ActivityPost(
                        id = it[Posts.id],
                        threadId = it[Posts.threadId],
                        threadTitle = it[Threads.title],
                        excerpt = it[Posts.body].take(EXCERPT_LENGTH),
                        createdAt = it[Posts.createdAt].toString(),
                    )
                },
        )
    }

    override suspend fun editPost(
        postId: Long,
        userId: Long,
        body: String,
    ): PostChange =
        change(postId, userId, allowFirst = true) {
            Posts.update({ Posts.id eq postId }) {
                it[Posts.body] = body
                it[editedAt] = CurrentTimestamp
            }
        }

    override suspend fun deletePost(
        postId: Long,
        userId: Long,
    ): PostChange =
        change(postId, userId, allowFirst = false) { post ->
            Posts.update({ Posts.id eq postId }) { it[deletedAt] = CurrentTimestamp }
            Threads.update({ Threads.id eq post[Posts.threadId] }) { it.update(replyCount, replyCount - 1) }
        }

    /** Checks the post exists, is live and is the user's, runs [apply], and returns the post as it is afterwards. */
    private suspend fun change(
        postId: Long,
        userId: Long,
        allowFirst: Boolean,
        apply: (ResultRow) -> Unit,
    ): PostChange {
        val (failure, row) =
            dbQuery {
                val post =
                    Posts.selectAll().where { (Posts.id eq postId) and Posts.deletedAt.isNull() }.singleOrNull()
                        ?: return@dbQuery PostChange.NotFound to null
                if (post[Posts.authorId] != userId) return@dbQuery PostChange.NotYours to null
                val firstId =
                    Posts
                        .select(
                            Posts.id,
                        ).where { Posts.threadId eq post[Posts.threadId] }
                        .orderBy(Posts.id)
                        .limit(1)
                        .single()[Posts.id]
                if (!allowFirst && firstId == postId) return@dbQuery PostChange.IsFirstPost to null
                apply(post)
                null to Posts.selectAll().where { Posts.id eq postId }.single()
            }
        return failure ?: PostChange.Done(row!!.toPost(users.usernames(listOf(userId))))
    }

    /** Archived categories keep their threads readable but take no new ones. */
    private fun categoryId(
        slug: String,
        archivedToo: Boolean = true,
    ): Long? =
        Categories
            .selectAll()
            .where { (Categories.slug eq slug) and (if (archivedToo) Op.TRUE else Categories.archivedAt.isNull()) }
            .singleOrNull()
            ?.get(Categories.id)

    private fun insertPost(
        threadId: Long,
        authorId: Long,
        body: String,
    ): Long =
        Posts.insert {
            it[Posts.threadId] = threadId
            it[Posts.authorId] = authorId
            it[Posts.body] = body
        }[Posts.id]

    private suspend fun loadThread(
        id: Long,
        page: PageRequest,
        viewerId: Long? = null,
    ): ThreadDetail? {
        val after = page.cursor?.let(::decodePostCursor)
        val found =
            dbQuery {
                val thread =
                    (Threads innerJoin Categories)
                        .selectAll()
                        .where { (Threads.id eq id) and Threads.hiddenAt.isNull() }
                        .singleOrNull() ?: return@dbQuery null
                val query = Posts.selectAll().where { Posts.threadId eq id }
                if (after != null) query.andWhere { Posts.id greater after }
                val posts = query.orderBy(Posts.id).limit(page.limit + 1).toList()
                val shownIds = posts.take(page.limit).map { it[Posts.id] }
                Triple(thread, posts, reactionsOf(shownIds, viewerId))
            } ?: return null
        val (thread, rows, tallies) = found
        val shown = rows.take(page.limit)
        val names = users.usernames(shown.map { it[Posts.authorId] } + thread[Threads.authorId])
        return ThreadDetail(
            id = thread[Threads.id],
            category = thread[Categories.slug],
            title = thread[Threads.title],
            author = names.nameOf(thread[Threads.authorId]),
            createdAt = thread[Threads.createdAt].toString(),
            replyCount = thread[Threads.replyCount],
            pinned = thread[Threads.pinned],
            locked = thread[Threads.locked],
            posts =
                PostPage(
                    shown.map { it.toPost(names, tallies) },
                    if (rows.size >
                        page.limit
                    ) {
                        shown.last()[Posts.id].toString()
                    } else {
                        null
                    },
                ),
        )
    }

    private fun ResultRow.toPost(
        names: Map<Long, String>,
        tallies: Tallies = Tallies(),
    ): Post {
        val visible = this[Posts.deletedAt] == null && this[Posts.hiddenAt] == null
        return Post(
            id = this[Posts.id],
            author = names.nameOf(this[Posts.authorId]),
            body = if (visible) this[Posts.body] else "",
            createdAt = this[Posts.createdAt].toString(),
            editedAt = this[Posts.editedAt]?.toString(),
            deleted = this[Posts.deletedAt] != null,
            hidden = this[Posts.hiddenAt] != null,
            reactions = if (visible) tallies.counts[this[Posts.id]].orEmpty() else emptyMap(),
            myReaction = if (visible) tallies.mine[this[Posts.id]] else null,
        )
    }

    /** What people chose for some posts, and what [viewerId] chose. Run inside a transaction. */
    private fun reactionsOf(
        postIds: List<Long>,
        viewerId: Long?,
    ): Tallies {
        if (postIds.isEmpty()) return Tallies()
        val chosen = Reactions.userId.count()
        val counts =
            Reactions
                .select(Reactions.postId, Reactions.kind, chosen)
                .where { Reactions.postId inList postIds }
                .groupBy(Reactions.postId, Reactions.kind)
                .groupBy({ it[Reactions.postId] }, { it[Reactions.kind] to it[chosen].toInt() })
                .mapValues { it.value.toMap() }
        val mine =
            if (viewerId == null) {
                emptyMap()
            } else {
                Reactions
                    .selectAll()
                    .where { (Reactions.postId inList postIds) and (Reactions.userId eq viewerId) }
                    .associate { it[Reactions.postId] to it[Reactions.kind] }
            }
        return Tallies(counts, mine)
    }

    private class Tallies(
        val counts: Map<Long, Map<String, Int>> = emptyMap(),
        val mine: Map<Long, String> = emptyMap(),
    )

    override suspend fun react(
        postId: Long,
        userId: Long,
        kind: Reaction,
    ): PostReactions? =
        dbQuery {
            if (!reactable(postId)) return@dbQuery null
            Reactions.upsert {
                it[Reactions.postId] = postId
                it[Reactions.userId] = userId
                it[Reactions.kind] = kind.column
            }
            summary(postId, userId)
        }

    override suspend fun unreact(
        postId: Long,
        userId: Long,
    ): PostReactions? =
        dbQuery {
            if (!reactable(postId)) return@dbQuery null
            Reactions.deleteWhere { (Reactions.postId eq postId) and (Reactions.userId eq userId) }
            summary(postId, userId)
        }

    /** Reactions go on posts people can see: not deleted, not hidden, and in a thread that is not hidden. */
    private fun reactable(postId: Long): Boolean =
        !(Posts innerJoin Threads)
            .selectAll()
            .where { (Posts.id eq postId) and Posts.deletedAt.isNull() and Posts.hiddenAt.isNull() and Threads.hiddenAt.isNull() }
            .empty()

    private fun summary(
        postId: Long,
        userId: Long,
    ): PostReactions {
        val tallies = reactionsOf(listOf(postId), userId)
        return PostReactions(tallies.counts[postId].orEmpty(), tallies.mine[postId])
    }

    private fun ResultRow.toSummary(names: Map<Long, String>) =
        ThreadSummary(
            id = this[Threads.id],
            title = this[Threads.title],
            author = names.nameOf(this[Threads.authorId]),
            createdAt = this[Threads.createdAt].toString(),
            replyCount = this[Threads.replyCount],
            lastPostAt = this[Threads.lastPostAt].toString(),
            pinned = this[Threads.pinned],
            locked = this[Threads.locked],
        )

    private fun Map<Long, String>.nameOf(userId: Long) = this[userId] ?: DELETED_USER
}
