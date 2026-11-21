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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import com.dot.gallery.cloud.webdav.data.api.WebDavResource


/**
 * The WebDAV `creationdate` prop is the only provider-side capture-time hint a
 * path-based server can offer. These tests pin that PROPFIND asks
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

    @Test
    fun photoCaptureTimeRequestedOnlyWhenEnabled() {
        server.enqueue(multistatus())
        clientWithCaptureTime().propFind("", depth = 1)
        val enabledBody = server.takeRequest().body.readUtf8()
        assertTrue(
            "PROPFIND must request nc:metadata-photos-original_date_time, was: $enabledBody",
            enabledBody.contains("metadata-photos-original_date_time")
        )

        server.enqueue(multistatus())
        client.propFind("", depth = 1)
        val defaultBody = server.takeRequest().body.readUtf8()
        assertTrue(
            "default PROPFIND must not request nc:metadata-photos-original_date_time, was: $defaultBody",
            !defaultBody.contains("metadata-photos-original_date_time")
        )
    }

    @Test
    fun photoCaptureTimeParsesValueAndEmptyPropstat() {
        server.enqueue(nextcloudMultistatus())

        val resources = clientWithCaptureTime().propFind("", depth = 1)

        val dated = resources.single { it.displayName == "Birdie.jpg" }
        assertEquals(1341059531L, dated.photoCaptureTimeSeconds)
        val noExif = resources.single { it.displayName == "clip.mp4" }
        assertNull(noExif.photoCaptureTimeSeconds)
    }

    private fun clientWithCaptureTime(): WebDavClient = WebDavClient(
        okHttpClient = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build(),
        baseUrl = server.url("/dav").toString(),
        username = "u",
        password = "p",
        filesEndpoint = "",
        requestPhotoCaptureTime = true
    )

    private fun nextcloudMultistatus(): MockResponse = MockResponse()
        .setResponseCode(207)
        .setHeader("Content-Type", "application/xml; charset=utf-8")
        .setBody(
            """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:nc="http://nextcloud.org/ns">
              <d:response>
                <d:href>/dav/Birdie.jpg</d:href>
                <d:propstat><d:prop>
                  <d:displayname>Birdie.jpg</d:displayname>
                  <d:getcontenttype>image/jpeg</d:getcontenttype>
                  <d:getlastmodified>Sat, 30 Jun 2012 12:32:11 GMT</d:getlastmodified>
                  <d:creationdate>1970-01-01T00:00:00+00:00</d:creationdate>
                  <nc:metadata-photos-original_date_time>1341059531</nc:metadata-photos-original_date_time>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/clip.mp4</d:href>
                <d:propstat><d:prop>
                  <d:displayname>clip.mp4</d:displayname>
                  <d:getcontenttype>video/mp4</d:getcontenttype>
                  <d:getlastmodified>Fri, 20 Nov 2026 17:29:21 GMT</d:getlastmodified>
                  <d:creationdate>1970-01-01T00:00:00+00:00</d:creationdate>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                <d:propstat><d:prop>
                  <nc:metadata-photos-original_date_time/>
                </d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
              </d:response>
            </d:multistatus>"""
        )

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
