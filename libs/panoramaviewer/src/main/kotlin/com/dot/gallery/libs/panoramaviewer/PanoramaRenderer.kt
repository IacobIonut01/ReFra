/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for panoramic and photosphere images.
 *
 * Draws the image on the inside of a sphere ([ProjectionType.SPHERE]) or
 * cylinder ([ProjectionType.CYLINDER]) and composites three texture layers:
 *
 * 1. **Background texture** — a tiny (≈32 px) version of the source image stretched
 *    fullscreen. OpenGL bilinear filtering produces a natural blur effect behind the
 *    panorama geometry.
 * 2. **Base texture** — a low-resolution (≈1024 px) decode of the full image used as
 *    the initial texture on the geometry, displayed instantly while the detail loads.
 * 3. **Detail texture** — a high-resolution region ([DetailRegion]) covering only the
 *    currently visible viewport, decoded on-demand via
 *    [BitmapRegionDecoder][android.graphics.BitmapRegionDecoder] to avoid OOM.
 *
 * The renderer is driven by [PanoramaGLSurfaceView], which feeds it pending bitmaps,
 * camera angles, and aspect-ratio data from a background loader thread.
 *
 * **Threading**: All `pending*` fields are `@Volatile` and are written from the loader
 * thread and read/consumed on the GL thread inside [onDrawFrame].
 *
 * @param projectionType Determines the geometry shape and camera constraints.
 * @see PanoramaGLSurfaceView
 * @see RegionLoader
 */
