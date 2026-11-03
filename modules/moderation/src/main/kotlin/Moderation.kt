package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.Accounts
import dev.nilsschlegel.forum.forum.ForumStaff
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/**
 * Serves `/api/staff` for moderators and admins, and the route that lets members report a post.
 * Needs [dev.nilsschlegel.forum.accounts.accounts] and [dev.nilsschlegel.forum.forum.forum] installed first.
 */
fun Application.moderation(
    accounts: Accounts,
    forum: ForumStaff,
) {
    val audit = AuditLog(accounts)
    val reports = ReportStore(accounts, forum)
    routing {
        reportRoutes(forum, reports)
        staffRoutes(accounts, forum, audit, reports)
    }
}
