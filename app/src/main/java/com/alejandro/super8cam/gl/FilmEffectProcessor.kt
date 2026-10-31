package com.alejandro.super8cam.gl

import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor

private const val TAG = "FilmEffectProcessor"
private const val SIMULATED_FILM_FPS = 18.0f
private const val FRAME_INTERVAL_NS = (1_000_000_000f / SIMULATED_FILM_FPS).toLong()
private const val FRAME_SLACK_NS = 4_000_000L // tolerate camera frame jitter

/**
 * Applies the Super-8 look (see [FilmShader]) to the live camera feed on its
 * own GL thread, and forwards the filtered frames to every attached output
 * (the on-screen preview *and* the video recorder see the same filtered
 * image, so what you record is what you saw while shooting).
 */
class FilmEffectProcessor : SurfaceProcessor {

    private val glThread = HandlerThread("FilmGLThread").apply { start() }
    private val glHandler = Handler(glThread.looper)
    private val glExecutor = Executor { command -> glHandler.post(command) }

    private val eglCore = EglCore()
    private val renderer = FilmRenderer()

    private var cameraSurfaceTexture: SurfaceTexture? = null
    private var cameraSurface: Surface? = null
    private val camTransform = FloatArray(16)
    private val finalTransform = FloatArray(16)

    private var startTimeNs = 0L
    private var lastDrawNs = 0L
    private var glSetUp = false

    private class OutputTarget(
        val output: SurfaceOutput,
        val eglSurface: EGLSurface,
        val width: Int,
        val height: Int
    )

    private val outputs = mutableListOf<OutputTarget>()

    private fun ensureGlSetUp() {
        if (!glSetUp) {
            eglCore.setup()
            renderer.setup()
            glSetUp = true
        }
    }

    override fun onInputSurface(request: SurfaceRequest) {
        glHandler.post {
            ensureGlSetUp()
            eglCore.makeCurrentPlaceholder()

            val resolution = request.resolution
            val texture = SurfaceTexture(renderer.cameraTextureId)
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            val surface = Surface(texture)

            texture.setOnFrameAvailableListener({ onCameraFrameAvailable() }, glHandler)
            cameraSurfaceTexture = texture
            cameraSurface = surface
            if (startTimeNs == 0L) startTimeNs = System.nanoTime()

            request.provideSurface(surface, glExecutor) {
                glHandler.post {
                    texture.setOnFrameAvailableListener(null)
                    texture.release()
                    surface.release()
                    if (cameraSurfaceTexture === texture) {
                        cameraSurfaceTexture = null
                        cameraSurface = null
                    }
                }
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        glHandler.post {
            ensureGlSetUp()
            val surface = surfaceOutput.getSurface(glExecutor) { event ->
                glHandler.post { removeOutput(surfaceOutput) }
            }
            val eglSurface = eglCore.createWindowSurface(surface)
            val size = surfaceOutput.size
            outputs.add(OutputTarget(surfaceOutput, eglSurface, size.width, size.height))
        }
    }

    private fun removeOutput(surfaceOutput: SurfaceOutput) {
        val iterator = outputs.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()
            if (target.output === surfaceOutput) {
                eglCore.destroySurface(target.eglSurface)
                iterator.remove()
            }
        }
    }

    private fun onCameraFrameAvailable() {
        val texture = cameraSurfaceTexture ?: return
        eglCore.makeCurrentPlaceholder()
        texture.updateTexImage()
        texture.getTransformMatrix(camTransform)

        if (outputs.isEmpty()) return

        // Real 8mm runs at ~18 fps: only pass a new frame through every
        // 1/18 s and drop the rest. This gives the choppy film cadence in
        // both the preview and the recorded video.
        val nowNs = System.nanoTime()
        if (lastDrawNs != 0L && nowNs - lastDrawNs < FRAME_INTERVAL_NS - FRAME_SLACK_NS) return
        lastDrawNs = nowNs

        val elapsedSeconds = (nowNs - startTimeNs) / 1_000_000_000f
        val frameSeed = kotlin.math.floor(elapsedSeconds * SIMULATED_FILM_FPS)

        for (target in outputs) {
            eglCore.makeCurrent(target.eglSurface)
            Matrix.setIdentityM(finalTransform, 0)
            try {
                target.output.updateTransformMatrix(finalTransform, camTransform)
            } catch (t: Throwable) {
                // Fall back to the raw camera transform if the output has
                // already been removed mid-frame.
                System.arraycopy(camTransform, 0, finalTransform, 0, 16)
            }
            val aspect = target.width.toFloat() / target.height.toFloat()
            renderer.draw(finalTransform, elapsedSeconds, frameSeed, aspect, target.width, target.height)
            eglCore.swapBuffers(target.eglSurface)
        }
    }

    fun release() {
        glHandler.post {
            for (target in outputs) {
                eglCore.destroySurface(target.eglSurface)
            }
            outputs.clear()
            cameraSurfaceTexture?.release()
            cameraSurface?.release()
            renderer.release()
            eglCore.release()
            glThread.quitSafely()
        }
    }
}
