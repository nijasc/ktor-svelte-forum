package dev.nilsschlegel.forum

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val MAX_POOL_SIZE = 10
private const val CONNECTION_TIMEOUT_MILLIS = 5_000L

object Visits : Table("visits") {
    val id = integer("id").autoIncrement()

    override val primaryKey = PrimaryKey(id)
}

@Serializable
data class VisitCount(
    val visits: Long,
)

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

fun Route.databaseRoutes() {
    post("/api/visits") {
        val count =
            withContext(Dispatchers.IO) {
                transaction {
                    Visits.insert { }
                    Visits.selectAll().count()
                }
            }
        call.respond(HttpStatusCode.Created, VisitCount(count))
    }
}
