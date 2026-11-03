package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

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

    override val primaryKey = PrimaryKey(id)
}

internal object Posts : Table("posts") {
    val id = long("id").autoIncrement()
    val threadId = long("thread_id").references(Threads.id)
    val authorId = long("author_id")
    val body = text("body")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

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

    override suspend fun threads(categorySlug: String): List<ThreadSummary>? {
        val rows =
            dbQuery {
                val categoryId = categoryId(categorySlug) ?: return@dbQuery null
                Threads
                    .selectAll()
                    .where { Threads.categoryId eq categoryId }
                    .orderBy(Threads.createdAt, SortOrder.DESC)
                    .toList()
            } ?: return null
        val names = users.usernames(rows.map { it[Threads.authorId] })
        return rows.map {
            ThreadSummary(
                it[Threads.id],
                it[Threads.title],
                names.nameOf(it[Threads.authorId]),
                it[Threads.createdAt].toString(),
            )
        }
    }

    override suspend fun thread(id: Long): ThreadDetail? = loadThread(id)

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
        return loadThread(threadId)
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
                Posts.selectAll().where { Posts.id eq postId }.single()
            } ?: return null
        return row.toPost(users.usernames(listOf(authorId)))
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

    private suspend fun loadThread(id: Long): ThreadDetail? {
        val (thread, posts) =
            dbQuery {
                val thread =
                    (Threads innerJoin Categories)
                        .selectAll()
                        .where { Threads.id eq id }
                        .singleOrNull() ?: return@dbQuery null
                thread to
                    Posts
                        .selectAll()
                        .where { Posts.threadId eq id }
                        .orderBy(Posts.createdAt)
                        .orderBy(Posts.id)
                        .toList()
            } ?: return null
        val names = users.usernames(posts.map { it[Posts.authorId] } + thread[Threads.authorId])
        return ThreadDetail(
            id = thread[Threads.id],
            category = thread[Categories.slug],
            title = thread[Threads.title],
            author = names.nameOf(thread[Threads.authorId]),
            createdAt = thread[Threads.createdAt].toString(),
            posts = posts.map { it.toPost(names) },
        )
    }

    private fun ResultRow.toPost(names: Map<Long, String>) =
        Post(this[Posts.id], names.nameOf(this[Posts.authorId]), this[Posts.body], this[Posts.createdAt].toString())

    private fun Map<Long, String>.nameOf(userId: Long) = this[userId] ?: DELETED_USER
}
