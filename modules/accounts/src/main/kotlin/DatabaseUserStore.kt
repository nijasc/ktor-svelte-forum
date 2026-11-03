package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

private const val MAX_USERNAME_COLUMN = 64
private const val MAX_HASH_COLUMN = 255
private const val MAX_EMAIL_COLUMN = 254
private const val MAX_ENUM_COLUMN = 16

internal object Users : Table("users") {
    val id = long("id").autoIncrement()
    val username = varchar("username", MAX_USERNAME_COLUMN)
    val passwordHash = varchar("password_hash", MAX_HASH_COLUMN)
    val email = varchar("email", MAX_EMAIL_COLUMN).nullable()
    val role = varchar("role", MAX_ENUM_COLUMN)
    val status = varchar("status", MAX_ENUM_COLUMN)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    val passwordChangedAt = timestamp("password_changed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * [initialAdmin] is the one username that becomes an admin when it signs up, and only while there is no admin yet.
 * That is how the first admin is made without touching the database by hand. That admin's email counts as verified,
 * so a server with no mail set up can still be used.
 */
internal class DatabaseUserStore(
    private val initialAdmin: String? = null,
) : UserStore,
    Accounts {
    override suspend fun create(
        username: String,
        email: String,
        passwordHash: String,
    ): CreateResult =
        dbQuery {
            if (byUsername(username) != null) return@dbQuery CreateResult.UsernameTaken
            if (byEmail(email) != null) return@dbQuery CreateResult.EmailTaken
            val first = initialAdmin.equals(username, ignoreCase = true) && noAdminYet()
            val inserted =
                Users.insertIgnore {
                    it[Users.username] = username
                    it[Users.email] = email
                    it[Users.passwordHash] = passwordHash
                    it[role] = if (first) Role.ADMIN.column else Role.MEMBER.column
                    it[status] = UserStatus.ACTIVE.column
                    if (first) it[emailVerifiedAt] = CurrentTimestamp
                }
            // A concurrent sign-up can take the name or address between the checks and the insert.
            if (inserted.insertedCount == 0) CreateResult.UsernameTaken else CreateResult.Created(checkNotNull(byUsername(username)))
        }

    override suspend fun findByUsername(username: String): UserRecord? = dbQuery { byUsername(username) }

    override suspend fun findByEmail(email: String): UserRecord? = dbQuery { byEmail(email) }

    override suspend fun findById(id: Long): UserRecord? =
        dbQuery {
            Users
                .selectAll()
                .where { Users.id eq id }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun markVerified(userId: Long) {
        dbQuery { Users.update({ Users.id eq userId }) { it[emailVerifiedAt] = CurrentTimestamp } }
    }

    override suspend fun changeEmail(
        userId: Long,
        email: String,
    ): Boolean =
        dbQuery {
            val owner = byEmail(email)
            if (owner != null && owner.id != userId) return@dbQuery false
            Users.update({ Users.id eq userId }) {
                it[Users.email] = email
                it[emailVerifiedAt] = null
            }
            true
        }

    override suspend fun setPassword(
        userId: Long,
        passwordHash: String,
    ) {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[Users.passwordHash] = passwordHash
                // The application's clock, because sessions record theirs and the two are compared.
                it[passwordChangedAt] = Instant.now()
            }
        }
    }

    override suspend fun usernames(ids: Collection<Long>): Map<Long, String> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            dbQuery {
                Users
                    .select(Users.id, Users.username)
                    .where { Users.id inList ids.toSet() }
                    .associate { it[Users.id] to it[Users.username] }
            }
        }

    override suspend fun idOf(username: String): Long? = dbQuery { byUsername(username)?.id }

    override suspend fun find(username: String): UserSummary? = dbQuery { byUsername(username)?.toSummary() }

    override suspend fun setStatus(
        userId: Long,
        status: UserStatus,
    ) {
        dbQuery { Users.update({ Users.id eq userId }) { it[Users.status] = status.column } }
    }

    override suspend fun setRole(
        userId: Long,
        role: Role,
    ) {
        dbQuery { Users.update({ Users.id eq userId }) { it[Users.role] = role.column } }
    }

    suspend fun profile(username: String): Profile? =
        dbQuery { byUsername(username)?.let { Profile(it.username, it.role, it.createdAt.toString()) } }

    private fun noAdminYet() = Users.selectAll().where { Users.role eq Role.ADMIN.column }.empty()

    private fun byUsername(username: String): UserRecord? =
        Users
            .selectAll()
            .where { Users.username.lowerCase() eq username.lowercase() }
            .singleOrNull()
            ?.toRecord()

    private fun byEmail(email: String): UserRecord? =
        Users
            .selectAll()
            .where { Users.email.lowerCase() eq email.lowercase() }
            .singleOrNull()
            ?.toRecord()

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            username = this[Users.username],
            passwordHash = this[Users.passwordHash],
            role = Role.fromColumn(this[Users.role]),
            status = UserStatus.fromColumn(this[Users.status]),
            createdAt = this[Users.createdAt],
            email = this[Users.email],
            emailVerified = this[Users.emailVerifiedAt] != null,
            passwordChangedAt = this[Users.passwordChangedAt],
        )
}
