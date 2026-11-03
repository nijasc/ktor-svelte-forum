package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.core.Actor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

internal enum class UserStatus {
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

/** Looks up display names, so other modules can show who wrote something without reading the users table. */
fun interface UserDirectory {
    /** Names for the ids that exist. Ids with no user are left out. */
    suspend fun usernames(ids: Collection<Long>): Map<Long, String>
}

internal data class UserRecord(
    val id: Long,
    val username: String,
    val passwordHash: String,
    val role: Role,
    val status: UserStatus,
) {
    fun toAuthUser() = AuthUser(id, username, role)
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
