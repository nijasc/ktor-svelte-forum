package dev.nilsschlegel.forum.forum

import dev.nilsschlegel.forum.database.TestDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the migrations over rows written by the first version of the app, which had `username` as the key. */
class MigrationTest {
    @Test
    fun `existing users threads and posts survive the move to user ids`() {
        TestDatabase.reset(target = "2")
        TestDatabase.execute(
            """
            INSERT INTO users (username, password_hash) VALUES ('Alice', 'h1'), ('bob', 'h2');
            INSERT INTO threads (category_id, title, author) VALUES (1, 'First', 'Alice');
            INSERT INTO posts (thread_id, author, body) VALUES (1, 'Alice', 'a'), (1, 'bob', 'b'), (1, 'bob', 'c');
            """.trimIndent(),
        )

        TestDatabase.migrate()

        val rows =
            TestDatabase.connection { c ->
                c.createStatement().use { s ->
                    val rs =
                        s.executeQuery(
                            """
                            SELECT u.username, p.body, t.reply_count, u.role, u.status
                            FROM posts p JOIN users u ON u.id = p.author_id JOIN threads t ON t.id = p.thread_id
                            ORDER BY p.id
                            """.trimIndent(),
                        )
                    buildList {
                        while (rs.next()) {
                            add(
                                listOf(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)),
                            )
                        }
                    }
                }
            }
        assertEquals(
            listOf(
                listOf("Alice", "a", 2, "member", "active"),
                listOf("bob", "b", 2, "member", "active"),
                listOf("bob", "c", 2, "member", "active"),
            ),
            rows,
        )
    }
}
