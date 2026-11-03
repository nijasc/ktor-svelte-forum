package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.Accounts
import dev.nilsschlegel.forum.accounts.AuthUser
import dev.nilsschlegel.forum.accounts.Role
import dev.nilsschlegel.forum.accounts.SESSION_AUTH
import dev.nilsschlegel.forum.accounts.UserStatus
import dev.nilsschlegel.forum.accounts.canBan
import dev.nilsschlegel.forum.accounts.requireRole
import dev.nilsschlegel.forum.core.ApiException
import dev.nilsschlegel.forum.core.RateLimits
import dev.nilsschlegel.forum.forum.ForumStaff
import dev.nilsschlegel.forum.forum.HideResult
import dev.nilsschlegel.forum.forum.MoveResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

private const val DEFAULT_LIMIT = 20
private const val MAX_LIMIT = 100

/** Any signed-in member may report a post. Moderators see the reports under `/api/staff/reports`. */
internal fun Route.reportRoutes(
    forum: ForumStaff,
    reports: ReportStore,
) {
    authenticate(SESSION_AUTH) {
        rateLimit(RateLimits.WRITE) {
            post("/api/posts/{id}/reports") {
                val user = call.requireRole(Role.MEMBER)
                val postId = call.id()
                val new = call.receive<NewReport>()
                forum.post(postId) ?: throw NotFoundException()
                if (!reports.file(postId, user.id, new.reason.trim())) {
                    throw ApiException(HttpStatusCode.Conflict, "You have already reported this post.")
                }
                call.respond(HttpStatusCode.Created)
            }
        }
    }
}

internal fun Route.staffRoutes(
    accounts: Accounts,
    forum: ForumStaff,
    audit: AuditLog,
    reports: ReportStore,
) {
    authenticate(SESSION_AUTH) {
        route("/api/staff") {
            threadRoutes(forum, audit)
            postRoutes(forum, audit)
            userRoutes(accounts, audit)
            reportQueueRoutes(reports, audit)
            categoryRoutes(forum, audit)
            get("/audit") {
                call.requireRole(Role.MODERATOR)
                call.respond(audit.page(call.cursor(), call.limit()))
            }
        }
    }
}

