package com.dot.gallery.core

import androidx.compose.runtime.compositionLocalOf
import com.dot.gallery.feature_node.domain.model.UIEvent
import com.dot.gallery.feature_node.domain.util.EventHandler
import com.dot.gallery.feature_node.presentation.util.printError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

val LocalEventHandler = compositionLocalOf<EventHandler> {
    error("No EventHandler provided!!! This is likely due to a missing Hilt injection in the Composable hierarchy.")
}

class DefaultEventHandler(
    private val updateDatabase: (suspend () -> Unit)? = null,
    private val processScope: CoroutineScope? = updateDatabase?.let {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    },
) : EventHandler {
    // The singleton handler owns durable process work on a separate channel so navigation
    // events and Activity lifecycle changes cannot cancel a database update in progress.
    // Activity-local handlers omit updateDatabase and keep UpdateDatabase as a normal UI event.
    private val databaseUpdates = updateDatabase?.let {
        Channel<Unit>(capacity = Channel.CONFLATED)
    }

    init {
        val updates = databaseUpdates
        val update = updateDatabase
        if (updates != null && update != null) {
            processScope?.launch {
                for (ignored in updates) {
                    delay(DATABASE_UPDATE_DELAY_MS)
                    try {
                        update()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        printError("app.events", "database update failed", error)
                    }
                }
            }
        }
    }

    // Use an unlimited buffer so trySend never drops events. A zero-capacity
    // (rendezvous) channel only delivers when a receiver is suspended at that exact
    // moment; under main-thread load (e.g. constant recomposition during video
    // playback) the collector is often busy and events like NavigationUpEvent were
    // silently dropped, causing the back gesture to be ignored on videos.
    private val updater = Channel<UIEvent>(capacity = Channel.UNLIMITED)
    override val updaterFlow = updater.receiveAsFlow()
    override var navigateAction: (String) -> Unit = {}
    override var toggleNavigationBarAction: (Boolean) -> Unit = {}
    override var navigateUpAction: () -> Unit = {}
    override var setFollowThemeAction: (Boolean) -> Unit = {}

    override fun pushEvent(event: UIEvent) {
        if (event == UIEvent.UpdateDatabase && databaseUpdates != null) {
            databaseUpdates.trySend(Unit)
        } else {
            updater.trySend(event)
        }
    }

    private companion object {
        const val DATABASE_UPDATE_DELAY_MS = 1_000L
    }

}

fun EventHandler.navigateUp() = pushEvent(UIEvent.NavigationUpEvent)
fun EventHandler.navigate(route: String) = pushEvent(UIEvent.NavigationRouteEvent(route))
fun EventHandler.toggleNavigationBar(show: Boolean) = pushEvent(UIEvent.ToggleNavigationBarEvent(show))
fun EventHandler.setFollowTheme(followTheme: Boolean) = pushEvent(UIEvent.SetFollowThemeEvent(followTheme))