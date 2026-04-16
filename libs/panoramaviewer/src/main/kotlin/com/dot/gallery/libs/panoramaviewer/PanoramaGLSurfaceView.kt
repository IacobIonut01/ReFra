/*
 * Copyright 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.libs.panoramaviewer

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

/**
 * Core Android [GLSurfaceView] that renders a panoramic or photosphere image.
 *
 * This view is the bridge between the Compose layer ([PanoramaViewer]) and the
 * OpenGL renderer ([PanoramaRenderer]). It is responsible for:
 *
 * - **Touch handling** — single-finger drag to rotate, pinch to zoom, single-tap
 *   detection, and gesture disambiguation with a parent horizontal pager.
 * - **Inertia** — fling-style momentum after a drag gesture.
 * - **Gyroscope input** — optional device-motion rotation for photospheres.
 * - **Detail loading orchestration** — scheduling background decodes of the
 *   visible viewport region via [RegionLoader] when the camera moves.
 * - **Yaw clamping** — for partial-arc cylinders, prevents the camera from
 *   rotating past the image boundaries.
 *
 * @param context           Android context.
 * @param projectionType    Geometry projection mode.
 * @param gyroscopeEnabled  Whether to register for gyroscope sensor events.
 *
 * @see PanoramaViewer
 * @see PanoramaRenderer
 */