private fun Route.threadRoutes(
    forum: ForumStaff,
    audit: AuditLog,
) {
    val simple: List<Triple<String, String, suspend (Long) -> Boolean>> =
        listOf(
            Triple("pin", "thread.pin") { forum.setThreadPinned(it, true) },
            Triple("unpin", "thread.unpin") { forum.setThreadPinned(it, false) },
            Triple("lock", "thread.lock") { forum.setThreadLocked(it, true) },
            Triple("unlock", "thread.unlock") { forum.setThreadLocked(it, false) },
            Triple("restore", "thread.restore") { forum.setThreadHidden(it, false) },
        )
    for ((path, action, run) in simple) {
        post("/threads/{id}/$path") {
            val actor = call.requireRole(Role.MODERATOR)
            val id = call.id()
            if (!run(id)) throw NotFoundException()
            audit.record(actor.id, action, "thread", id.toString())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post("/threads/{id}/hide") {
        val actor = call.requireRole(Role.MODERATOR)
        val id = call.id()
        val reason = call.receive<ActionReason>().reason.trim()
        if (!forum.setThreadHidden(id, true)) throw NotFoundException()
        audit.record(actor.id, "thread.hide", "thread", id.toString(), reason)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/threads/{id}/move") {
        val actor = call.requireRole(Role.MODERATOR)
        val id = call.id()
        val target = call.receive<MoveThread>().category
        when (forum.moveThread(id, target)) {
            MoveResult.MOVED -> audit.record(actor.id, "thread.move", "thread", id.toString(), target)
            MoveResult.NO_THREAD -> throw NotFoundException()
            MoveResult.NO_CATEGORY -> throw ApiException(HttpStatusCode.UnprocessableEntity, "That category does not exist.")
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.postRoutes(
    forum: ForumStaff,
    audit: AuditLog,
) {
    post("/posts/{id}/hide") {
        val actor = call.requireRole(Role.MODERATOR)
        val reason = call.receive<ActionReason>().reason.trim()
        hide(call, forum, audit, actor, hidden = true, detail = reason)
    }
    post("/posts/{id}/restore") {
        val actor = call.requireRole(Role.MODERATOR)
        hide(call, forum, audit, actor, hidden = false, detail = "")
    }
}

private suspend fun hide(
    call: ApplicationCall,
    forum: ForumStaff,
    audit: AuditLog,
    actor: AuthUser,
    hidden: Boolean,
    detail: String,
) {
    val id = call.id()
    when (forum.setPostHidden(id, hidden)) {
        is HideResult.Done -> audit.record(actor.id, if (hidden) "post.hide" else "post.restore", "post", id.toString(), detail)
        HideResult.NotFound -> throw NotFoundException()
        HideResult.IsFirstPost -> throw ApiException(HttpStatusCode.Conflict, "Hide the thread instead of its first post.")
    }
    call.respond(HttpStatusCode.NoContent)
}

private fun Route.userRoutes(
    accounts: Accounts,
    audit: AuditLog,
) {
    get("/users/{username}") {
        call.requireRole(Role.MODERATOR)
        val user = accounts.find(call.username()) ?: throw NotFoundException()
        call.respond(StaffUserView(user.username, user.role, user.status))
    }
    post("/users/{username}/ban") {
        val actor = call.requireRole(Role.MODERATOR)
        val reason = call.receive<ActionReason>().reason.trim()
        setStatus(call, accounts, audit, actor, UserStatus.BANNED, "user.ban", reason)
    }
    post("/users/{username}/unban") {
        val actor = call.requireRole(Role.MODERATOR)
        setStatus(call, accounts, audit, actor, UserStatus.ACTIVE, "user.unban", "")
    }
    put("/users/{username}/role") {
        val actor = call.requireRole(Role.ADMIN)
        val role = call.receive<RoleChange>().role
        val user = accounts.find(call.username()) ?: throw NotFoundException()
        if (user.id == actor.id) throw ApiException(HttpStatusCode.Forbidden, "You cannot change your own role.")
        accounts.setRole(user.id, role)
        audit.record(actor.id, "user.role", "user", user.username, "${user.role.name.lowercase()} to ${role.name.lowercase()}")
        call.respond(HttpStatusCode.NoContent)
    }
}

private suspend fun setStatus(
    call: ApplicationCall,
    accounts: Accounts,
    audit: AuditLog,
    actor: AuthUser,
    status: UserStatus,
    action: String,
    detail: String,
) {
    val user = accounts.find(call.username()) ?: throw NotFoundException()
    if (!canBan(actor.role, user.role)) throw ApiException(HttpStatusCode.Forbidden, "You are not allowed to do that to this user.")
    accounts.setStatus(user.id, status)
    audit.record(actor.id, action, "user", user.username, detail)
    call.respond(HttpStatusCode.NoContent)
}

private fun Route.reportQueueRoutes(
    reports: ReportStore,
    audit: AuditLog,
) {
    get("/reports") {
        call.requireRole(Role.MODERATOR)
        call.respond(reports.open(call.cursor(), call.limit()))
    }
    post("/reports/{id}/resolve") {
        val actor = call.requireRole(Role.MODERATOR)
        val id = call.id()
        val body = call.receive<ResolveReport>()
        if (!reports.resolve(id, actor.id, body.outcome, body.note.trim())) throw NotFoundException()
        audit.record(
            actor.id,
            "report.resolve",
            "report",
            id.toString(),
            "${body.outcome.name.lowercase()}: ${body.note.trim()}".trimEnd(' ', ':'),
        )
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun Route.categoryRoutes(
    forum: ForumStaff,
    audit: AuditLog,
) {
    get("/categories") {
        call.requireRole(Role.ADMIN)
        call.respond(forum.categories())
    }
    post("/categories") {
        val actor = call.requireRole(Role.ADMIN)
        val new = call.receive<NewCategory>()
        val created =
            forum.createCategory(new.slug, new.title.trim(), new.description.trim())
                ?: throw ApiException(HttpStatusCode.Conflict, "That slug is taken.")
        audit.record(actor.id, "category.create", "category", created.slug, created.title)
        call.respond(HttpStatusCode.Created, created)
    }
    put("/categories/order") {
        val actor = call.requireRole(Role.ADMIN)
        val order = call.receive<CategoryOrder>().slugs
        if (!forum.reorderCategories(order)) throw ApiException(HttpStatusCode.UnprocessableEntity, "List every category exactly once.")
        audit.record(actor.id, "category.reorder", "category", "*", order.joinToString(","))
        call.respond(HttpStatusCode.NoContent)
    }
    patch("/categories/{slug}") {
        val actor = call.requireRole(Role.ADMIN)
        val slug = call.parameters["slug"] ?: throw NotFoundException()
        val change = call.receive<UpdateCategory>()
        val updated =
            forum.updateCategory(slug, change.title?.trim(), change.description?.trim(), change.archived) ?: throw NotFoundException()
        val detail =
            listOfNotNull(
                change.title?.let {
                    "title"
                },
                change.description?.let { "description" },
                change.archived?.let { if (it) "archived" else "unarchived" },
            )
        audit.record(actor.id, "category.update", "category", slug, detail.joinToString(","))
        call.respond(updated)
    }
}

private fun ApplicationCall.id(): Long = parameters["id"]?.toLongOrNull() ?: throw NotFoundException()

private fun ApplicationCall.username(): String = parameters["username"] ?: throw NotFoundException()

private fun ApplicationCall.cursor(): Long? =
    request.queryParameters["cursor"]?.let {
        it.toLongOrNull()
            ?: throw BadRequestException("Not a valid cursor.")
    }

private fun ApplicationCall.limit(): Int {
    val value = request.queryParameters["limit"]?.let { it.toIntOrNull() ?: throw BadRequestException("Not a valid limit.") }
    return (value ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
}
