package dev.nilsschlegel.forum.moderation

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

private const val MAX_ACTION_COLUMN = 48
private const val MAX_TARGET_TYPE_COLUMN = 16
private const val MAX_TARGET_ID_COLUMN = 64
private const val MAX_REASON_COLUMN = 500

internal object AuditLogs : Table("audit_log") {
    val id = long("id").autoIncrement()
    val actorId = long("actor_id")
    val action = varchar("action", MAX_ACTION_COLUMN)
    val targetType = varchar("target_type", MAX_TARGET_TYPE_COLUMN)
    val targetId = varchar("target_id", MAX_TARGET_ID_COLUMN)
    val detail = text("detail")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

internal object Reports : Table("reports") {
    val id = long("id").autoIncrement()
    val postId = long("post_id")
    val reporterId = long("reporter_id")
    val reason = varchar("reason", MAX_REASON_COLUMN)
    val status = varchar("status", MAX_TARGET_TYPE_COLUMN)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val resolvedBy = long("resolved_by").nullable()
    val resolvedAt = timestamp("resolved_at").nullable()
    val outcome = varchar("outcome", MAX_TARGET_TYPE_COLUMN).nullable()
    val note = text("note")

    override val primaryKey = PrimaryKey(id)
}
