package dev.nilsschlegel.forum.database

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection

/**
 * A real Postgres for repository tests, started once per test run and shared by all tests in that JVM.
 *
 * Call [reset] in `@BeforeEach`. It skips the test when Docker is not available, wipes the schema, runs every
 * migration found on the test classpath and connects Exposed. The classpath decides which modules' migrations run.
 */
object TestDatabase {
    private val container by lazy { PostgreSQLContainer("postgres:18-alpine").also { it.start() } }

    private val flyway get() =
        Flyway
            .configure()
            .dataSource(container.jdbcUrl, container.username, container.password)

    /** Starts from an empty schema and migrates up to [target], or to the latest version. */
    fun reset(target: String = "latest") {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker is not available")
        connection { it.createStatement().use { s -> s.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;") } }
        migrate(target)
        Database.connect(container.jdbcUrl, user = container.username, password = container.password)
    }

    /** Applies the migrations after the current version, up to [target]. */
    fun migrate(target: String = "latest") {
        flyway.target(target).load().migrate()
    }

    /** Runs [block] on a plain JDBC connection, for setting up or reading rows without the code under test. */
    fun <T> connection(block: (Connection) -> T): T = container.createConnection("").use(block)

    fun execute(sql: String) = connection { it.createStatement().use { s -> s.execute(sql) } }
}
