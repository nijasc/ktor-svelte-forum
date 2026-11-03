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

    override val primaryKey = PrimaryKey(id)
}

/**
 * [initialAdmin] is the one username that becomes an admin when it signs up, and only while there is no admin yet.
 * That is how the first admin is made without touching the database by hand.
 */
internal class DatabaseUserStore(
    private val initialAdmin: String? = null,
) : UserStore,
    Accounts {
    override suspend fun create(
        username: String,
        passwordHash: String,
    ): UserRecord? =
        dbQuery {
            val first =
                initialAdmin.equals(username, ignoreCase = true) && Users.selectAll().where { Users.role eq Role.ADMIN.column }.empty()
            val inserted =
                Users.insertIgnore {
                    it[Users.username] = username
                    it[Users.passwordHash] = passwordHash
                    it[role] = if (first) Role.ADMIN.column else Role.MEMBER.column
                    it[status] = UserStatus.ACTIVE.column
                }
            if (inserted.insertedCount == 0) null else byUsername(username)
        }

    override suspend fun findByUsername(username: String): UserRecord? = dbQuery { byUsername(username) }

    override suspend fun findById(id: Long): UserRecord? =
        dbQuery {
            Users
                .selectAll()
                .where { Users.id eq id }
                .singleOrNull()
                ?.toRecord()
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

    private fun ResultRow.toRecord() =
        UserRecord(
            id = this[Users.id],
            username = this[Users.username],
            passwordHash = this[Users.passwordHash],
            role = Role.fromColumn(this[Users.role]),
            status = UserStatus.fromColumn(this[Users.status]),
            createdAt = this[Users.createdAt],
        )
}
