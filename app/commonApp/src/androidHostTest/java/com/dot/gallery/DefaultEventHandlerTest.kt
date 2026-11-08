package com.dot.gallery

import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.navigateUp
import com.dot.gallery.domain.model.UIEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import com.dot.gallery.core.workers.updateDatabase



/**
 * Regression tests for [DefaultEventHandler].
 *
 * the back gesture was ignored on videos when controls were hidden.
 * The media viewer's back handler pushes a [UIEvent.NavigationUpEvent] through the
 * event handler. The handler previously used a zero-capacity (rendezvous) channel,
 * whose `trySend` only succeeds when a receiver is suspended at that exact moment.
 * During video playback the main thread is busy with constant recomposition, so the
 * collector was frequently not waiting and the navigation event was silently dropped.
 *
 * The channel is now an unlimited buffer, so events queued before/while no receiver
 * is suspended are still delivered.
 */
class DefaultEventHandlerTest {

    @Test
    fun deliversEventsPushedBeforeAnyReceiverIsSuspended() = runBlocking {
        val handler = DefaultEventHandler()

        // Push events before anyone starts collecting. With a rendezvous channel
        // these would be dropped by trySend; with a buffered channel they survive.
        handler.navigateUp()
        handler.pushEvent(UIEvent.UpdateDatabase)
        handler.navigateUp()

        val received = withTimeout(2_000) {
            handler.updaterFlow.take(3).toList()
        }

        assertEquals(
            listOf(
                UIEvent.NavigationUpEvent,
                UIEvent.UpdateDatabase,
                UIEvent.NavigationUpEvent
            ),
            received
        )
    }

    @Test
    fun databaseUpdateIsIndependentFromLaterUiEvents() = runTest {
        var databaseUpdateCount = 0
        val handler = DefaultEventHandler(
            updateDatabase = { databaseUpdateCount++ },
            processScope = backgroundScope,
        )

        handler.pushEvent(UIEvent.UpdateDatabase)
        handler.navigateUp()

        assertEquals(UIEvent.NavigationUpEvent, handler.updaterFlow.first())
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, databaseUpdateCount)
    }

    @Test
    fun preservesEventOrderForBurstOfEvents() = runBlocking {
        val handler = DefaultEventHandler()
        val routes = (1..50).map { "route/$it" }

        routes.forEach { handler.pushEvent(UIEvent.NavigationRouteEvent(it)) }

        val received = withTimeout(2_000) {
            handler.updaterFlow.take(routes.size).toList()
        }

        assertEquals(
            routes,
            received.map { (it as UIEvent.NavigationRouteEvent).route }
        )
    }
}
