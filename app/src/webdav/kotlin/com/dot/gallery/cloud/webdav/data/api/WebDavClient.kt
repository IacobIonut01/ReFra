/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.webdav.data.api

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xml.sax.InputSource
import java.io.File
import java.io.InterruptedIOException
import java.io.StringReader
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavResource(
    val href: String,
    val displayName: String = "",
    val contentType: String = "",
    val contentLength: Long = 0L,
    val lastModified: String = "",
    val etag: String = "",
    val isCollection: Boolean = false,
    val fileId: String = "",
    val favorite: Boolean = false,
    val creationDate: String = ""
)

/**
 * Generic WebDAV (RFC 4918) client. Server-specific behavior (preview URLs,
 * favorites semantics, OCS endpoints) lives in [com.dot.gallery.cloud.webdav.WebDavDialect].
 *
 * The [filesEndpoint] is the path (relative to [baseUrl]) that hosts the user's
 * files collection — e.g. `/remote.php/dav/files/{user}` for ownCloud/Nextcloud,
 * or an empty string for a server whose root already is the WebDAV collection.
 */
class WebDavClient(
    private val okHttpClient: OkHttpClient,
    baseUrl: String,
    private val username: String,
    private val password: String,
    filesEndpoint: String
) {
    private val credentials = Credentials.basic(username, password)
    private val normalizedBase = baseUrl.trimEnd('/')

    val webDavBaseUrl: String = if (filesEndpoint.isEmpty()) {
        normalizedBase
    } else {
        normalizedBase + "/" + filesEndpoint.trimStart('/')
    }

    /**
     * Path prefix that PROPFIND hrefs carry and that should be stripped to get a
     * relative path. Derived from the FULL files-collection URL so a server hosted
     * under a sub-path (e.g. `https://host/owncloud` + `/remote.php/dav/files/user`)
     * strips the whole `/owncloud/remote.php/dav/files/user` prefix — using only
     * [filesEndpoint] here would miss the `/owncloud` base path and leave hrefs
     * unstripped, producing doubled album paths that fetch nothing.
     */
    private val rootPath: String =
        webDavBaseUrl.toHttpUrlOrNull()?.encodedPath?.trimEnd('/').orEmpty()

    /** Converts a server-returned (possibly percent-encoded) href into a decoded path relative to the files collection. */
    fun relativePath(href: String): String {
        val encodedPath = if (href.startsWith("http", ignoreCase = true)) {
            href.toHttpUrlOrNull()?.encodedPath ?: href
        } else href
        val rel = if (rootPath.isNotEmpty() && encodedPath.startsWith(rootPath)) {
            encodedPath.substring(rootPath.length)
        } else encodedPath
        // Decode percent-encoding by round-tripping through a throwaway HttpUrl.
        val decoded = "http://x/${rel.trimStart('/')}".toHttpUrlOrNull()
            ?.pathSegments?.joinToString("/") ?: rel.trimStart('/')
        return decoded.trimStart('/')
    }

    fun propFind(remotePath: String, depth: Int = 1): List<WebDavResource> {
        val url = buildUrl(remotePath)
        val propFindBody = """<?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns" xmlns:nc="http://nextcloud.org/ns">
                <d:prop>
                    <d:displayname/>
                    <d:getcontenttype/>
                    <d:getcontentlength/>
                    <d:getlastmodified/>
                    <d:creationdate/>
                    <d:getetag/>
                    <d:resourcetype/>
                    <oc:fileid/>
                    <oc:favorite/>
                </d:prop>
            </d:propfind>""".trimIndent()

        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", propFindBody.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", credentials)
            .header("Depth", depth.toString())
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("PROPFIND", response.code, response.message)
            }
            parsePropFindResponse(response.body.string())
        }
    }

    fun download(remotePath: String): ByteArray {
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .get()
            .header("Authorization", credentials)
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebDavException("Download", response.code, response.message)
            response.body.bytes()
        }
    }

    fun sha1(remotePath: String): String {
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .get()
            .header("Authorization", credentials)
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebDavException("Checksum", response.code, response.message)
            val digest = MessageDigest.getInstance("SHA-1")
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Checksum cancelled")
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    fun upload(
        remotePath: String,
        file: File,
        contentType: String = "application/octet-stream",
        overwrite: Boolean = true
    ) {
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .put(file.asRequestBody(contentType.toMediaType()))
            .header("Authorization", credentials)
            .apply { if (!overwrite) header("If-None-Match", "*") }
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebDavException("Upload", response.code, response.message)
        }
    }

    fun mkdir(remotePath: String) {
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .method("MKCOL", null)
            .header("Authorization", credentials)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) { // 405 = already exists
                throw WebDavException("MKCOL", response.code, response.message)
            }
        }
    }

    /**
     * Ensures every ancestor collection of [remotePath] exists by MKCOL'ing each
     * path segment in turn. WebDAV servers reject a PUT into a missing collection
     * (commonly 403 or 409), so uploads must create the target folder chain first.
     * MKCOL is idempotent here — an already-existing collection returns 405 and is
     * ignored by [mkdir]; any other failure is swallowed so it surfaces on the PUT.
     */
    fun ensureParentCollections(remotePath: String) {
        val segments = remotePath.trim('/').split('/').filter { it.isNotEmpty() }
        if (segments.size <= 1) return // file sits at the collection root; no parent to create
        var current = ""
        for (i in 0 until segments.size - 1) {
            current = if (current.isEmpty()) segments[i] else "$current/${segments[i]}"
            runCatching { mkdir(current) }
        }
    }

    fun delete(remotePath: String) {
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .delete()
            .header("Authorization", credentials)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404 && response.code != 410) {
                throw WebDavException("DELETE", response.code, response.message)
            }
        }
    }

    /** Toggle a favorite flag via PROPPATCH (`oc:favorite`). Supported by ownCloud/Nextcloud. */
    fun setFavorite(remotePath: String, favorite: Boolean) {
        val body = """<?xml version="1.0"?>
            <d:propertyupdate xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                <d:set>
                    <d:prop>
                        <oc:favorite>${if (favorite) 1 else 0}</oc:favorite>
                    </d:prop>
                </d:set>
            </d:propertyupdate>""".trimIndent()
        val request = Request.Builder()
            .url(buildUrl(remotePath))
            .method("PROPPATCH", body.toRequestBody("application/xml".toMediaType()))
            .header("Authorization", credentials)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw WebDavException("PROPPATCH", response.code, response.message)
        }
    }

    fun checkConnection() {
        propFind("", depth = 0)
    }

    fun getDownloadUrl(remotePath: String): String = buildUrl(remotePath)

    fun getAuthHeaders(): Map<String, String> = mapOf("Authorization" to credentials)

    private fun buildUrl(remotePath: String): String =
        "$webDavBaseUrl/${remotePath.trimStart('/')}"

    private fun parsePropFindResponse(xml: String): List<WebDavResource> {
        if (xml.isBlank()) return emptyList()
        val resources = mutableListOf<WebDavResource>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val responses = doc.getElementsByTagNameNS("DAV:", "response")
            for (i in 0 until responses.length) {
                val responseEl = responses.item(i)
                val href = getTextContent(responseEl, "DAV:", "href") ?: continue
                val displayName = getTextContent(responseEl, "DAV:", "displayname") ?: ""
                val contentType = getTextContent(responseEl, "DAV:", "getcontenttype") ?: ""
                val contentLength = getTextContent(responseEl, "DAV:", "getcontentlength")?.toLongOrNull() ?: 0L
                val lastModified = getTextContent(responseEl, "DAV:", "getlastmodified") ?: ""
                val creationDate = getTextContent(responseEl, "DAV:", "creationdate") ?: ""
                val etag = getTextContent(responseEl, "DAV:", "getetag") ?: ""
                val fileId = getTextContent(responseEl, "http://owncloud.org/ns", "fileid") ?: ""
                val favorite = (getTextContent(responseEl, "http://owncloud.org/ns", "favorite") ?: "0") == "1"

                val isCollection = (responseEl as? org.w3c.dom.Element)?.let { el ->
                    val propStats = el.getElementsByTagNameNS("DAV:", "propstat")
                    var isCol = false
                    for (j in 0 until propStats.length) {
                        val prop = (propStats.item(j) as? org.w3c.dom.Element)
                            ?.getElementsByTagNameNS("DAV:", "prop")?.item(0) as? org.w3c.dom.Element
                        val resourceType = prop?.getElementsByTagNameNS("DAV:", "resourcetype")?.item(0) as? org.w3c.dom.Element
                        if (resourceType != null) {
                            isCol = resourceType.getElementsByTagNameNS("DAV:", "collection").length > 0
                        }
                    }
                    isCol
                } ?: false

                resources.add(
                    WebDavResource(
                        href = href,
                        displayName = displayName.ifBlank { href.trimEnd('/').substringAfterLast('/') },
                        contentType = contentType,
                        contentLength = contentLength,
                        lastModified = lastModified,
                        creationDate = creationDate,
                        etag = etag,
                        isCollection = isCollection,
                        fileId = fileId,
                        favorite = favorite
                    )
                )
            }
        } catch (_: Exception) {
            // Fallback for malformed XML
        }
        return resources
    }

    private fun getTextContent(parent: org.w3c.dom.Node, namespaceUri: String, localName: String): String? {
        if (parent is org.w3c.dom.Element) {
            val nodes = parent.getElementsByTagNameNS(namespaceUri, localName)
            if (nodes.length > 0) return nodes.item(0).textContent
        }
        return null
    }
}

class WebDavException(
    val operation: String,
    val statusCode: Int,
    statusMessage: String
) : Exception("$operation failed: $statusCode $statusMessage")
