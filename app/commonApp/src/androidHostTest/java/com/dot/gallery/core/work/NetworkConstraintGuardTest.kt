package com.dot.gallery.core.work

import dev.brewkits.kmpworkmanager.background.domain.Constraints
import dev.brewkits.kmpworkmanager.background.domain.ExistingPolicy
import dev.brewkits.kmpworkmanager.background.domain.TaskRequest
import dev.brewkits.kmpworkmanager.background.domain.TaskTrigger
import dev.brewkits.kmpworkmanager.testing.FakeBackgroundTaskScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline-variant regression: the merged manifest strips ACCESS_NETWORK_STATE, and
 * androidx.work throws SecurityException reading ConnectivityManager for any WorkSpec
 * carrying a network constraint. [ConstraintStrippingScheduler] must therefore clear
 * `requiresNetwork` / `requiresUnmeteredNetwork` on every scheduling path (enqueue and
 * chain beginWith) so no network-constrained WorkSpec is ever persisted.
 */
class NetworkConstraintGuardTest {

    @Test
    fun enqueueDropsNetworkFlags() = runTest {
        val fake = FakeBackgroundTaskScheduler()
        val scheduler = ConstraintStrippingScheduler(fake)

        scheduler.enqueue(
            id = "cloud_sync",
            trigger = TaskTrigger.Periodic(intervalMs = 900_000),
            workerClassName = "CloudSyncWorker",
            constraints = Constraints(
                requiresNetwork = true,
                requiresUnmeteredNetwork = true,
            ),
            policy = ExistingPolicy.UPDATE,
        )

        val constraints = fake.enqueuedTasks.single().constraints
        assertFalse(constraints.requiresNetwork)
        assertFalse(constraints.requiresUnmeteredNetwork)
    }

    @Test
    fun enqueueKeepsNonNetworkConstraints() = runTest {
        val fake = FakeBackgroundTaskScheduler()
        val scheduler = ConstraintStrippingScheduler(fake)

        scheduler.enqueue(
            id = "backup",
            trigger = TaskTrigger.OneTime(),
            workerClassName = "CloudUploadWorker",
            constraints = Constraints(
                requiresNetwork = true,
                requiresCharging = true,
                isHeavyTask = true,
                maxRetries = 2,
            ),
        )

        val constraints = fake.enqueuedTasks.single().constraints
        assertFalse(constraints.requiresNetwork)
        assertTrue(constraints.requiresCharging)
        assertTrue(constraints.isHeavyTask)
        assertEquals(2, constraints.maxRetries)
    }

    @Test
    fun unconstrainedTaskPassesThrough() = runTest {
        val fake = FakeBackgroundTaskScheduler()
        val scheduler = ConstraintStrippingScheduler(fake)

        scheduler.enqueue(
            id = "capture_index",
            trigger = TaskTrigger.OneTime(),
            workerClassName = "CaptureTimeIndexWorker",
            constraints = Constraints(requiresCharging = false),
        )

        val constraints = fake.enqueuedTasks.single().constraints
        assertFalse(constraints.requiresNetwork)
        assertFalse(constraints.requiresUnmeteredNetwork)
        assertFalse(constraints.requiresCharging)
    }

    @Test
    fun withoutNetworkFlagsClearsBothFlags() {
        val cleared = Constraints(requiresNetwork = true, requiresUnmeteredNetwork = true)
            .withoutNetworkFlags()

        assertFalse(cleared.requiresNetwork)
        assertFalse(cleared.requiresUnmeteredNetwork)
    }

    @Test
    fun withoutNetworkFlagsIsIdentityForOtherConstraints() {
        val original = Constraints(requiresCharging = true, maxRetries = 3)

        assertTrue(original.withoutNetworkFlags() === original)
    }

    @Test
    fun chainRequestDropsNetworkFlags() {
        // beginWith applies this same TaskRequest-level sanitization to every chain step.
        val request = TaskRequest(
            workerClassName = "ModelDownloadWorker",
            constraints = Constraints(
                requiresNetwork = true,
                isHeavyTask = true,
            ),
        ).withoutNetworkFlags()

        assertFalse(request.constraints?.requiresNetwork == true)
        assertTrue(request.constraints?.isHeavyTask == true)
        assertEquals("ModelDownloadWorker", request.workerClassName)
    }
}
