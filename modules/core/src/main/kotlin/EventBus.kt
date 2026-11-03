package dev.nilsschlegel.forum.core

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * A small in-process event bus. A module publishes a fact about something that happened, such as a new post, and other
 * modules react without the publisher knowing about them.
 *
 * Handlers run one after another on the publisher's coroutine, so they should be quick. A handler that fails is logged
 * and never fails the publisher or the other handlers. Events are not stored, so a restart loses what was in flight.
 * Move to a queue if delivery ever has to survive that.
 */
class EventBus {
    private val log = LoggerFactory.getLogger(EventBus::class.java)
    private val handlers = CopyOnWriteArrayList<Pair<KClass<*>, suspend (Any) -> Unit>>()

    fun <T : Any> subscribe(
        type: KClass<T>,
        handler: suspend (T) -> Unit,
    ) {
        @Suppress("UNCHECKED_CAST")
        handlers += type to (handler as suspend (Any) -> Unit)
    }

    inline fun <reified T : Any> on(noinline handler: suspend (T) -> Unit) = subscribe(T::class, handler)

    suspend fun publish(event: Any) {
        for ((type, handler) in handlers) {
            if (!type.isInstance(event)) continue
            try {
                handler(event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                log.warn("A handler for {} failed", event::class.simpleName, failure)
            }
        }
    }
}
