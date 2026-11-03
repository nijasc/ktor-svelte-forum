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

@Serializable
enum class UserStatus {
    @SerialName("active")
    ACTIVE,

    @SerialName("banned")
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
    /** Whether the user has confirmed their email address. Writing needs it, see [requireVerified]. */
    val verified: Boolean,
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
    val email: String?,
    val emailVerified: Boolean,
    val passwordChangedAt: Instant?,
) {
    fun toAuthUser() = AuthUser(id, username, role, emailVerified)

    fun toSummary() = UserSummary(id, username, role, status)
}

internal interface UserStore {
    suspend fun create(
        username: String,
        email: String,
        passwordHash: String,
    ): CreateResult

    suspend fun findByUsername(username: String): UserRecord?

    suspend fun findById(id: Long): UserRecord?

    suspend fun findByEmail(email: String): UserRecord?

    suspend fun markVerified(userId: Long)

    /** Sets a new address and takes the verified mark away. Returns false if another user has the address. */
    suspend fun changeEmail(
        userId: Long,
        email: String,
    ): Boolean

    /** Sets a new password and records when, which signs out every session started before. */
    suspend fun setPassword(
        userId: Long,
        passwordHash: String,
    )
}

internal sealed interface CreateResult {
    data class Created(
        val user: UserRecord,
    ) : CreateResult

    data object UsernameTaken : CreateResult

    data object EmailTaken : CreateResult
}

/** What anyone may see about a user. */
@Serializable
data class Profile(
    val username: String,
    val role: Role,
    val joinedAt: String,
)