internal class PanoramaRenderer(
    private val projectionType: ProjectionType
) : GLSurfaceView.Renderer {

    // ──────────────────────────────────────────────────────────────────────
    // Camera state (written from the UI / sensor thread, read on GL thread)
    // ──────────────────────────────────────────────────────────────────────

    /** Horizontal rotation in degrees. */
    @Volatile var yaw: Float = 0f

    /** Vertical rotation in degrees. Positive = look up. */
    @Volatile var pitch: Float = 0f

    /** Vertical field-of-view in degrees. Adjusted by pinch-to-zoom. */
    @Volatile var fov: Float = DEFAULT_FOV

    // ──────────────────────────────────────────────────────────────────────
    // Pending data from the loader thread (consumed on the GL thread)
    // ──────────────────────────────────────────────────────────────────────

    /** Low-resolution bitmap to upload as the base texture. */
    @Volatile var pendingBaseBitmap: Bitmap? = null

    /** High-resolution detail region to upload as the detail texture. */
    @Volatile var pendingDetail: DetailRegion? = null

    /** Image aspect ratio (width / height) used to rebuild the cylinder mesh. */
    @Volatile var pendingImageAspect: Float = 0f

    /**
     * Horizontal arc span of the cylinder in degrees.
     *
     * - `360f` for full-wrap cylinders and spheres.
     * - A smaller value for partial panoramas, computed from the image aspect ratio.
     *
     * Used by [getVisibleUVRect] for correct UV mapping and by
     * [PanoramaGLSurfaceView] for yaw clamping.
     */
    @Volatile var cylinderArcDegrees: Float = 360f

    /** Tiny bitmap to upload as the blurred fullscreen background texture. */
    @Volatile var pendingBgBitmap: Bitmap? = null

    // ──────────────────────────────────────────────────────────────────────
    // GL resources
    // ──────────────────────────────────────────────────────────────────────

    /** Panorama shader program handle. */
    private var program = 0

    /** Background fullscreen-quad shader program handle. */
    private var bgProgram = 0

    private var baseTextureId = 0
    private var detailTextureId = 0
    private var bgTextureId = 0
    private var baseLoaded = false
    private var detailLoaded = false
    private var bgLoaded = false

    /** UV bounds of the currently uploaded detail texture. */
    private var detailBounds = floatArrayOf(0f, 0f, 0f, 0f)

    /** The 3D mesh representing the sphere or cylinder geometry. */
    private lateinit var mesh: SphereGeometry.Mesh

    // Pre-allocated matrix arrays to avoid per-frame allocations.
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    /** Current viewport width in pixels. */
    var viewportWidth = 1
        private set

    /** Current viewport height in pixels. */
    var viewportHeight = 1
        private set

    // ──────────────────────────────────────────────────────────────────────
    // GLSurfaceView.Renderer callbacks
    // ──────────────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        PanoramaLog.d("onSurfaceCreated()")
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // Render the inside of the geometry
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        bgProgram = createProgram(BG_VERTEX_SHADER, BG_FRAGMENT_SHADER)
        PanoramaLog.d("onSurfaceCreated() program=$program bgProgram=$bgProgram")

        mesh = when (projectionType) {
            ProjectionType.SPHERE -> SphereGeometry.createSphere(radius = GEOMETRY_RADIUS, latSegments = 64, lonSegments = 64)
            ProjectionType.CYLINDER -> SphereGeometry.createCylinder(radius = GEOMETRY_RADIUS, height = GEOMETRY_RADIUS, segments = 128)
        }
        PanoramaLog.d("onSurfaceCreated() mesh indexCount=${mesh.indexCount}")

        baseTextureId = createTexture()
        detailTextureId = createTexture()
        bgTextureId = createTexture()
        PanoramaLog.d("onSurfaceCreated() baseTexId=$baseTextureId detailTexId=$detailTextureId bgTexId=$bgTextureId")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        PanoramaLog.d("onSurfaceChanged() ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        // Rebuild cylinder mesh if image aspect ratio arrived
        val imgAspect = pendingImageAspect
        if (imgAspect > 0f) {
            pendingImageAspect = 0f
            if (projectionType == ProjectionType.CYLINDER) {
                // Arc degrees: wider images get more wrap, capped at 360°
                val arcDeg = (imgAspect * 120f).coerceIn(120f, 360f)
                cylinderArcDegrees = arcDeg
                // Height based on arc length (not full circumference) to maintain aspect ratio
                val r = GEOMETRY_RADIUS
                val arcLen = (arcDeg / 360f) * 2f * Math.PI.toFloat() * r
                val cylinderHeight = arcLen / imgAspect
                mesh = SphereGeometry.createCylinder(radius = r, height = cylinderHeight, arcDegrees = arcDeg, segments = 128)
                PanoramaLog.d("onDrawFrame() rebuilt cylinder mesh: height=$cylinderHeight arc=$arcDeg° (aspect=$imgAspect)")
            }
        }

        // Upload background blur texture if pending
        pendingBgBitmap?.let { bmp ->
            PanoramaLog.d("onDrawFrame() uploading bg texture ${bmp.width}x${bmp.height}")
            uploadTexture(bgTextureId, bmp)
            bgLoaded = true
            pendingBgBitmap = null
        }

        // Upload base texture if pending
        pendingBaseBitmap?.let { bmp ->
            PanoramaLog.d("onDrawFrame() uploading base texture ${bmp.width}x${bmp.height}")
            uploadTexture(baseTextureId, bmp)
            baseLoaded = true
            pendingBaseBitmap = null
        }

        // Upload detail texture if pending
        pendingDetail?.let { detail ->
            PanoramaLog.d("onDrawFrame() uploading detail texture ${detail.bitmap.width}x${detail.bitmap.height} bounds=(${detail.uMin},${detail.vMin})-(${detail.uMax},${detail.vMax})")
            uploadTexture(detailTextureId, detail.bitmap)
            detailBounds = floatArrayOf(detail.uMin, detail.vMin, detail.uMax, detail.vMax)
            detailLoaded = true
            pendingDetail = null
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (!baseLoaded) return

        // --- Background pass: fullscreen blurred image ---
        if (bgLoaded) drawBackground()

        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, 0.1f, 300f)

        // Camera at the origin, looking based on yaw/pitch
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.rotateM(viewMatrix, 0, -pitch, 1f, 0f, 0f)
        Matrix.rotateM(viewMatrix, 0, -yaw, 0f, 1f, 0f)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix.clone(), 0)

        GLES20.glUseProgram(program)

        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val baseSamplerHandle = GLES20.glGetUniformLocation(program, "uBaseTexture")
        val detailSamplerHandle = GLES20.glGetUniformLocation(program, "uDetailTexture")
        val detailBoundsHandle = GLES20.glGetUniformLocation(program, "uDetailBounds")
        val hasDetailHandle = GLES20.glGetUniformLocation(program, "uHasDetail")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, mesh.vertices)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, mesh.texCoords)

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Base texture on unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, baseTextureId)
        GLES20.glUniform1i(baseSamplerHandle, 0)

        // Detail texture on unit 1
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, detailTextureId)
        GLES20.glUniform1i(detailSamplerHandle, 1)

        GLES20.glUniform4fv(detailBoundsHandle, 1, detailBounds, 0)
        GLES20.glUniform1f(hasDetailHandle, if (detailLoaded) 1f else 0f)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            mesh.indexCount,
            GLES20.GL_UNSIGNED_SHORT,
            mesh.indices
        )

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Render passes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Draws the blurred fullscreen background behind the panorama geometry.
     *
     * Temporarily disables depth testing and face culling so the fullscreen
     * quad is always visible, then re-enables them for the main panorama pass.
     */
    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUseProgram(bgProgram)

        val posHandle = GLES20.glGetAttribLocation(bgProgram, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(bgProgram, "aTexCoord")
        val samplerHandle = GLES20.glGetUniformLocation(bgProgram, "uTexture")

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, BG_QUAD_VERTS)

        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, BG_QUAD_TEX)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    // ──────────────────────────────────────────────────────────────────────
    // UV calculations
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Computes the visible UV rectangle based on the current camera state.
     *
     * The returned array is `[uMin, vMin, uMax, vMax]` in 0–1 texture space,
     * with a 30 % margin added on each side for pre-loading slightly beyond
     * the viewport. This is used by [PanoramaGLSurfaceView] to decide which
     * image region to decode at high resolution via [RegionLoader.loadRegion].
     *
     * For partial cylinders the U centre is computed differently from
     * full-wrap geometries because yaw is clamped to the arc range.
     *
     * @return A four-element float array `[uMin, vMin, uMax, vMax]`.
     */
    fun getVisibleUVRect(): FloatArray {
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val margin = 1.3f // 30% margin for pre-loading

        val totalHDeg = if (projectionType == ProjectionType.CYLINDER) cylinderArcDegrees else 360f

        // Horizontal: yaw maps to U
        val uCenter = if (projectionType == ProjectionType.CYLINDER && totalHDeg < 360f) {
            // Partial cylinder: yaw in [-arc/2, arc/2] → U in [0, 1]
            (yaw + totalHDeg / 2f) / totalHDeg
        } else {
            // Full 360° wrap
            ((yaw % 360f + 360f) % 360f) / 360f
        }
        val uHalfExtent = (fov / totalHDeg) * margin

        // Vertical: pitch maps to V
        val vCenter = (90f - pitch) / 180f
        val vHalfExtent = (fov / aspect / 180f) * margin

        return floatArrayOf(
            (uCenter - uHalfExtent).coerceIn(0f, 1f),
            (vCenter - vHalfExtent).coerceIn(0f, 1f),
            (uCenter + uHalfExtent).coerceIn(0f, 1f),
            (vCenter + vHalfExtent).coerceIn(0f, 1f)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // GL helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Allocates a GL texture with bilinear filtering and edge-clamping. */
    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    /** Uploads [bitmap] data to the given GL texture handle. */
    private fun uploadTexture(textureId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    /** Links a vertex + fragment shader pair into a GL program. */
    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        return prog
    }

    /** Compiles a single GLSL shader, logging errors on failure. */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            PanoramaLog.e("compileShader() failed: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {

        // ── Camera defaults & limits ────────────────────────────────────

        /** Default vertical field-of-view in degrees. */
        const val DEFAULT_FOV = 95f

        /** Minimum FOV (maximum zoom-in) in degrees. */
        const val MIN_FOV = 30f

        /** Maximum FOV (maximum zoom-out) in degrees. */
        const val MAX_FOV = 110f

        /** Maximum pitch angle for [ProjectionType.SPHERE] (±90° = full vertical). */
        const val MAX_PITCH_SPHERE = 90f

        /** Maximum pitch angle for [ProjectionType.CYLINDER] (restricted look). */
        const val MAX_PITCH_CYLINDER = 45f

        // ── Geometry ────────────────────────────────────────────────────

        /** Radius of the sphere / cylinder geometry in world units. */
        private const val GEOMETRY_RADIUS = 50f

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uMVPMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uBaseTexture;
            uniform sampler2D uDetailTexture;
            uniform vec4 uDetailBounds;
            uniform float uHasDetail;
            void main() {
                vec4 base = texture2D(uBaseTexture, vTexCoord);
                if (uHasDetail > 0.5 &&
                    vTexCoord.x >= uDetailBounds.x && vTexCoord.x <= uDetailBounds.z &&
                    vTexCoord.y >= uDetailBounds.y && vTexCoord.y <= uDetailBounds.w) {
                    vec2 detailCoord = (vTexCoord - uDetailBounds.xy) / (uDetailBounds.zw - uDetailBounds.xy);
                    gl_FragColor = texture2D(uDetailTexture, detailCoord);
                } else {
                    gl_FragColor = base;
                }
            }
        """

        // ── Background shaders & geometry ───────────────────────────────
        // Simple passthrough with darkening for the fullscreen blurred backdrop.
        private const val BG_VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val BG_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                // Two-ring Gaussian blur on the tiny (~32px) background texture.
                // Inner ring at 1/16 step, outer ring at 1/8 step.
                vec2 s1 = vec2(1.0 / 16.0);
                vec2 s2 = vec2(1.0 / 8.0);
                vec4 c  = texture2D(uTexture, vTexCoord) * 0.16;
                c += texture2D(uTexture, vTexCoord + vec2( s1.x, 0.0)) * 0.08;
                c += texture2D(uTexture, vTexCoord + vec2(-s1.x, 0.0)) * 0.08;
                c += texture2D(uTexture, vTexCoord + vec2(0.0,  s1.y)) * 0.08;
                c += texture2D(uTexture, vTexCoord + vec2(0.0, -s1.y)) * 0.08;
                c += texture2D(uTexture, vTexCoord + vec2( s2.x, 0.0)) * 0.06;
                c += texture2D(uTexture, vTexCoord + vec2(-s2.x, 0.0)) * 0.06;
                c += texture2D(uTexture, vTexCoord + vec2(0.0,  s2.y)) * 0.06;
                c += texture2D(uTexture, vTexCoord + vec2(0.0, -s2.y)) * 0.06;
                c += texture2D(uTexture, vTexCoord + s1) * 0.04;
                c += texture2D(uTexture, vTexCoord - s1) * 0.04;
                c += texture2D(uTexture, vTexCoord + vec2(s1.x, -s1.y)) * 0.04;
                c += texture2D(uTexture, vTexCoord + vec2(-s1.x, s1.y)) * 0.04;
                c += texture2D(uTexture, vTexCoord + s2) * 0.03;
                c += texture2D(uTexture, vTexCoord - s2) * 0.03;
                c += texture2D(uTexture, vTexCoord + vec2(s2.x, -s2.y)) * 0.03;
                c += texture2D(uTexture, vTexCoord + vec2(-s2.x, s2.y)) * 0.03;
                gl_FragColor = vec4(c.rgb * 0.4, 1.0);
            }
        """

        // Fullscreen quad — two triangles in clip space with matching UVs.
        private val BG_QUAD_VERTS = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
                position(0)
            }
        private val BG_QUAD_TEX = ByteBuffer.allocateDirect(8 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))
                position(0)
            }
    }
}
