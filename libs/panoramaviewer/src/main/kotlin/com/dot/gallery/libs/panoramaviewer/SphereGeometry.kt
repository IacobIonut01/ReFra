/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Factory for generating sphere and cylinder mesh geometry used by [PanoramaRenderer].
 *
 * Both [createSphere] and [createCylinder] produce indexed triangle meshes with
 * separate vertex-position and texture-coordinate buffers in native byte order,
 * ready for direct use with OpenGL ES `glVertexAttribPointer` / `glDrawElements`.
 *
 * All geometry is centred at the origin. The camera is placed at the origin and
 * looks at the **inside** surface (back-face culling removes the outside).
 *
 * @see PanoramaRenderer
 */
internal object SphereGeometry {

    /**
     * An indexed triangle mesh stored in direct [java.nio] buffers.
     *
     * @property vertices   Packed XYZ float positions (3 floats per vertex).
     * @property texCoords  Packed UV float coordinates (2 floats per vertex).
     * @property indices    Unsigned-short triangle indices.
     * @property indexCount Total number of indices (= number of triangles × 3).
     */
    data class Mesh(
        val vertices: FloatBuffer,
        val texCoords: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int
    )

    /**
     * Creates a UV sphere mesh for photosphere (equirectangular) rendering.
     * The camera is placed at the center looking outward.
     *
     * @param radius Sphere radius
     * @param latSegments Number of latitude segments
     * @param lonSegments Number of longitude segments
     */
    fun createSphere(
        radius: Float = 1f,
        latSegments: Int = 64,
        lonSegments: Int = 64
    ): Mesh {
        val vertexCount = (latSegments + 1) * (lonSegments + 1)
        val vertices = allocateFloatBuffer(vertexCount * 3)
        val texCoords = allocateFloatBuffer(vertexCount * 2)

        for (lat in 0..latSegments) {
            val theta = Math.PI * lat / latSegments
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()

            for (lon in 0..lonSegments) {
                val phi = 2.0 * Math.PI * lon / lonSegments
                val sinPhi = sin(phi).toFloat()
                val cosPhi = cos(phi).toFloat()

                val x = radius * sinTheta * cosPhi
                val y = radius * cosTheta
                val z = radius * sinTheta * sinPhi

                vertices.put(x)
                vertices.put(y)
                vertices.put(z)

                // Flip U so the image is not mirrored when viewed from inside
                val u = 1f - lon.toFloat() / lonSegments
                val v = lat.toFloat() / latSegments
                texCoords.put(u)
                texCoords.put(v)
            }
        }

        val indexCount = latSegments * lonSegments * 6
        val indices = allocateShortBuffer(indexCount)

        for (lat in 0 until latSegments) {
            for (lon in 0 until lonSegments) {
                val first = (lat * (lonSegments + 1) + lon).toShort()
                val second = (first + lonSegments + 1).toShort()

                indices.put(first)
                indices.put(second)
                indices.put((first + 1).toShort())

                indices.put(second)
                indices.put((second + 1).toShort())
                indices.put((first + 1).toShort())
            }
        }

        vertices.position(0)
        texCoords.position(0)
        indices.position(0)

        return Mesh(vertices, texCoords, indices, indexCount)
    }

    /**
     * Creates a cylinder mesh for panorama rendering.
     * The camera is placed at the center looking outward.
     *
     * @param radius Cylinder radius
     * @param height Cylinder height
     * @param arcDegrees Angular span in degrees (360 = full wrap, 180 = half, etc.).
     *        The arc is centered on the default camera look direction (-Z).
     * @param segments Number of circumference segments
     */
    fun createCylinder(
        radius: Float = 1f,
        height: Float = 1f,
        arcDegrees: Float = 360f,
        segments: Int = 128
    ): Mesh {
        val vertexCount = (segments + 1) * 2
        val vertices = allocateFloatBuffer(vertexCount * 3)
        val texCoords = allocateFloatBuffer(vertexCount * 2)

        val halfHeight = height / 2f
        val arcRad = Math.toRadians(arcDegrees.toDouble())
        // Center the arc on -Z (angle = 3π/2) so the middle of the image faces the default camera
        val startAngle = (3.0 * Math.PI / 2.0) - arcRad / 2.0

        for (i in 0..segments) {
            val t = i.toDouble() / segments
            val angle = startAngle + arcRad * t
            val x = radius * cos(angle).toFloat()
            val z = radius * sin(angle).toFloat()
            val u = 1f - t.toFloat()

            // Bottom vertex
            vertices.put(x)
            vertices.put(-halfHeight)
            vertices.put(z)
            texCoords.put(u)
            texCoords.put(1f)

            // Top vertex
            vertices.put(x)
            vertices.put(halfHeight)
            vertices.put(z)
            texCoords.put(u)
            texCoords.put(0f)
        }

        val indexCount = segments * 6
        val indices = allocateShortBuffer(indexCount)

        for (i in 0 until segments) {
            val bl = (i * 2).toShort()
            val tl = (i * 2 + 1).toShort()
            val br = (i * 2 + 2).toShort()
            val tr = (i * 2 + 3).toShort()

            indices.put(bl)
            indices.put(br)
            indices.put(tl)

            indices.put(tl)
            indices.put(br)
            indices.put(tr)
        }

        vertices.position(0)
        texCoords.position(0)
        indices.position(0)

        return Mesh(vertices, texCoords, indices, indexCount)
    }

    /** Allocates a direct [FloatBuffer] of the given [capacity] in native byte order. */
    private fun allocateFloatBuffer(capacity: Int): FloatBuffer =
        ByteBuffer.allocateDirect(capacity * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    /** Allocates a direct [ShortBuffer] of the given [capacity] in native byte order. */
    private fun allocateShortBuffer(capacity: Int): ShortBuffer =
        ByteBuffer.allocateDirect(capacity * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
}
