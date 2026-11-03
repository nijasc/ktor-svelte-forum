package dev.nilsschlegel.forum.notifications

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

private const val MAX_KIND_COLUMN = 16
private const val MAX_TITLE_COLUMN = 200

internal object Watches : Table("watches") {
    val userId = long("user_id")
    val threadId = long("thread_id")

    override val primaryKey = PrimaryKey(userId, threadId)
}

internal object Notifications : Table("notifications") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val kind = varchar("kind", MAX_KIND_COLUMN)
    val actorId = long("actor_id")
    val threadId = long("thread_id")
    val threadTitle = varchar("thread_title", MAX_TITLE_COLUMN)
    val postId = long("post_id")
    val count = integer("count").default(1)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val readAt = timestamp("read_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
