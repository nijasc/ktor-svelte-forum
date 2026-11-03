package dev.nilsschlegel.forum.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.nilsschlegel.forum.core.requireEnv
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val MAX_POOL_SIZE = 10
private const val CONNECTION_TIMEOUT_MILLIS = 5_000L

/**
 * Connects to Postgres when the app starts and runs every Flyway migration found on the classpath under `db/migration`.
 * Each module ships its own migrations. Version numbers are shared by all modules, so see the README before adding one.
 */
fun Application.configureDatabase() {
    monitor.subscribe(ApplicationStarted) {
        val dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = requireEnv("DATABASE_URL")
                    username = requireEnv("POSTGRES_USER")
                    password = requireEnv("POSTGRES_PASSWORD")
                    maximumPoolSize = MAX_POOL_SIZE
                    connectionTimeout = CONNECTION_TIMEOUT_MILLIS
                },
            )
        monitor.subscribe(ApplicationStopped) { dataSource.close() }
        Database.connect(dataSource)
        Flyway
            .configure()
            .dataSource(dataSource)
            .load()
            .migrate()
    }
}

/** Runs [block] in a transaction on the IO dispatcher. Every repository goes through this. */
suspend fun <T> dbQuery(block: () -> T): T = withContext(Dispatchers.IO) { transaction { block() } }
