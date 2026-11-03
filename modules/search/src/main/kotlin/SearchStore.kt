package dev.nilsschlegel.forum.search

import dev.nilsschlegel.forum.accounts.UserDirectory
import dev.nilsschlegel.forum.database.dbQuery
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.Connection

/** Words in the thread titles and posts, best match first. */
internal class SearchStore(
    private val users: UserDirectory,
) {
    private class Row(
        val kind: HitKind,
        val threadId: Long,
        val postId: Long?,
        val title: String,
        val snippet: String,
        val authorId: Long,
        val category: String,
        val createdAt: String,
    )

    /**
     * [text] goes through `websearch_to_tsquery`, which reads quotes, `OR` and `-word` and accepts any input, so it
     * cannot break the query. Hidden and deleted content, and hidden threads, never match. [offset] is how many hits to skip.
     */
    suspend fun search(
        text: String,
        offset: Int,
        limit: Int,
    ): SearchPage {
        val rows = dbQuery { query(text, offset, limit + 1) }
        val shown = rows.take(limit)
        val names = users.usernames(shown.map { it.authorId })
        val items =
            shown.map {
                SearchHit(it.kind, it.threadId, it.postId, it.title, it.snippet, names[it.authorId] ?: "deleted", it.category, it.createdAt)
            }
        return SearchPage(items, if (rows.size > limit) (offset + limit).toString() else null)
    }

    private fun query(
        text: String,
        offset: Int,
        limit: Int,
    ): List<Row> {
        val connection = TransactionManager.current().connection.connection as Connection
        connection.prepareStatement(SQL).use { statement ->
            statement.setString(1, text)
            statement.setString(2, HEADLINE)
            statement.setString(3, HEADLINE)
            statement.setInt(4, limit)
            statement.setInt(5, offset)
            statement.executeQuery().use { rs ->
                return buildList {
                    while (rs.next()) {
                        add(
                            Row(
                                kind = if (rs.getString("kind") == "thread") HitKind.THREAD else HitKind.POST,
                                threadId = rs.getLong("thread_id"),
                                postId = rs.getLong("post_id").takeUnless { rs.wasNull() },
                                title = rs.getString("title"),
                                snippet = rs.getString("snippet"),
                                authorId = rs.getLong("author_id"),
                                category = rs.getString("category"),
                                createdAt = rs.getTimestamp("created_at").toInstant().toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** A word in a thread's title says more about the thread than the same word in a post, so title hits rank higher. */
        const val TITLE_BOOST = 2.0

        const val HEADLINE = "StartSel=$MATCH_START, StopSel=$MATCH_END, MaxFragments=1, MaxWords=25, MinWords=8, ShortWord=0"

        val SQL =
            """
            WITH q AS (SELECT websearch_to_tsquery('simple', ?) AS query)
            SELECT * FROM (
                SELECT 'thread' AS kind, t.id AS thread_id, NULL::bigint AS post_id, t.title AS title,
                       ts_headline('simple', search_text(t.title), q.query, ?) AS snippet,
                       t.author_id, c.slug AS category, t.created_at,
                       ts_rank(to_tsvector('simple', search_text(t.title)), q.query) * $TITLE_BOOST AS rank
                FROM threads t JOIN categories c ON c.id = t.category_id, q
                WHERE t.hidden_at IS NULL AND to_tsvector('simple', search_text(t.title)) @@ q.query
                UNION ALL
                SELECT 'post', t.id, p.id, t.title,
                       ts_headline('simple', search_text(p.body), q.query, ?),
                       p.author_id, c.slug, p.created_at,
                       ts_rank(to_tsvector('simple', search_text(p.body)), q.query)
                FROM posts p JOIN threads t ON t.id = p.thread_id JOIN categories c ON c.id = t.category_id, q
                WHERE p.deleted_at IS NULL AND p.hidden_at IS NULL AND t.hidden_at IS NULL
                  AND to_tsvector('simple', search_text(p.body)) @@ q.query
            ) hits
            ORDER BY rank DESC, created_at DESC, post_id DESC NULLS LAST
            LIMIT ? OFFSET ?
            """.trimIndent()
    }
}
