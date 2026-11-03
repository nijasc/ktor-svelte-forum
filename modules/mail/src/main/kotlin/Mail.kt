package dev.nilsschlegel.forum.mail

import dev.nilsschlegel.forum.core.env
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A plain text email. */
data class MailMessage(
    val to: String,
    val subject: String,
    val text: String,
)

/** Sending failed. The message says why, without the API key. */
class MailException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Where email goes. Callers decide whether a failure matters, a lost reset link does not stop a request. */
fun interface MailSender {
    suspend fun send(message: MailMessage)
}

/** Writes the email to the log instead of sending it. This is the default, so development needs no account. */
class LogMailSender : MailSender {
    private val log = LoggerFactory.getLogger(LogMailSender::class.java)

    override suspend fun send(message: MailMessage) {
        log.info("Mail to {}\nSubject: {}\n\n{}", message.to, message.subject, message.text)
    }
}

/** Sends through [Resend](https://resend.com). [baseUrl] is a parameter so tests can point it at a local server. */
class ResendMailSender(
    private val apiKey: String,
    private val from: String,
    private val baseUrl: String = "https://api.resend.com",
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : MailSender {
    @Serializable
    private data class Payload(
        val from: String,
        val to: List<String>,
        val subject: String,
        val text: String,
    )

    override suspend fun send(message: MailMessage) {
        val body = Json.encodeToString(Payload(from, listOf(message.to), message.subject, message.text))
        val request =
            HttpRequest
                .newBuilder(URI.create("$baseUrl/emails"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response =
            try {
                withContext(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofString()) }
            } catch (failure: java.io.IOException) {
                throw MailException("Could not reach the mail provider: ${failure.message}", failure)
            }
        if (response.statusCode() !in 200..299) {
            throw MailException("The mail provider answered ${response.statusCode()}: ${response.body().take(MAX_ERROR_BODY)}")
        }
    }

    private companion object {
        const val MAX_ERROR_BODY = 300
    }
}

/**
 * Picks the sender from the environment. `MAIL_PROVIDER` is `log` (the default) or `resend`, which also needs
 * `RESEND_API_KEY` and `MAIL_FROM`.
 */
fun mailSenderFromEnv(): MailSender =
    when (val provider = env("MAIL_PROVIDER") ?: "log") {
        "log" -> LogMailSender()
        "resend" ->
            ResendMailSender(
                apiKey = requireNotNull(env("RESEND_API_KEY")) { "Set RESEND_API_KEY when MAIL_PROVIDER is resend." },
                from = requireNotNull(env("MAIL_FROM")) { "Set MAIL_FROM when MAIL_PROVIDER is resend." },
            )
        else -> error("MAIL_PROVIDER must be log or resend, not $provider.")
    }
