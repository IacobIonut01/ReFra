/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.content.Context
import com.dot.gallery.cloud.core.CredentialEncryptor
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printWarn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Typed error from a Handover call. [code] is the server's protocol error code
 * ("unauthorized", "queue_full", …) or "network"/"http_<status>" when no
 * protocol body came back. [retryable] mirrors the server's flag — callers
 * should back off and retry rather than mark media failed.
 */
class HandoverException(
    val code: String,
    message: String,
    val retryable: Boolean = false,
    val httpStatus: Int = 0,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * HTTP client for a ReFra Handover server (docs/protocol.md).
 *
 * Config is re-read per call — URL switches (local ↔ remote) and credential
 * changes take effect immediately. All calls run on [Dispatchers.IO].
 */
@Singleton
class HandoverClient @Inject constructor(
    private val context: Context,
    private val encryptor: CredentialEncryptor,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** Envelopes can be tens of MB on slow uplinks — generous write timeout. */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    /** Resolved config, or null when remote inference is off/unconfigured. */
    suspend fun config(): HandoverConfig? =
        HandoverConfig.load(context) { encryptor.decryptOrNull(it) ?: "" }
            .takeIf { it.configured }

    // ------------------------------------------------------------------ info

    suspend fun info(config: HandoverConfig): HandoverInfo =
        get(config, "/api/v1/info", HandoverInfo.serializer())

    /**
     * Probes the given base URL + secret without touching saved settings —
     * used by the settings screen's "Test connection" action.
     */
    suspend fun testConnection(
        baseUrl: String,
        secret: String,
        authMode: String = HandoverConfig.AUTH_BEARER,
    ): HandoverInfo {
        if (!HandoverConfig.isUsableRemoteUrl(baseUrl.trim())) {
            throw HandoverException(
                "insecure_url",
                "Server URL must start with http:// or https://",
                retryable = false)
        }
        return execute(secret, authMode, Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/info").get().build(), EMPTY_BODY)
            .let { json.decodeFromString(HandoverInfo.serializer(), it) }
    }

    // ------------------------------------------------------------------ jobs

    /** POST an HPKG envelope; returns the accepted job id + per-item rejections. */
    suspend fun submit(config: HandoverConfig, envelope: ByteArray): SubmitResponse {
        val base = config.effectiveUrl(context)
        return execute(config.secret, config.authMode, Request.Builder()
            .url("$base/api/v1/jobs")
            .post(envelope.toRequestBody(ENVELOPE_MIME)).build(), envelope)
            .let { json.decodeFromString(SubmitResponse.serializer(), it) }
    }

    suspend fun status(config: HandoverConfig, jobId: String): JobStatus =
        get(config, "/api/v1/jobs/$jobId", JobStatus.serializer())

    suspend fun results(
        config: HandoverConfig, jobId: String, cursor: Int = 0, limit: Int = 256
    ): ResultsPage =
        get(config, "/api/v1/jobs/$jobId/results?cursor=$cursor&limit=$limit",
            ResultsPage.serializer())

    suspend fun cancel(config: HandoverConfig, jobId: String) {
        val base = config.effectiveUrl(context)
        execute(config.secret, config.authMode,
            Request.Builder().url("$base/api/v1/jobs/$jobId").delete().build(),
            EMPTY_BODY)
    }

    // --------------------------------------------------------------- search

    /** Server-side index query — text, raw vector, or "similar to indexed item". */
    suspend fun query(config: HandoverConfig, request: QueryRequest): QueryResponse {
        val base = config.effectiveUrl(context)
        val body = json.encodeToString(QueryRequest.serializer(), request)
            .encodeToByteArray()
        return execute(config.secret, config.authMode,
            Request.Builder().url("$base/api/v1/query")
                .post(body.toRequestBody(JSON_MIME)).build(), body)
            .let { json.decodeFromString(QueryResponse.serializer(), it) }
    }

    /** Synchronous text embedding (interactive search path). */
    suspend fun embedText(
        config: HandoverConfig, text: String, engine: String? = null
    ): EmbedResponse {
        val base = config.effectiveUrl(context)
        val body = buildJsonObject {
            put("text", text)
            if (engine != null) put("engine", engine)
        }.toString().encodeToByteArray()
        return execute(config.secret, config.authMode,
            Request.Builder().url("$base/api/v1/embed/text")
                .post(body.toRequestBody(JSON_MIME)).build(), body)
            .let { json.decodeFromString(EmbedResponse.serializer(), it) }
    }

    /** Removes ids from the caller's server-side index namespace. */
    suspend fun indexDelete(config: HandoverConfig, ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val base = config.effectiveUrl(context)
        val body = buildJsonObject {
            put("ids", kotlinx.serialization.json.JsonArray(
                ids.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }.toString().encodeToByteArray()
        val raw = execute(config.secret, config.authMode,
            Request.Builder().url("$base/api/v1/index/delete")
                .post(body.toRequestBody(JSON_MIME)).build(), body)
        // {"removed": {"<engineId>": <count>, …}} — total across engines
        return json.parseToJsonElement(raw).jsonObject["removed"]?.jsonObject
            ?.values?.sumOf { it.jsonPrimitive.content.toIntOrNull() ?: 0 } ?: 0
    }

    // --------------------------------------------------------------- internals

    private suspend fun <T> get(
        config: HandoverConfig,
        path: String,
        serializer: DeserializationStrategy<T>,
    ): T {
        val base = config.effectiveUrl(context)
        return execute(config.secret, config.authMode,
            Request.Builder().url("$base$path").get().build(), EMPTY_BODY)
            .let { json.decodeFromString(serializer, it) }
    }

    private suspend fun execute(
        secret: String,
        authMode: String,
        request: Request,
        bodyBytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request.newBuilder().apply {
                if (authMode == HandoverConfig.AUTH_HMAC) {
                    val target = request.url.encodedPath +
                        (request.url.encodedQuery?.let { "?$it" } ?: "")
                    HandoverHmac.headers(secret, request.method, target, bodyBytes)
                        .forEach { (name, value) -> header(name, value) }
                } else {
                    header("Authorization", "Bearer $secret")
                }
                header("Accept", "application/json")
            }.build()).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw parseError(response.code, body)
                }
                body
            }
        } catch (e: HandoverException) {
            throw e
        } catch (e: java.io.IOException) {
            throw HandoverException("network", e.message ?: "network error",
                retryable = true, cause = e)
        }
    }

    private fun parseError(status: Int, body: String): HandoverException {
        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
        }.onFailure {
            printWarn(
                "handover.client",
                "HTTP $status error body is not the protocol envelope (${body.length} chars)",
            )
        }.getOrNull()
        val code = parsed?.get("code")?.jsonPrimitive?.content ?: "http_$status"
        val message = parsed?.get("message")?.jsonPrimitive?.content ?: "HTTP $status"
        val retryable = parsed?.get("retryable")?.jsonPrimitive?.content
            ?.toBooleanStrictOrNull() ?: status in RETRYABLE_STATUSES
        return HandoverException(code, message, retryable, status)
    }

    private companion object {
        val ENVELOPE_MIME = "application/vnd.refra.handover+hpkg".toMediaType()
        val JSON_MIME = "application/json".toMediaType()
        val EMPTY_BODY = ByteArray(0)
        val RETRYABLE_STATUSES = setOf(408, 429, 500, 502, 503, 504)
    }
}
