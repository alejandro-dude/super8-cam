package com.alejandro.super8cam.gl

import androidx.camera.core.CameraEffect
import androidx.core.util.Consumer
import java.util.concurrent.Executor

/**
 * CameraEffect's constructors are `protected` (it's meant to be
 * subclassed, not built), so this is the thin subclass that wires our
 * [FilmEffectProcessor] in as the SurfaceProcessor for both the preview
 * and the recorded video.
 */
class FilmCameraEffect(
    executor: Executor,
    processor: FilmEffectProcessor,
    errorListener: Consumer<Throwable>
) : CameraEffect(CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE, executor, processor, errorListener)
