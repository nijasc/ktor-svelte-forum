package dev.nilsschlegel.forum.forum

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

private const val MAX_REACTION_COLUMN = 16
private const val MAX_SLUG_COLUMN = 64
private const val MAX_TITLE_COLUMN = 200

internal object Categories : Table("categories") {
    val id = long("id").autoIncrement()
    val slug = varchar("slug", MAX_SLUG_COLUMN)
    val title = varchar("title", MAX_TITLE_COLUMN)
    val description = varchar("description", 500)
    val position = integer("position")
    val archivedAt = timestamp("archived_at").nullable()

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
    val pinned = bool("pinned").default(false)
    val locked = bool("locked").default(false)
    val hiddenAt = timestamp("hidden_at").nullable()

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
    val hiddenAt = timestamp("hidden_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

internal object Reactions : Table("reactions") {
    val postId = long("post_id")
    val userId = long("user_id")
    val kind = varchar("kind", MAX_REACTION_COLUMN)

    override val primaryKey = PrimaryKey(postId, userId)
}
