/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import com.dot.gallery.core.ml.SearchModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HPKG wire format + DTO parity checks — plain JVM, no Android deps.
 * The golden envelope below was produced by the server's reference writer
 * (`src/handover/envelope.py` — write_envelope) so byte-level differences
 * between the two implementations fail here.
 */
class HandoverWireTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------ envelope

    @Test
    fun writeEnvelope_matchesPythonReferenceBytes() {
        // Given the same two items the Python golden was generated with
        val items = listOf(
            HandoverEnvelope.WireItem(
                id = "42", kind = HandoverEnvelope.KIND_IMAGE, mime = "image/jpeg",
                payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
                    .plus("JPEGDATA".toByteArray()),
                timestamp = 1735689600, sourceRevision = "rev-a",
            ),
            HandoverEnvelope.WireItem(
                id = "43", kind = HandoverEnvelope.KIND_VIDEO, mime = "image/jpeg",
                payload = "frameAAframeBB".toByteArray(),
                timestamp = 1735689700, sourceRevision = "rev-b",
                frameOffsets = longArrayOf(0, 7),
            ),
        )

        // When the Kotlin writer encodes it
        val actual = HandoverEnvelope.write(
            items, engine = "eg2-q4", sink = HandoverEnvelope.SINK_BOTH,
            client = mapOf("app" to "refra-gallery"),
        )

        // Then it is byte-identical to the Python reference output
        assertArrayEquals(GOLDEN_ENVELOPE_HEX.hexToBytes(), actual)
    }

    @Test
    fun writeEnvelope_headerLayoutIsLittleEndian() {
        val bytes = HandoverEnvelope.write(
            items = listOf(
                HandoverEnvelope.WireItem("1", HandoverEnvelope.KIND_IMAGE,
                    "image/jpeg", byteArrayOf(1, 2, 3))
            ),
        )

        // magic + u16 version + u16 flags + u32 manifest length, all LE
        assertEquals('H'.code.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('K'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
        assertEquals(1, bytes[4].toInt() and 0xFF)   // version low byte
        assertEquals(0, bytes[5].toInt() and 0xFF)   // version high byte
        assertEquals(0, bytes[6].toInt() and 0xFF)   // flags = no gzip
        assertEquals(0, bytes[7].toInt() and 0xFF)
        val manifestLen = (bytes[8].toInt() and 0xFF) or
            ((bytes[9].toInt() and 0xFF) shl 8) or
            ((bytes[10].toInt() and 0xFF) shl 16) or
            ((bytes[11].toInt() and 0xFF) shl 24)
        assertEquals(bytes.size - 12 - manifestLen, 3) // payload region = 3 bytes
    }

    @Test
    fun writeEnvelope_embeddingItemCarriesDim() {
        val bytes = HandoverEnvelope.write(
            items = listOf(
                HandoverEnvelope.WireItem(
                    id = "e1", kind = HandoverEnvelope.KIND_EMBEDDING,
                    mime = "application/octet-stream",
                    payload = HandoverEnvelope.packFloat32(floatArrayOf(1f, 0f)),
                    dim = 2,
                )
            ),
            sink = HandoverEnvelope.SINK_SERVER,
        )
        val manifestLen = (bytes[8].toInt() and 0xFF) or
            ((bytes[9].toInt() and 0xFF) shl 8) or
            ((bytes[10].toInt() and 0xFF) shl 16) or
            ((bytes[11].toInt() and 0xFF) shl 24)
        val manifest = json.parseToJsonElement(
            bytes.decodeToString(12, 12 + manifestLen)
        ).jsonObject

        assertEquals("server", manifest["sink"]!!.jsonPrimitive.content)
        val first = manifest["items"]!!
            .let { json.parseToJsonElement(it.toString()) }
            .let { it.toString() }
        assertTrue(first.contains("\"dim\":2"))
    }

    // ------------------------------------------------------------ vectors

    @Test
    fun packFloat32_littleEndianLayout() {
        val packed = HandoverEnvelope.packFloat32(floatArrayOf(1f))
        // IEEE-754 LE for 1.0f = 0x0000803F → bytes 00 00 80 3F
        assertArrayEquals(byteArrayOf(0, 0, 0x80.toByte(), 0x3F), packed)
    }

    @Test
    fun wireVector_roundTrip() {
        val original = floatArrayOf(0.5f, -1.25f, 3.14159f, 0f)
        val b64 = java.util.Base64.getEncoder()
            .encodeToString(HandoverEnvelope.packFloat32(original))
        val decoded = b64.decodeWireVector(expectedDim = 4)
        assertArrayEquals(original, decoded, 1e-6f)
    }

    // ------------------------------------------------------------ DTO parsing

    @Test
    fun jobStatus_parsesServerShape() {
        val status = json.decodeFromString(
            JobStatus.serializer(),
            """{"jobId":"abc","principal":"alice","engineId":"eg2-q4","sink":"both",
               "state":"running","total":64,"done":12,"failed":0,"indexed":12,
               "etaSeconds":42,"error":null}"""
        )
        assertEquals("abc", status.jobId)
        assertEquals(12, status.done)
        assertEquals(42, status.etaSeconds)
        assertTrue(!status.isTerminal)
    }

    @Test
    fun resultsPage_parsesTerminalAndVector() {
        val page = json.decodeFromString(
            ResultsPage.serializer(),
            """{"items":[{"id":"42","status":"ok","embedding":"AACAPw==","dim":1,
               "engineId":"eg2-q4","modelRevision":"rev-1"},
              {"id":"43","status":"error","error":"decode_failed"}]}"""
        )
        assertEquals(2, page.items.size)
        assertNull(page.nextCursor)
        val vector = page.items[0].embedding!!.decodeWireVector(1)
        assertEquals(1f, vector[0], 1e-6f)
        assertEquals("decode_failed", page.items[1].error)
    }

    @Test
    fun queryResponse_parsesHits() {
        val response = json.decodeFromString(
            QueryResponse.serializer(),
            """{"items":[{"id":"17","score":0.91},{"id":"3","score":0.42}],
               "engineId":"eg2-q4","modelRevision":"r","indexRevision":"eg2-v1",
               "indexedItems":101}"""
        )
        assertEquals(2, response.items.size)
        assertEquals("17", response.items[0].id)
        assertEquals(0.91f, response.items[0].score, 1e-6f)
        assertEquals(101, response.indexedItems)
    }

    @Test
    fun handoverInfo_parsesCapabilitiesAndLimits() {
        val info = json.decodeFromString(
            HandoverInfo.serializer(),
            """{"server":"refra-handover","version":"0.3.0","protocol":1,
               "principal":"alice",
               "capabilities":{"searchProvider":true,"embeddingItems":true,"dashboard":true},
               "engines":[{"id":"eg2-q4","displayName":"EmbeddingGemma 2","engine":"eg2",
                  "appEngine":"EMBEDDING_GEMMA_2","modalities":["text","image","video"],
                  "embeddingDim":768,"indexRevisionTag":"eg2-v1","modelRevision":"rev",
                  "filesInstalled":true,"loaded":true}],
               "limits":{"maxBatchBytes":67108864,"maxBatchItems":64,"maxItemBytes":16777216,
                  "maxJobsActive":4,"resultTtlSeconds":1800},
               "auth":{"mode":"bearer"},"jobsActive":0,"itemsPending":0}"""
        )
        assertTrue(info.capabilities.searchProvider)
        assertEquals(64, info.limits.maxBatchItems)
        assertEquals("EMBEDDING_GEMMA_2", info.engines[0].appEngine)
    }

    // ------------------------------------------------------------ engine resolution

    @Test
    fun resolveHandoverEngine_matchesAppEngine() {
        val info = HandoverInfo(
            engines = listOf(
                EngineInfo(id = "clip-int8", appEngine = "CLIP",
                    indexRevisionTag = "clip-v2", embeddingDim = 512, loaded = true),
                EngineInfo(id = "eg2-q4", appEngine = "EMBEDDING_GEMMA_2",
                    indexRevisionTag = "eg2-v1", embeddingDim = 768, loaded = true),
            )
        )

        val eg2 = resolveHandoverEngine(info, SearchModel.EMBEDDING_GEMMA_2, "")
        assertNotNull(eg2)
        assertEquals("eg2-q4", eg2!!.id)

        val clip = resolveHandoverEngine(info, SearchModel.CLIP, "")
        assertNotNull(clip)
        assertEquals("clip-int8", clip!!.id)
    }

    @Test
    fun resolveHandoverEngine_overrideWins() {
        val info = HandoverInfo(
            engines = listOf(
                EngineInfo(id = "clip-int8", appEngine = "CLIP", embeddingDim = 512),
                EngineInfo(id = "custom-q8", appEngine = "CLIP", embeddingDim = 512),
            )
        )
        val picked = resolveHandoverEngine(info, SearchModel.CLIP, "custom-q8")
        assertEquals("custom-q8", picked!!.id)
        assertNull(resolveHandoverEngine(info, SearchModel.CLIP, "nonexistent"))
    }

    // ------------------------------------------------------------ remote-url policy

    @Test
    fun isUsableRemoteUrl_acceptsHttpAndHttps() {
        assertTrue(HandoverConfig.isUsableRemoteUrl("https://handover.example.com"))
        assertTrue(HandoverConfig.isUsableRemoteUrl("http://192.168.1.10:8084"))
        assertTrue(HandoverConfig.isUsableRemoteUrl(""))
        assertTrue(!HandoverConfig.isUsableRemoteUrl("handover.example.com"))
        assertTrue(!HandoverConfig.isUsableRemoteUrl("ftp://handover.example.com"))
    }

    @Test
    fun isPlainHttpUrl_flagsCleartext() {
        assertTrue(HandoverConfig.isPlainHttpUrl("http://192.168.1.10:8084"))
        assertTrue(!HandoverConfig.isPlainHttpUrl("https://handover.example.com"))
        assertTrue(!HandoverConfig.isPlainHttpUrl(""))
    }

    @Test
    fun configured_acceptsPlainHttpRemoteUrl() {
        fun config(url: String) = HandoverConfig(
            enabled = true, remoteUrl = url, localUrl = "", localWifiSsid = "",
            autoUrlSwitch = false, secret = "s", engine = "",
            sink = HandoverEnvelope.SINK_BOTH, preferRemoteQueries = true,
        )
        assertTrue(config("https://handover.example.com").configured)
        assertTrue(config("http://192.168.1.10:8084").configured)
        assertTrue(config("http://192.168.1.10:8084").remoteUrlPlainHttp)
        assertTrue(!config("").configured)
        assertTrue(!config("handover.example.com").configured)
    }

    // ------------------------------------------------------------ hmac signing

    @Test
    fun hmacSignature_matchesServerCanonicalForm() {
        // Golden vector produced by the server's own signed-message
        // construction (auth.py): ts\nnonce\nMETHOD\npath?query\nsha256(body).
        val sig = HandoverHmac.signature(
            secret = SECRET,
            tsSeconds = "1700000000",
            nonce = "a1b2c3d4e5f60718293a4b5c6d7e8f90",
            method = "POST",
            target = "/api/v1/query?engine=eg2-q4",
            body = "{\"text\":\"a photo of a cat\"}".encodeToByteArray(),
        )
        assertEquals(
            "8913bb8cfaa07c62d4180d39c9a849b2db62419cc3eedbba8a2b981c3720a6a1",
            sig
        )
    }

    @Test
    fun hmacSignature_emptyBodyGet() {
        val sig = HandoverHmac.signature(
            secret = SECRET,
            tsSeconds = "1700000000",
            nonce = "a1b2c3d4e5f60718293a4b5c6d7e8f90",
            method = "GET",
            target = "/api/v1/info",
            body = ByteArray(0),
        )
        assertEquals(
            "dc89d753b2c8794d863c00e940e24fb0b3d474009814bbfd4ba6d69121aac3ba",
            sig
        )
    }

    @Test
    fun hmacHeaders_carryFreshTsNonceSignature() {
        val headers = HandoverHmac.headers(
            secret = SECRET, method = "GET", target = "/api/v1/info",
            body = ByteArray(0),
        )
        val ts = headers[HandoverHmac.HEADER_TS]!!.toLong()
        assertTrue(kotlin.math.abs(System.currentTimeMillis() / 1000 - ts) < 60)
        assertEquals(32, headers[HandoverHmac.HEADER_NONCE]!!.length)
        // The same ts+nonce must reproduce the attached signature.
        val recomputed = HandoverHmac.signature(
            SECRET, headers[HandoverHmac.HEADER_TS]!!,
            headers[HandoverHmac.HEADER_NONCE]!!, "GET", "/api/v1/info",
            ByteArray(0),
        )
        assertEquals(recomputed, headers[HandoverHmac.HEADER_SIGNATURE])
    }

    // ------------------------------------------------------------ helpers

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val SECRET =
            "b3f1c9a84d2e7f05a1c3e6b8d9f0a2c4e6b8d0f2a4c6e8b0d2f4a6c8e0b2d4f6"

        /** Frozen output of Python `write_envelope` for the two fixed items —
         *  engine="eg2-q4", sink="both", client={"app":"refra-gallery"}. */
        const val GOLDEN_ENVELOPE_HEX =
            "48504b47010000005d0100007b22656e67696e65223a226567322d7134222c" +
            "22636c69656e74223a7b22617070223a2272656672612d67616c6c657279227d" +
            "2c2273696e6b223a22626f7468222c226974656d73223a5b7b226964223a22" +
            "3432222c226b696e64223a22696d616765222c226d696d65223a22696d616765" +
            "2f6a706567222c226f6666736574223a302c226c656e677468223a31302c22" +
            "74696d657374616d70223a313733353638393630302c22736f757263655265" +
            "766973696f6e223a227265762d61227d2c7b226964223a223433222c226b69" +
            "6e64223a22766964656f222c226d696d65223a22696d6167652f6a70656722" +
            "2c226f6666736574223a31302c226c656e677468223a31342c2274696d6573" +
            "74616d70223a313733353638393730302c22736f757263655265766973696f" +
            "6e223a227265762d62222c226672616d65436f756e74223a322c226672616d" +
            "654f666673657473223a5b302c375d7d5d7dffd84a50454744415441667261" +
            "6d6541416672616d654242"
    }
}
