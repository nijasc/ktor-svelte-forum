package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * A record of what staff did. It is written after the change succeeds, in its own transaction, so a failure to write
 * it does not undo the action. Entries are never changed or deleted.
 */
internal class AuditLog(
    private val users: UserDirectory,
) {
    suspend fun record(
        actorId: Long,
        action: String,
        targetType: String,
        targetId: String,
        detail: String = "",
    ) {
        dbQuery {
            AuditLogs.insert {
                it[AuditLogs.actorId] = actorId
                it[AuditLogs.action] = action
                it[AuditLogs.targetType] = targetType
                it[AuditLogs.targetId] = targetId
                it[AuditLogs.detail] = detail
            }
        }
    }

    /** Newest first. [before] is the id of the last entry of the previous page. */
    suspend fun page(
        before: Long?,
        limit: Int,
    ): AuditPage {
        val rows =
            dbQuery {
                val query = AuditLogs.selectAll()
                if (before != null) query.andWhere { AuditLogs.id less before }
                query.orderBy(AuditLogs.id, SortOrder.DESC).limit(limit + 1).toList()
            }
        val shown = rows.take(limit)
        val names = users.usernames(shown.map { it[AuditLogs.actorId] })
        val items =
            shown.map {
                AuditEntry(
                    id = it[AuditLogs.id],
                    actor = names[it[AuditLogs.actorId]] ?: "deleted",
                    action = it[AuditLogs.action],
                    targetType = it[AuditLogs.targetType],
                    targetId = it[AuditLogs.targetId],
                    detail = it[AuditLogs.detail],
                    createdAt = it[AuditLogs.createdAt].toString(),
                )
            }
        return AuditPage(items, if (rows.size > limit) shown.last()[AuditLogs.id].toString() else null)
    }
}
