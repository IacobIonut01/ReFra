/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.webdav

import com.dot.gallery.cloud.webdav.data.api.WebDavClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The WebDAV `creationdate` prop is the only provider-side capture-time hint a
 * path-based server can offer (issue #1277). These tests pin that PROPFIND asks
 * for it and that a returned value lands on [WebDavResource.creationDate] for
 * the entity mapper to store as `takenTimestamp`.
 */
class WebDavPropFindCaptureDateTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = WebDavClient(
            okHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
            baseUrl = server.url("/dav").toString(),
            username = "u",
            password = "p",
            filesEndpoint = ""
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun propFindRequestsCreationDate() {
        server.enqueue(multistatus())

        client.propFind("", depth = 1)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(
            "PROPFIND must request DAV:creationdate, was: $body",
            body.contains("creationdate")
        )
    }

    @Test
    fun creationDateIsParsedFromMultistatus() {
        server.enqueue(multistatus())

        val resources = client.propFind("", depth = 1)

        val photo = resources.single { it.displayName == "IMG_001.jpg" }
        assertEquals("2021-05-06T12:34:56Z", photo.creationDate)
    }

    @Test
    fun missingCreationDateParsesAsEmpty() {
        server.enqueue(multistatus())

        val resources = client.propFind("", depth = 1)

        val folder = resources.single { it.isCollection }
        assertEquals("", folder.creationDate)
    }

    private fun multistatus(): MockResponse = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/IMG_001.jpg</d:href>
                <d:propstat><d:prop>
                  <d:displayname>IMG_001.jpg</d:displayname>
                  <d:getcontenttype>image/jpeg</d:getcontenttype>
                  <d:getcontentlength>1234</d:getcontentlength>
                  <d:getlastmodified>Thu, 01 Jan 2026 00:00:00 GMT</d:getlastmodified>
                  <d:creationdate>2021-05-06T12:34:56Z</d:creationdate>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>"""
        )
}
