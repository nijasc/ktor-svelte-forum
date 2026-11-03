package dev.nilsschlegel.forum.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Kind {
    /** Someone replied in a thread the user watches. */
    @SerialName("reply")
    REPLY,

    /** Someone wrote the user's name after an `@`. */
    @SerialName("mention")
    MENTION,
    ;

    internal val column get() = name.lowercase()

    internal companion object {
        fun fromColumn(value: String) = entries.first { it.column == value }
    }
}

@Serializable
data class NotificationView(
    val id: Long,
    val kind: Kind,
    val actor: String,
    val threadId: Long,
    val threadTitle: String,
    val postId: Long,
    /** More than one when several replies came while this one was still unread. */
    val count: Int,
    val read: Boolean,
    val createdAt: String,
)

/** One page, newest first. [unread] counts all unread notifications, not only those on this page. */
@Serializable
data class NotificationPage(
    val items: List<NotificationView>,
    val next: String?,
    val unread: Long,
)

@Serializable
data class UnreadCount(
    val count: Long,
)

@Serializable
data class WatchState(
    val watching: Boolean,
)
