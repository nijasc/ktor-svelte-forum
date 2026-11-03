package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.Actor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class Role {
    @SerialName("member")
    MEMBER,

    @SerialName("moderator")
    MODERATOR,

    @SerialName("admin")
    ADMIN,
    ;

    internal val column get() = name.lowercase()

    internal companion object {
        fun fromColumn(value: String) = entries.first { it.column == value }
    }
}

enum class UserStatus {
    ACTIVE,
    BANNED,
    ;

    val column get() = name.lowercase()

    companion object {
        fun fromColumn(value: String) = entries.first { it.column == value }
    }
}

/** The signed-in user, available as `call.principal<AuthUser>()` inside `authenticate(SESSION_AUTH)`. */
data class AuthUser(
    override val id: Long,
    val username: String,
    val role: Role,
) : Actor

/** Looks up users, so other modules can show who wrote something without reading the users table. */
interface UserDirectory {
    /** Names for the ids that exist. Ids with no user are left out. */
    suspend fun usernames(ids: Collection<Long>): Map<Long, String>

    /** The id of the user with this name, ignoring case, or null. */
    suspend fun idOf(username: String): Long?
}

/** A user as moderators see them. */
data class UserSummary(
    val id: Long,
    val username: String,
    val role: Role,
    val status: UserStatus,
)

/** What moderators and admins can do to accounts. Every change is the caller's to authorise and log. */
interface UserAdmin {
    suspend fun find(username: String): UserSummary?

    suspend fun setStatus(
        userId: Long,
        status: UserStatus,
    )

    suspend fun setRole(
        userId: Long,
        role: Role,
    )
}

/** Everything other modules get from [accounts]. */
interface Accounts :
    UserDirectory,
    UserAdmin

internal data class UserRecord(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: Role,
    val status: UserStatus,
    val createdAt: Instant,
) {
    fun toAuthUser() = AuthUser(id, username, role)

    fun toSummary() = UserSummary(id, username, role, status)
}

internal interface UserStore {
    /** Returns the new user, or null if the username is taken (ignoring case). */
    suspend fun create(
        username: String,
        passwordHash: String,
    ): UserRecord?

    suspend fun findByUsername(username: String): UserRecord?

    suspend fun findById(id: Long): UserRecord?
}

/** What anyone may see about a user. */
@Serializable
data class Profile(
    val username: String,
    val role: Role,
    val joinedAt: String,
)
