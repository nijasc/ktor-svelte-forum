package dev.nilsschlegel.forum.moderation

import dev.nilsschlegel.forum.accounts.Role
import dev.nilsschlegel.forum.accounts.UserStatus
import io.github.nijasc.ktor.svelte.Validated
import io.github.nijasc.ktor.svelte.Validator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MAX_REASON = 500
private const val MAX_NOTE = 1_000
private const val MAX_SLUG = 64
private const val MAX_TITLE = 120
private const val MAX_DESCRIPTION = 500
private val SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")

/** Why a moderator did something. It goes into the audit log. */
@Serializable
data class ActionReason(
    val reason: String,
) : Validated {
    override fun Validator.rules() {
        check("reason", reason.isNotBlank() && reason.length <= MAX_REASON) { "Give a reason of up to $MAX_REASON characters." }
    }
}

@Serializable
data class NewReport(
    val reason: String,
) : Validated {
    override fun Validator.rules() {
        check("reason", reason.isNotBlank() && reason.length <= MAX_REASON) { "Say what is wrong, in up to $MAX_REASON characters." }
    }
}

@Serializable
enum class ReportOutcome {
    @SerialName("dismissed")
    DISMISSED,

    @SerialName("actioned")
    ACTIONED,
}

@Serializable
data class ResolveReport(
    val outcome: ReportOutcome,
    val note: String = "",
) : Validated {
    override fun Validator.rules() {
        check("note", note.length <= MAX_NOTE) { "Keep the note under $MAX_NOTE characters." }
    }
}

@Serializable
data class MoveThread(
    val category: String,
)

@Serializable
data class RoleChange(
    val role: Role,
)

@Serializable
data class NewCategory(
    val slug: String,
    val title: String,
    val description: String = "",
) : Validated {
    override fun Validator.rules() {
        check("slug", slug.length <= MAX_SLUG && SLUG.matches(slug)) { "Use lowercase letters, digits and single hyphens in the slug." }
        check("title", title.isNotBlank() && title.length <= MAX_TITLE) { "Use a title of up to $MAX_TITLE characters." }
        check("description", description.length <= MAX_DESCRIPTION) { "Keep the description under $MAX_DESCRIPTION characters." }
    }
}

@Serializable
data class UpdateCategory(
    val title: String? = null,
    val description: String? = null,
    val archived: Boolean? = null,
) : Validated {
    override fun Validator.rules() {
        check("title", title == null || (title.isNotBlank() && title.length <= MAX_TITLE)) { "Use a title of up to $MAX_TITLE characters." }
        check("description", description == null || description.length <= MAX_DESCRIPTION) {
            "Keep the description under $MAX_DESCRIPTION characters."
        }
    }
}

@Serializable
data class CategoryOrder(
    val slugs: List<String>,
)

@Serializable
data class StaffUserView(
    val username: String,
    val role: Role,
    val status: UserStatus,
)

@Serializable
data class AuditEntry(
    val id: Long,
    val actor: String,
    val action: String,
    val targetType: String,
    val targetId: String,
    val detail: String,
    val createdAt: String,
)

@Serializable
data class AuditPage(
    val items: List<AuditEntry>,
    val next: String?,
)

@Serializable
data class ReportedPost(
    val id: Long,
    val threadId: Long,
    val threadTitle: String,
    val author: String,
    val body: String,
    val hidden: Boolean,
)

@Serializable
data class ReportView(
    val id: Long,
    val post: ReportedPost,
    val reporter: String,
    val reason: String,
    val createdAt: String,
)

@Serializable
data class ReportPage(
    val items: List<ReportView>,
    val next: String?,
)
