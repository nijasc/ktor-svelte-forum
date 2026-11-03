package dev.nilsschlegel.forum.notifications

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

internal class NotificationStore(
    private val users: UserDirectory,
) {
    suspend fun watch(
        userId: Long,
        threadId: Long,
    ) {
        dbQuery {
            Watches.insertIgnore {
                it[Watches.userId] = userId
                it[Watches.threadId] = threadId
            }
        }
    }

    suspend fun unwatch(
        userId: Long,
        threadId: Long,
    ) {
        dbQuery { Watches.deleteWhere { (Watches.userId eq userId) and (Watches.threadId eq threadId) } }
    }

    suspend fun isWatching(
        userId: Long,
        threadId: Long,
    ): Boolean = dbQuery { !Watches.selectAll().where { (Watches.userId eq userId) and (Watches.threadId eq threadId) }.empty() }

    suspend fun watchers(threadId: Long): Set<Long> =
        dbQuery {
            Watches
                .select(Watches.userId)
                .where { Watches.threadId eq threadId }
                .map { it[Watches.userId] }
                .toSet()
        }

    /**
     * Tells [userId] about something. A reply is folded into an unread reply notification for the same thread, so a
     * busy thread leaves one line with a count instead of a long list. Mentions are always separate.
     */
    suspend fun notify(
        userId: Long,
        kind: Kind,
        actorId: Long,
        threadId: Long,
        threadTitle: String,
        postId: Long,
    ) {
        dbQuery {
            val open =
                (Notifications.userId eq userId) and (Notifications.threadId eq threadId) and (Notifications.kind eq kind.column) and
                    Notifications.readAt.isNull()
            val folded =
                kind == Kind.REPLY &&
                    Notifications.update({ open }) {
                        it.update(count, count + 1)
                        it[Notifications.actorId] = actorId
                        it[Notifications.postId] = postId
                        it[createdAt] = CurrentTimestamp
                    } > 0
            if (!folded) {
                Notifications.insert {
                    it[Notifications.userId] = userId
                    it[Notifications.kind] = kind.column
                    it[Notifications.actorId] = actorId
                    it[Notifications.threadId] = threadId
                    it[Notifications.threadTitle] = threadTitle
                    it[Notifications.postId] = postId
                }
            }
        }
    }

    /** Newest first. [before] is the id of the last notification of the previous page. */
    suspend fun page(
        userId: Long,
        before: Long?,
        limit: Int,
    ): NotificationPage {
        val rows =
            dbQuery {
                val query = Notifications.selectAll().where { Notifications.userId eq userId }
                if (before != null) query.andWhere { Notifications.id less before }
                query.orderBy(Notifications.id, SortOrder.DESC).limit(limit + 1).toList()
            }
        val shown = rows.take(limit)
        val names = users.usernames(shown.map { it[Notifications.actorId] })
        val items =
            shown.map {
                NotificationView(
                    id = it[Notifications.id],
                    kind = Kind.fromColumn(it[Notifications.kind]),
                    actor = names[it[Notifications.actorId]] ?: "deleted",
                    threadId = it[Notifications.threadId],
                    threadTitle = it[Notifications.threadTitle],
                    postId = it[Notifications.postId],
                    count = it[Notifications.count],
                    read = it[Notifications.readAt] != null,
                    createdAt = it[Notifications.createdAt].toString(),
                )
            }
        return NotificationPage(items, if (rows.size > limit) shown.last()[Notifications.id].toString() else null, unread(userId))
    }

    suspend fun unread(userId: Long): Long =
        dbQuery { Notifications.selectAll().where { (Notifications.userId eq userId) and Notifications.readAt.isNull() }.count() }

    suspend fun markAllRead(userId: Long) {
        dbQuery {
            Notifications.update({ (Notifications.userId eq userId) and Notifications.readAt.isNull() }) {
                it[readAt] =
                    CurrentTimestamp
            }
        }
    }

    /** Returns false if the notification does not exist or belongs to someone else. */
    suspend fun markRead(
        userId: Long,
        id: Long,
    ): Boolean =
        dbQuery {
            val own = (Notifications.id eq id) and (Notifications.userId eq userId)
            if (Notifications.selectAll().where { own }.empty()) return@dbQuery false
            Notifications.update({ own and Notifications.readAt.isNull() }) { it[readAt] = CurrentTimestamp }
            true
        }
}
