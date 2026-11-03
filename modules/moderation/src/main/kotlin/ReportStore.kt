package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import dev.nilsschlegel.forum.forum.ForumStaff
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

private const val OPEN = "open"
private const val RESOLVED = "resolved"

internal class ReportStore(
    private val users: UserDirectory,
    private val forum: ForumStaff,
) {
    /** Returns false if this user has already reported this post. */
    suspend fun file(
        postId: Long,
        reporterId: Long,
        reason: String,
    ): Boolean =
        dbQuery {
            Reports
                .insertIgnore {
                    it[Reports.postId] = postId
                    it[Reports.reporterId] = reporterId
                    it[Reports.reason] = reason
                    it[status] = OPEN
                    it[note] = ""
                }.insertedCount > 0
        }

    /** Open reports, oldest first, so none waits forever. [after] is the id of the last report of the previous page. */
    suspend fun open(
        after: Long?,
        limit: Int,
    ): ReportPage {
        val rows =
            dbQuery {
                val query = Reports.selectAll().where { Reports.status eq OPEN }
                if (after != null) query.andWhere { Reports.id greater after }
                query.orderBy(Reports.id).limit(limit + 1).toList()
            }
        val shown = rows.take(limit)
        val posts = forum.posts(shown.map { it[Reports.postId] })
        val names = users.usernames(shown.map { it[Reports.reporterId] })
        val items =
            shown.mapNotNull { row ->
                val post = posts[row[Reports.postId]] ?: return@mapNotNull null
                ReportView(
                    id = row[Reports.id],
                    post = ReportedPost(post.id, post.threadId, post.threadTitle, post.author, post.body, post.hidden || post.deleted),
                    reporter = names[row[Reports.reporterId]] ?: "deleted",
                    reason = row[Reports.reason],
                    createdAt = row[Reports.createdAt].toString(),
                )
            }
        return ReportPage(items, if (rows.size > limit) shown.last()[Reports.id].toString() else null)
    }

    /** Returns false if there is no such open report. */
    suspend fun resolve(
        id: Long,
        moderatorId: Long,
        outcome: ReportOutcome,
        note: String,
    ): Boolean =
        dbQuery {
            Reports.update({ (Reports.id eq id) and (Reports.status eq OPEN) }) {
                it[status] = RESOLVED
                it[resolvedBy] = moderatorId
                it[resolvedAt] = CurrentTimestamp
                it[Reports.outcome] = outcome.name.lowercase()
                it[Reports.note] = note
            } > 0
        }
}
