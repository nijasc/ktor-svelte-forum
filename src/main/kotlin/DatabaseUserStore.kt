package dev.nilsschlegel.forum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val MAX_USERNAME_COLUMN = 64
private const val MAX_HASH_COLUMN = 255

object Users : Table("users") {
    val username = varchar("username", MAX_USERNAME_COLUMN)
    val passwordHash = varchar("password_hash", MAX_HASH_COLUMN)

    override val primaryKey = PrimaryKey(username)
}

class DatabaseUserStore : UserStore {
    override suspend fun create(
        username: String,
        passwordHash: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            transaction {
                Users
                    .insertIgnore {
                        it[Users.username] = username
                        it[Users.passwordHash] = passwordHash
                    }.insertedCount > 0
            }
        }

    override suspend fun passwordHash(username: String): String? =
        withContext(Dispatchers.IO) {
            transaction {
                Users
                    .selectAll()
                    .where { Users.username eq username }
                    .singleOrNull()
                    ?.get(Users.passwordHash)
            }
        }
}
