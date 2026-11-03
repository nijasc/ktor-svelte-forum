package dev.nilsschlegel.forum.mail

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResendMailSenderTest {
    private var status = 200
    private var reply = """{"id":"1"}"""
    private var seenPath = ""
    private var seenAuth = ""
    private var seenBody = ""

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                seenPath = exchange.requestURI.path
                seenAuth = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                seenBody = exchange.requestBody.readBytes().decodeToString()
                val bytes = reply.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    private val sender = ResendMailSender("secret-key", "Forum <forum@example.com>", baseUrl = "http://127.0.0.1:${server.address.port}")

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun `posts the message to the provider with the key`() =
        runBlocking {
            sender.send(MailMessage("ann@example.com", "Hello", "Line one\nLine two"))
            assertEquals("/emails", seenPath)
            assertEquals("Bearer secret-key", seenAuth)
            assertTrue(""""to":["ann@example.com"]""" in seenBody, seenBody)
            assertTrue(""""subject":"Hello"""" in seenBody, seenBody)
            assertTrue(""""from":"Forum <forum@example.com>"""" in seenBody, seenBody)
            assertTrue("Line one\\nLine two" in seenBody, seenBody)
        }

    @Test
    fun `a provider error is reported without leaking the key`() =
        runBlocking {
            status = 422
            reply = """{"message":"Invalid from address"}"""
            val failure = assertFailsWith<MailException> { sender.send(MailMessage("ann@example.com", "Hello", "Hi")) }
            assertTrue("422" in failure.message.orEmpty() && "Invalid from address" in failure.message.orEmpty())
            assertTrue("secret-key" !in failure.message.orEmpty())
        }

    @Test
    fun `an unreachable provider is a mail failure`() =
        runBlocking {
            val dead = ResendMailSender("key", "a@b.c", baseUrl = "http://127.0.0.1:1")
            assertFailsWith<MailException> { dead.send(MailMessage("ann@example.com", "Hello", "Hi")) }
            Unit
        }
}
