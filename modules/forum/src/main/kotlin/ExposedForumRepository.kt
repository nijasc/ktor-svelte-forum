package dev.nilsschlegel.forum.forum

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
private const val MAX_AUTHOR_COLUMN = 64

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
    val author = varchar("author", MAX_AUTHOR_COLUMN)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

internal object Posts : Table("posts") {
    val id = long("id").autoIncrement()
    val threadId = long("thread_id").references(Threads.id)
    val author = varchar("author", MAX_AUTHOR_COLUMN)
    val body = text("body")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

internal class ExposedForumRepository : ForumRepository {
    override suspend fun categories(): List<Category> =
        dbQuery {
            Categories
                .selectAll()
                .orderBy(Categories.position)
                .map { Category(it[Categories.slug], it[Categories.title], it[Categories.description]) }
        }

    override suspend fun threads(categorySlug: String): List<ThreadSummary>? =
        dbQuery {
            val categoryId = categoryId(categorySlug) ?: return@dbQuery null
            Threads
                .selectAll()
                .where { Threads.categoryId eq categoryId }
                .orderBy(Threads.createdAt, SortOrder.DESC)
                .map {
                    ThreadSummary(it[Threads.id], it[Threads.title], it[Threads.author], it[Threads.createdAt].toString())
                }
        }

    override suspend fun thread(id: Long): ThreadDetail? = dbQuery { loadThread(id) }

    override suspend fun createThread(
        categorySlug: String,
        author: String,
        title: String,
        body: String,
    ): ThreadDetail? =
        dbQuery {
            val categoryId = categoryId(categorySlug) ?: return@dbQuery null
            val threadId =
                Threads.insert {
                    it[Threads.categoryId] = categoryId
                    it[Threads.title] = title
                    it[Threads.author] = author
                }[Threads.id]
            insertPost(threadId, author, body)
            loadThread(threadId)
        }

    override suspend fun reply(
        threadId: Long,
        author: String,
        body: String,
    ): Post? =
        dbQuery {
            if (Threads.selectAll().where { Threads.id eq threadId }.empty()) return@dbQuery null
            val postId = insertPost(threadId, author, body)
            Posts
                .selectAll()
                .where { Posts.id eq postId }
                .single()
                .toPost()
        }

    private fun categoryId(slug: String): Long? =
        Categories
            .selectAll()
            .where { Categories.slug eq slug }
            .singleOrNull()
            ?.get(Categories.id)

    private fun insertPost(
        threadId: Long,
        author: String,
        body: String,
    ): Long =
        Posts.insert {
            it[Posts.threadId] = threadId
            it[Posts.author] = author
            it[Posts.body] = body
        }[Posts.id]

    private fun loadThread(id: Long): ThreadDetail? {
        val thread =
            (Threads innerJoin Categories)
                .selectAll()
                .where { Threads.id eq id }
                .singleOrNull() ?: return null
        val posts =
            Posts
                .selectAll()
                .where { Posts.threadId eq id }
                .orderBy(Posts.createdAt)
                .orderBy(Posts.id)
                .map { it.toPost() }
        return ThreadDetail(
            id = thread[Threads.id],
            category = thread[Categories.slug],
            title = thread[Threads.title],
            author = thread[Threads.author],
            createdAt = thread[Threads.createdAt].toString(),
            posts = posts,
        )
    }

    private fun ResultRow.toPost() = Post(this[Posts.id], this[Posts.author], this[Posts.body], this[Posts.createdAt].toString())
}
