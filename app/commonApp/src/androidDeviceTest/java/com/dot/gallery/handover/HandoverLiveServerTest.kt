/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.CredentialEncryptor
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end contract check against a **live** ReFra-Handover server: HPKG
 * submit → job polling → results → server-side index → /query → /index/delete.
 * This is the M5 device gate — the same Python process the app talks to in
 * production, not a mock.
 *
 * Run a local instance first (any reachable address; the models just need to
 * be installed):
 *   cd ReFra-Handover && HANDOVER_MODEL_DIR=<dir-with-eg2> \
 *     HANDOVER_SECRETS_FILE=<secrets.json> \
 *     .venv/bin/python -m handover serve --host 0.0.0.0 --port 8092
 *
 * Then:
 *   ./gradlew :app:commonApp:connectedAndroidDeviceTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.dot.gallery.handover.HandoverLiveServerTest
 *
 * Emulator → host goes through `adb reverse` (default `http://127.0.0.1:8092`;
 * override via runner args `handoverUrl` / `handoverSecret`):
 *   adb reverse tcp:8092 tcp:8092
 * `10.0.2.2` also works when the host firewall allows inbound to the port.
 * Skips cleanly when the server is down. The https-for-remote policy is
 * enforced on the settings/`config()` path — this test builds the config
 * object directly, so the loopback http URL is allowed.
 *
 * The `hmacMode_signedRequestAccepted` case additionally needs a second
 * instance running with HANDOVER_AUTH_MODE=hmac, passed via the runner arg
 * `handoverHmacUrl` (e.g. `--port 8093` + `adb reverse tcp:8093 tcp:8093`);
 * without it that case skips.
 */
@RunWith(AndroidJUnit4::class)
class HandoverLiveServerTest {

    private val args = InstrumentationRegistry.getArguments()
    private val baseUrl = args.getString("handoverUrl") ?: DEFAULT_URL
    private val secret = args.getString("handoverSecret") ?: DEFAULT_SECRET

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val client = HandoverClient(context, CredentialEncryptor())
    private val config = HandoverConfig(
        enabled = true,
        remoteUrl = baseUrl,
        localUrl = "",
        localWifiSsid = "",
        autoUrlSwitch = false,
        secret = secret,
        engine = "",
        sink = HandoverEnvelope.SINK_BOTH,
        preferRemoteQueries = false,
    )

    @Before
    fun assumeServerReachable() {
        val reachable = runCatching {
            runBlocking { client.info(config) }
        }.getOrNull()
        assumeTrue("no handover server at $baseUrl — test skipped", reachable != null)
    }

    @Test
    fun info_advertisesLoadedEngineAndLimits() = runBlocking {
        val info = client.info(config)
        assertTrue("protocol v1 expected, got ${info.protocol}", info.protocol >= 1)
        assertTrue(
            "no engine loaded server-side",
            info.engines.any { it.loaded && it.embeddingDim > 0 },
        )
        assertTrue(info.limits.maxBatchItems >= 1)
        assertTrue(info.limits.maxItemBytes > 0)
        assertEquals(secretOwner(), info.principal)
    }

    @Test
    fun envelope_roundTrip_indexesQueriesAndDeletes() = runBlocking {
        val itemId = "live-test-${System.currentTimeMillis()}"
        val jpeg = makeJpeg(64, 64)
        val envelope = HandoverEnvelope.write(
            items = listOf(
                HandoverEnvelope.WireItem(
                    id = itemId,
                    kind = HandoverEnvelope.KIND_IMAGE,
                    mime = "image/jpeg",
                    payload = jpeg,
                    timestamp = System.currentTimeMillis() / 1000L,
                    sourceRevision = "live-test",
                ),
            ),
            sink = HandoverEnvelope.SINK_SERVER,
            client = mapOf("app" to "device-test"),
        )

        // submit → accept exactly one item
        val submitted = client.submit(config, envelope)
        assertTrue("jobId missing", submitted.jobId.isNotBlank())
        assertEquals(1, submitted.accepted)
        assertTrue(submitted.rejected.isEmpty())

        // poll to terminal
        val status = awaitTerminal(submitted.jobId)
        assertEquals("done", status.state)
        assertEquals(1, status.done)
        assertEquals(1, status.indexed)

        // results carry the item without an error
        val page = client.results(config, submitted.jobId)
        val item = page.items.firstOrNull { it.id == itemId }
        assertNotNull("item absent from results page", item)
        assertTrue(item!!.status == "ok" || item.status == "indexed")

        // server-side index answers "more like this" with the item itself
        val hits = client.query(config, QueryRequest(itemId = itemId, k = 5))
        assertTrue("indexed item absent from /query", hits.items.any { it.id == itemId })
        val self = hits.items.first { it.id == itemId }
        assertTrue("self-hit should top the ranking", self.score >= 0.99f)

        // delete propagation — mirror of the app's MediaHandlerImpl hook
        val removed = client.indexDelete(config, listOf(itemId))
        assertEquals(1, removed)
        // After removal the id can't resolve server-side: /query{itemId}
        // answers 404 bad_envelope rather than an empty ranking.
        try {
            client.query(config, QueryRequest(itemId = itemId, k = 5))
            fail("deleted id still resolves in /query")
        } catch (e: HandoverException) {
            assertEquals("not_indexed", e.code)
        }
    }

    @Test
    fun hmacMode_signedRequestAccepted() = runBlocking {
        val hmacUrl = args.getString("handoverHmacUrl")
        assumeTrue("no hmac-mode server configured — test skipped", !hmacUrl.isNullOrBlank())
        val signed = config.copy(remoteUrl = hmacUrl!!, authMode = HandoverConfig.AUTH_HMAC)
        val info = client.info(signed)
        assertTrue(info.protocol >= 1)
        assertEquals(secretOwner(), info.principal)
        // A bearer request must be refused by the same server.
        try {
            client.info(config.copy(remoteUrl = hmacUrl))
            fail("bearer request accepted by an hmac-mode server")
        } catch (e: HandoverException) {
            assertEquals("unauthorized", e.code)
        }
        Unit
    }

    @Test
    fun badSecret_rejectedWithUnauthorized() = runBlocking {
        val bad = config.copy(secret = "wrong-secret")
        try {
            client.info(bad)
            fail("expected HandoverException for bad secret")
        } catch (e: HandoverException) {
            assertEquals("unauthorized", e.code)
            assertTrue(!e.retryable)
        }
        Unit
    }

    // ------------------------------------------------------------------ util

    private suspend fun awaitTerminal(jobId: String): JobStatus {
        val deadline = System.currentTimeMillis() + JOB_TIMEOUT_MS
        while (true) {
            val s = client.status(config, jobId)
            if (s.isTerminal) return s
            check(System.currentTimeMillis() < deadline) {
                "job $jobId not terminal after ${JOB_TIMEOUT_MS}ms (state=${s.state})"
            }
            delay(POLL_MS)
        }
    }

    private fun makeJpeg(w: Int, h: Int): ByteArray {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF3A7BD5.toInt())
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }

    /** alice's principal name as the server knows it (secrets file key). */
    private fun secretOwner() = args.getString("handoverPrincipal") ?: "alice"

    private companion object {
        const val DEFAULT_URL = "http://127.0.0.1:8092"
        const val DEFAULT_SECRET = "alice-secret-live"
        const val POLL_MS = 400L
        const val JOB_TIMEOUT_MS = 120_000L
    }
}
