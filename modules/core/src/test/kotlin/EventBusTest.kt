package dev.nilsschlegel.forum.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class EventBusTest {
    private data class Greeted(
        val name: String,
    )

    private data class Left(
        val name: String,
    )

    @Test
    fun `handlers get the events of their type, in the order they subscribed`() =
        runBlocking {
            val bus = EventBus()
            val seen = mutableListOf<String>()
            bus.on<Greeted> { seen += "first:${it.name}" }
            bus.on<Greeted> { seen += "second:${it.name}" }
            bus.on<Left> { seen += "left:${it.name}" }

            bus.publish(Greeted("ann"))
            bus.publish(Left("bob"))

            assertEquals(listOf("first:ann", "second:ann", "left:bob"), seen)
        }

    @Test
    fun `a failing handler does not stop the others or the publisher`() =
        runBlocking {
            val bus = EventBus()
            val seen = mutableListOf<String>()
            bus.on<Greeted> { error("broken") }
            bus.on<Greeted> { seen += it.name }

            bus.publish(Greeted("ann"))

            assertEquals(listOf("ann"), seen)
        }

    @Test
    fun `events with no handler are ignored`() =
        runBlocking {
            EventBus().publish(Greeted("nobody listens"))
        }
}
