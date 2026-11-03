package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/**
 * The signed-in user, who must have at least the [minimum] role. Call it first in a handler inside `authenticate(SESSION_AUTH)`.
 * Roles are ordered: a moderator passes a member check, an admin passes both.
 */
fun ApplicationCall.requireRole(minimum: Role): AuthUser {
    val user = principal<AuthUser>() ?: throw ApiException(HttpStatusCode.Unauthorized, "Sign in first.")
    if (user.role < minimum) throw ApiException(HttpStatusCode.Forbidden, "You are not allowed to do that.")
    return user
}

/** Whether [actor] may ban or unban [target]. Moderators handle members, admins also handle moderators, nobody handles admins. */
fun canBan(
    actor: Role,
    target: Role,
): Boolean =
    when (target) {
        Role.MEMBER -> actor >= Role.MODERATOR
        Role.MODERATOR -> actor >= Role.ADMIN
        Role.ADMIN -> false
    }

/**
 * Like [requireRole] for a plain member, and the email address must be confirmed too. Use it for anything that
 * creates content, which keeps throwaway accounts with made-up addresses from posting.
 */
fun ApplicationCall.requireVerified(): AuthUser {
    val user = requireRole(Role.MEMBER)
    if (!user.verified) throw ApiException(HttpStatusCode.Forbidden, "Confirm your email address first.")
    return user
}