@SuppressLint("ViewConstructor")
internal class PanoramaGLSurfaceView(
    context: Context,
    private val projectionType: ProjectionType,
    private val gyroscopeEnabled: Boolean
) : GLSurfaceView(context), SensorEventListener {

    // ──────────────────────────────────────────────────────────────────────
    // Public callbacks
    // ──────────────────────────────────────────────────────────────────────

    /** Called when the user performs a single tap (touch down + up without drag). */
    var onTapListener: (() -> Unit)? = null

    /** Called whenever the camera state changes (rotation, zoom). */
    var onCameraChangedListener: ((CameraState) -> Unit)? = null

    // ──────────────────────────────────────────────────────────────────────
    // Core components
    // ──────────────────────────────────────────────────────────────────────

    private val renderer = PanoramaRenderer(projectionType)
    private var imageLoader: PanoramaImageLoader? = null

    // ──────────────────────────────────────────────────────────────────────
    // Touch state
    // ──────────────────────────────────────────────────────────────────────

    private var previousX = 0f
    private var previousY = 0f
    private var pointerCount = 0

    /** Tracks gesture start position and timing for direction disambiguation. */
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var gestureDecided = false
    private var gestureIsPanorama = false

    // ──────────────────────────────────────────────────────────────────────
    // Inertia state
    // ──────────────────────────────────────────────────────────────────────

    private var velocityX = 0f
    private var velocityY = 0f
    private var lastTouchTime = 0L

    // ──────────────────────────────────────────────────────────────────────
    // Detail loading
    // ──────────────────────────────────────────────────────────────────────

    private val loaderThread = HandlerThread("PanoramaDetailLoader").apply { start() }
    private val loaderHandler = Handler(loaderThread.looper)
    private var lastDetailYaw = Float.MAX_VALUE
    private var lastDetailPitch = Float.MAX_VALUE
    private var lastDetailFov = Float.MAX_VALUE
    private var detailLoadingInProgress = false
    private val detailLoadRunnable = Runnable { loadDetailForCurrentView() }
    private var released = false

    // ──────────────────────────────────────────────────────────────────────
    // Sensors & display
    // ──────────────────────────────────────────────────────────────────────

    /** Screen density used to normalise touch sensitivity across devices. */
    private val viewportDensity = context.resources.displayMetrics.density

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    /** Pinch-to-zoom gesture detector. */
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.fov = (renderer.fov / detector.scaleFactor).coerceIn(
                PanoramaRenderer.MIN_FOV,
                PanoramaRenderer.MAX_FOV
            )
            PanoramaLog.d("onScale() fov=${renderer.fov}")
            scheduleDetailLoad()
            notifyCameraChanged()
            return true
        }
    })

    // ──────────────────────────────────────────────────────────────────────
    // Initialisation
    // ──────────────────────────────────────────────────────────────────────

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        PanoramaLog.d("init() projectionType=$projectionType gyroscope=$gyroscopeEnabled")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Image loading
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Sets the image source by content [uri] using the default [RegionLoader].
     *
     * This triggers a multi-stage load on a background thread:
     * 1. A tiny (≈32 px) bitmap for the blurred background.
     * 2. A low-res (≈1024 px) base texture mapped onto the geometry.
     * 3. A high-res detail texture for the initial viewport.
     *
     * If the [BitmapRegionDecoder][android.graphics.BitmapRegionDecoder] fails
     * to initialise (e.g. for WebP images), a full-image fallback is attempted.
     *
     * @param contentResolver Used to open an input stream for the image.
     * @param uri             Content URI of the panoramic image.
     */
    fun setImageSource(contentResolver: ContentResolver, uri: Uri) {
        if (released) return
        PanoramaLog.d("setImageSource() uri=$uri")
        setImageLoader(RegionLoader(contentResolver, uri))
    }

    /**
     * Sets a custom [PanoramaImageLoader] as the image source.
     *
     * Use this instead of [setImageSource] when you need custom loading logic,
     * such as decrypting vault media or loading from a network source.
     *
     * The loader's [PanoramaImageLoader.initialize], [PanoramaImageLoader.loadBase],
     * and [PanoramaImageLoader.loadRegion] methods will be called on a background
     * thread. The loader will be [closed][PanoramaImageLoader.close] when the
     * viewer is disposed or a new source is set.
     *
     * @param loader The custom image loader to use.
     */
    fun setImageLoader(loader: PanoramaImageLoader) {
        if (released) return
        PanoramaLog.d("setImageLoader() loader=${loader::class.simpleName}")
        loaderHandler.post {
            imageLoader?.close()
            if (!loader.initialize()) {
                PanoramaLog.w("setImageLoader() initialize() failed, trying loadBase fallback")
                val fallback = loader.loadBase(RegionLoader.BASE_TEXTURE_SIZE)
                if (fallback != null) {
                    PanoramaLog.d("setImageLoader() fallback base loaded: ${fallback.width}x${fallback.height}")
                    renderer.pendingBaseBitmap = fallback
                    requestRender()
                } else {
                    PanoramaLog.e("setImageLoader() fallback also failed — black screen expected")
                }
                loader.close()
                return@post
            }
            imageLoader = loader

            // Tell renderer the image aspect ratio so it can resize the cylinder
            if (loader.imageWidth > 0 && loader.imageHeight > 0) {
                renderer.pendingImageAspect = loader.imageWidth.toFloat() / loader.imageHeight.toFloat()
            }

            // Load tiny background texture for blur effect (32px = very blurry when stretched)
            val bg = loader.loadBase(BG_TEXTURE_SIZE)
            if (bg != null) {
                PanoramaLog.d("setImageLoader() bg blur texture loaded: ${bg.width}x${bg.height}")
                renderer.pendingBgBitmap = bg
            }

            // Load low-res base texture
            val base = loader.loadBase(RegionLoader.BASE_TEXTURE_SIZE)
            if (base != null) {
                PanoramaLog.d("setImageLoader() base texture loaded: ${base.width}x${base.height}")
                renderer.pendingBaseBitmap = base
                requestRender()
            } else {
                PanoramaLog.e("setImageLoader() loadBase returned null")
            }

            // Load initial detail for the default camera position
            loadDetailForCurrentView()
        }
    }

    /**
     * Schedules a detail region load on the background thread if the camera
     * has moved more than [DETAIL_RELOAD_THRESHOLD] degrees since the last
     * detail was decoded. A short delay ([DETAIL_LOAD_DELAY_MS]) is applied
     * so rapid successive movements don't flood the decoder.
     */
    private fun scheduleDetailLoad() {
        if (released || detailLoadingInProgress) return
        val yawDelta = abs(renderer.yaw - lastDetailYaw)
        val pitchDelta = abs(renderer.pitch - lastDetailPitch)
        val fovDelta = abs(renderer.fov - lastDetailFov)
        if (yawDelta < DETAIL_RELOAD_THRESHOLD &&
            pitchDelta < DETAIL_RELOAD_THRESHOLD &&
            fovDelta < DETAIL_RELOAD_FOV_THRESHOLD) return

        loaderHandler.removeCallbacks(detailLoadRunnable)
        loaderHandler.postDelayed(detailLoadRunnable, DETAIL_LOAD_DELAY_MS)
    }

    /**
     * Decodes the high-resolution detail region for the current camera view.
     *
     * Converts the UV rect from [PanoramaRenderer.getVisibleUVRect] to pixel
     * coordinates, calls [RegionLoader.loadRegion], and posts the result as
     * a [DetailRegion] for upload on the GL thread.
     */
    private fun loadDetailForCurrentView() {
        val loader = imageLoader ?: return
        detailLoadingInProgress = true

        val uvRect = renderer.getVisibleUVRect()
        val uMin = uvRect[0]
        val vMin = uvRect[1]
        val uMax = uvRect[2]
        val vMax = uvRect[3]

        // Convert UV to pixel coordinates
        val left = (uMin * loader.imageWidth).toInt()
        val top = (vMin * loader.imageHeight).toInt()
        val right = (uMax * loader.imageWidth).toInt()
        val bottom = (vMax * loader.imageHeight).toInt()

        PanoramaLog.d("loadDetail() uv=($uMin,$vMin)-($uMax,$vMax) px=($left,$top)-($right,$bottom)")

        val detail = loader.loadRegion(left, top, right, bottom, RegionLoader.DETAIL_TEXTURE_SIZE)
        if (detail != null) {
            renderer.pendingDetail = DetailRegion(detail, uMin, vMin, uMax, vMax)
            requestRender()
        }

        lastDetailYaw = renderer.yaw
        lastDetailPitch = renderer.pitch
        lastDetailFov = renderer.fov
        detailLoadingInProgress = false
    }

    // ──────────────────────────────────────────────────────────────────────
    // Touch handling
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Handles all touch input: drag-to-rotate, pinch-to-zoom, tap detection,
     * and gesture disambiguation with a parent horizontal pager.
     *
     * **Gesture disambiguation**: On [MotionEvent.ACTION_MOVE], the view waits
     * until the finger has moved beyond [GESTURE_SLOP] pixels before deciding
     * whether this is a panorama rotation or a horizontal pager swipe. Vertical
     * or diagonal drags are claimed by the panorama; purely horizontal drags
     * are passed to the parent.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
                pointerCount = 1
                gestureDecided = false
                gestureIsPanorama = false
                velocityX = 0f
                velocityY = 0f
                lastTouchTime = System.nanoTime()
                // Don't claim the gesture yet — wait to see direction
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                // Multi-touch = pinch = always ours
                gestureDecided = true
                gestureIsPanorama = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!gestureDecided && pointerCount == 1) {
                    val totalDx = abs(event.x - downX)
                    val totalDy = abs(event.y - downY)
                    val totalDist = totalDx + totalDy
                    if (totalDist > GESTURE_SLOP) {
                        // Vertical drag or diagonal → panorama rotation
                        // Pure horizontal with low vertical → let pager handle it
                        gestureDecided = true
                        gestureIsPanorama = totalDy > totalDx * 0.6f || totalDx < PAGER_SWIPE_THRESHOLD
                        if (gestureIsPanorama) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        PanoramaLog.d("gesture decided: panorama=$gestureIsPanorama dx=$totalDx dy=$totalDy")
                    }
                }

                if (gestureIsPanorama && pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - previousX
                    val dy = event.y - previousY

                    val sensitivity = renderer.fov / 90f * TOUCH_SENSITIVITY / (viewportDensity.coerceAtLeast(1f))
                    val deltaYaw = dx * sensitivity
                    val deltaPitch = dy * sensitivity

                    renderer.yaw += deltaYaw
                    clampYaw()
                    val maxPitch = when (projectionType) {
                        ProjectionType.SPHERE -> PanoramaRenderer.MAX_PITCH_SPHERE
                        ProjectionType.CYLINDER -> PanoramaRenderer.MAX_PITCH_CYLINDER
                    }
                    renderer.pitch = (renderer.pitch + deltaPitch).coerceIn(-maxPitch, maxPitch)

                    val now = System.nanoTime()
                    val dt = ((now - lastTouchTime) / 1_000_000f).coerceAtLeast(1f)
                    velocityX = deltaYaw / dt * INERTIA_VELOCITY_SCALE
                    velocityY = deltaPitch / dt * INERTIA_VELOCITY_SCALE
                    lastTouchTime = now
                    notifyCameraChanged()
                }
                previousX = event.x
                previousY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerCount = 0
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP && !gestureDecided) {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (elapsed < TAP_TIMEOUT_MS) {
                        onTapListener?.invoke()
                    }
                }
                if (gestureIsPanorama) {
                    // Start inertia on GL thread
                    queueEvent { applyInertia() }
                    // Schedule detail load after gesture ends
                    scheduleDetailLoad()
                }
                gestureDecided = false
                gestureIsPanorama = false
            }
        }

        return true
    }

    /**
     * Applies fling-style inertia on the GL thread.
     *
     * Each frame, velocities are multiplied by [INERTIA_FRICTION] and applied
     * to yaw/pitch. The loop self-schedules via [postDelayed] at ≈60 fps
     * until velocity drops below the threshold.
     */
    private fun applyInertia() {
        val friction = INERTIA_FRICTION
        if (abs(velocityX) < 0.01f && abs(velocityY) < 0.01f) {
            // Inertia finished — schedule detail load
            post { scheduleDetailLoad() }
            return
        }

        velocityX *= friction
        velocityY *= friction
        renderer.yaw += velocityX
        clampYaw()

        val maxPitch = when (projectionType) {
            ProjectionType.SPHERE -> PanoramaRenderer.MAX_PITCH_SPHERE
            ProjectionType.CYLINDER -> PanoramaRenderer.MAX_PITCH_CYLINDER
        }
        renderer.pitch = (renderer.pitch + velocityY).coerceIn(-maxPitch, maxPitch)
        post { notifyCameraChanged() }

        if (abs(velocityX) > 0.01f || abs(velocityY) > 0.01f) {
            postDelayed({ queueEvent { applyInertia() } }, 16)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (gyroscopeEnabled && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        }
        PanoramaLog.d("onResume()")
    }

    override fun onPause() {
        super.onPause()
        if (gyroscopeEnabled) {
            sensorManager.unregisterListener(this)
        }
        PanoramaLog.d("onPause()")
    }

    /**
     * Releases all resources held by this view.
     *
     * Cancels pending detail loads, closes the [RegionLoader], and shuts
     * down the background loader thread. After this call, the view must
     * not be reused.
     */
    fun release() {
        if (released) return
        released = true
        PanoramaLog.d("release()")
        loaderHandler.removeCallbacksAndMessages(null)
        // Close loader on the loader thread, then quit
        loaderHandler.post {
            imageLoader?.close()
            imageLoader = null
            loaderThread.quitSafely()
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Gyroscope (SensorEventListener)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Applies gyroscope rotation when [gyroscopeEnabled] is true and no
     * touch gesture is in progress.
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (!gyroscopeEnabled || pointerCount > 0) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Gyroscope gives rotation rate in rad/s
            val axisX = event.values[0] // pitch rate
            val axisY = event.values[1] // yaw rate
            val dt = GYRO_DT

            renderer.yaw += Math.toDegrees(axisY.toDouble()).toFloat() * dt
            clampYaw()
            val maxPitch = when (projectionType) {
                ProjectionType.SPHERE -> PanoramaRenderer.MAX_PITCH_SPHERE
                ProjectionType.CYLINDER -> PanoramaRenderer.MAX_PITCH_CYLINDER
            }
            renderer.pitch = (renderer.pitch + Math.toDegrees(axisX.toDouble()).toFloat() * dt)
                .coerceIn(-maxPitch, maxPitch)
            scheduleDetailLoad()
            notifyCameraChanged()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ──────────────────────────────────────────────────────────────────────
    // Camera helpers
    // ──────────────────────────────────────────────────────────────────────

    /** Posts the current camera state to [onCameraChangedListener]. */
    private fun notifyCameraChanged() {
        onCameraChangedListener?.invoke(
            CameraState(
                yaw = renderer.yaw,
                pitch = renderer.pitch,
                fov = renderer.fov,
                arcDegrees = renderer.cylinderArcDegrees,
                projectionType = projectionType
            )
        )
    }

    /**
     * Clamps yaw for partial cylinders so the camera stays within the image arc.
     * For full 360° cylinders and spheres, yaw wraps freely.
     */
    private fun clampYaw() {
        if (projectionType == ProjectionType.SPHERE) return
        val arc = renderer.cylinderArcDegrees
        if (arc >= 360f) return
        // Max yaw = half arc minus half fov (so we don't see past edges)
        val maxYaw = (arc / 2f - renderer.fov / 2f).coerceAtLeast(0f)
        renderer.yaw = renderer.yaw.coerceIn(-maxYaw, maxYaw)
    }

    companion object {

        // ── Touch & gesture tuning ───────────────────────────────────────

        /** Base touch sensitivity multiplier (scaled by FOV and screen density). */
        private const val TOUCH_SENSITIVITY = 0.13f

        /** Per-frame velocity multiplier for inertia decay. 1.0 = no friction. */
        private const val INERTIA_FRICTION = 0.92f

        /** Multiplier applied to drag velocity when starting inertia. */
        private const val INERTIA_VELOCITY_SCALE = 10f

        /** Assumed delta-time per gyroscope sample (≈60 fps). */
        private const val GYRO_DT = 0.016f

        // ── Detail loading thresholds ────────────────────────────────────

        /** Minimum yaw/pitch change (degrees) before a new detail is decoded. */
        private const val DETAIL_RELOAD_THRESHOLD = 3f

        /** Minimum FOV change (degrees) before a new detail is decoded. */
        private const val DETAIL_RELOAD_FOV_THRESHOLD = 2f

        /** Delay (ms) after the last camera movement before starting a detail load. */
        private const val DETAIL_LOAD_DELAY_MS = 150L

        // ── Gesture disambiguation ───────────────────────────────────────

        /** Minimum movement (px) before the gesture direction is decided. */
        private const val GESTURE_SLOP = 20f

        /** Horizontal distance (px) at which a swipe is given to the parent pager. */
        private const val PAGER_SWIPE_THRESHOLD = 80f

        /** Maximum duration (ms) of a touch to be considered a single tap. */
        private const val TAP_TIMEOUT_MS = 250L

        // ── Textures ─────────────────────────────────────────────────────

        /** Size (px) of the tiny background texture. GL bilinear filtering creates a natural blur. */
        private const val BG_TEXTURE_SIZE = 32
    }
}
