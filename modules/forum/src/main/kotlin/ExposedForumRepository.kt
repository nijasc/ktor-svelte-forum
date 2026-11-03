package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

private const val MAX_SLUG_COLUMN = 64
private const val MAX_TITLE_COLUMN = 200
private const val DELETED_USER = "deleted"

internal object Categories : Table("categories") {
    val id = long("id").autoIncrement()
    val slug = varchar("slug", MAX_SLUG_COLUMN)
    val title = varchar("title", MAX_TITLE_COLUMN)
    val description = varchar("description", 500)
    val position = integer("position")

    override val primaryKey = PrimaryKey(id)
}

internal object Threads : Table("threads") {
    val id = long("id").autoIncrement()
    val categoryId = long("category_id").references(Categories.id)
    val title = varchar("title", MAX_TITLE_COLUMN)
    val authorId = long("author_id")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val replyCount = integer("reply_count").default(0)
    val lastPostAt = timestamp("last_post_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

internal object Posts : Table("posts") {
    val id = long("id").autoIncrement()
    val threadId = long("thread_id").references(Threads.id)
    val authorId = long("author_id")
    val body = text("body")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val editedAt = timestamp("edited_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal class ExposedForumRepository(
    private val users: UserDirectory,
) : ForumRepository {
    override suspend fun categories(): List<Category> =
        dbQuery {
            Categories
                .selectAll()
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
                val query = Threads.selectAll().where { Threads.categoryId eq categoryId }
                if (cursor != null) {
                    query.andWhere {
                        (Threads.lastPostAt less cursor.lastPostAt) or
                            ((Threads.lastPostAt eq cursor.lastPostAt) and (Threads.id less cursor.id))
                    }
                }
                query
                    .orderBy(Threads.lastPostAt, SortOrder.DESC)
                    .orderBy(Threads.id, SortOrder.DESC)
                    .limit(page.limit + 1)
                    .toList()
            } ?: return null
        val shown = rows.take(page.limit)
        val names = users.usernames(shown.map { it[Threads.authorId] })
        val items =
            shown.map {
                ThreadSummary(
                    id = it[Threads.id],
                    title = it[Threads.title],
                    author = names.nameOf(it[Threads.authorId]),
                    createdAt = it[Threads.createdAt].toString(),
                    replyCount = it[Threads.replyCount],
                    lastPostAt = it[Threads.lastPostAt].toString(),
                )
            }
        val next = if (rows.size > page.limit) ThreadCursor(shown.last()[Threads.lastPostAt], shown.last()[Threads.id]).encode() else null
        return ThreadPage(items, next)
    }

    override suspend fun thread(
        id: Long,
        page: PageRequest,
    ): ThreadDetail? = loadThread(id, page)

    override suspend fun createThread(
        categorySlug: String,
        authorId: Long,
        title: String,
        body: String,
    ): ThreadDetail? {
        val threadId =
            dbQuery {
                val categoryId = categoryId(categorySlug) ?: return@dbQuery null
                val id =
                    Threads.insert {
                        it[Threads.categoryId] = categoryId
                        it[Threads.title] = title
                        it[Threads.authorId] = authorId
                    }[Threads.id]
                insertPost(id, authorId, body)
                id
            } ?: return null
        return loadThread(threadId, PageRequest(null, PageRequest.DEFAULT_LIMIT))
    }

    override suspend fun reply(
        threadId: Long,
        authorId: Long,
        body: String,
    ): Post? {
        val row =
            dbQuery {
                if (Threads.selectAll().where { Threads.id eq threadId }.empty()) return@dbQuery null
                val postId = insertPost(threadId, authorId, body)
                Threads.update({ Threads.id eq threadId }) {
                    it.update(replyCount, replyCount + 1)
                    it[lastPostAt] = CurrentTimestamp
                }
                Posts.selectAll().where { Posts.id eq postId }.single()
            } ?: return null
        return row.toPost(users.usernames(listOf(authorId)))
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

    private fun categoryId(slug: String): Long? =
        Categories
            .selectAll()
            .where { Categories.slug eq slug }
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
    ): ThreadDetail? {
        val after = page.cursor?.let(::decodePostCursor)
        val (thread, rows) =
            dbQuery {
                val thread =
                    (Threads innerJoin Categories)
                        .selectAll()
                        .where { Threads.id eq id }
                        .singleOrNull() ?: return@dbQuery null
                val query = Posts.selectAll().where { Posts.threadId eq id }
                if (after != null) query.andWhere { Posts.id greater after }
                thread to query.orderBy(Posts.id).limit(page.limit + 1).toList()
            } ?: return null
        val shown = rows.take(page.limit)
        val names = users.usernames(shown.map { it[Posts.authorId] } + thread[Threads.authorId])
        return ThreadDetail(
            id = thread[Threads.id],
            category = thread[Categories.slug],
            title = thread[Threads.title],
            author = names.nameOf(thread[Threads.authorId]),
            createdAt = thread[Threads.createdAt].toString(),
            replyCount = thread[Threads.replyCount],
            posts = PostPage(shown.map { it.toPost(names) }, if (rows.size > page.limit) shown.last()[Posts.id].toString() else null),
        )
    }

    private fun ResultRow.toPost(names: Map<Long, String>) =
        Post(
            id = this[Posts.id],
            author = names.nameOf(this[Posts.authorId]),
            body = if (this[Posts.deletedAt] == null) this[Posts.body] else "",
            createdAt = this[Posts.createdAt].toString(),
            editedAt = this[Posts.editedAt]?.toString(),
            deleted = this[Posts.deletedAt] != null,
        )

    private fun Map<Long, String>.nameOf(userId: Long) = this[userId] ?: DELETED_USER
}
