package dev.nilsschlegel.forum.accounts

import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

private const val TOKEN_BYTES = 32
private const val MAX_EMAIL_COLUMN = 254
private const val MAX_PURPOSE_COLUMN = 16

internal enum class TokenPurpose(
    val column: String,
    val lifetime: Duration,
) {
    VERIFY("verify", Duration.ofDays(3)),
    RESET("reset", Duration.ofHours(1)),
}

internal object EmailTokens : Table("email_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id")
    val purpose = varchar("purpose", MAX_PURPOSE_COLUMN)
    val tokenHash = char("token_hash", 64)
    val email = varchar("email", MAX_EMAIL_COLUMN).nullable()
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Who a used token belonged to. [email] is the address a verification token was sent to. */
internal data class TokenOwner(
    val userId: Long,
    val email: String?,
)

/**
 * One-time links by email. The token itself is only ever in the email. The database keeps its SHA-256, so a copy of the
 * database cannot be used to verify an address or reset a password.
 */
internal class TokenStore {
    /** A new token, which replaces older unused ones of the same purpose for that user. */
    suspend fun issue(
        userId: Long,
        purpose: TokenPurpose,
        email: String?,
    ): String {
        val token = newToken()
        dbQuery {
            EmailTokens.update(
                { (EmailTokens.userId eq userId) and (EmailTokens.purpose eq purpose.column) and EmailTokens.usedAt.isNull() },
            ) {
                it[usedAt] = CurrentTimestamp
            }
            EmailTokens.insert {
                it[EmailTokens.userId] = userId
                it[EmailTokens.purpose] = purpose.column
                it[tokenHash] = hash(token)
                it[EmailTokens.email] = email
                it[expiresAt] = Instant.now().plus(purpose.lifetime)
            }
        }
        return token
    }

    /** Uses the token up if it is valid, unused and not expired. A token works once. */
    suspend fun consume(
        token: String,
        purpose: TokenPurpose,
    ): TokenOwner? =
        dbQuery {
            val valid = (EmailTokens.tokenHash eq hash(token)) and (EmailTokens.purpose eq purpose.column) and EmailTokens.usedAt.isNull()
            val row =
                EmailTokens
                    .selectAll()
                    .where { valid and (EmailTokens.expiresAt greater Instant.now()) }
                    .singleOrNull() ?: return@dbQuery null
            // The update repeats the unused check, so two requests with the same token cannot both succeed.
            val claimed =
                EmailTokens.update({ (EmailTokens.id eq row[EmailTokens.id]) and EmailTokens.usedAt.isNull() }) {
                    it[usedAt] =
                        CurrentTimestamp
                }
            if (claimed == 0) null else TokenOwner(row[EmailTokens.userId], row[EmailTokens.email])
        }

    /** Uses up every unused token of a purpose for the user, for example after a password reset. */
    suspend fun revokeAll(
        userId: Long,
        purpose: TokenPurpose,
    ) {
        dbQuery {
            EmailTokens.update(
                { (EmailTokens.userId eq userId) and (EmailTokens.purpose eq purpose.column) and EmailTokens.usedAt.isNull() },
            ) {
                it[usedAt] = CurrentTimestamp
            }
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
