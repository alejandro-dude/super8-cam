package com.alejandro.super8cam

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.alejandro.super8cam.gl.FilmCameraEffect
import com.alejandro.super8cam.gl.FilmEffectProcessor
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CameraController"

class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var filmEffect: FilmEffectProcessor? = null

    var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    var onRecordingSaved: ((success: Boolean, message: String) -> Unit)? = null

    fun start(lifecycleOwner: LifecycleOwner, previewView: PreviewView, onReady: () -> Unit = {}) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            bind(provider, lifecycleOwner, previewView)
            onReady()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(provider: ProcessCameraProvider, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        provider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val qualitySelector = QualitySelector.from(
            Quality.FHD,
            FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
        )
        val newRecorder = Recorder.Builder().setQualitySelector(qualitySelector).build()
        val newVideoCapture = VideoCapture.withOutput(newRecorder)

        val effect = FilmEffectProcessor()
        filmEffect = effect
        val cameraEffect = FilmCameraEffect(
            ContextCompat.getMainExecutor(context),
            effect
        ) { throwable -> Log.e(TAG, "Film effect error", throwable) }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(newVideoCapture)
            .addEffect(cameraEffect)
            .build()

        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup
        )

        recorder = newRecorder
        videoCapture = newVideoCapture
    }

    @Suppress("MissingPermission")
    fun toggleRecording() {
        val currentRecording = activeRecording
        if (currentRecording != null) {
            currentRecording.stop()
            activeRecording = null
            return
        }

        val capture = videoCapture ?: return
        val name = "SUPER8_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Super8")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onRecordingStateChanged?.invoke(true)
                    is VideoRecordEvent.Finalize -> {
                        onRecordingStateChanged?.invoke(false)
                        if (!event.hasError()) {
                            onRecordingSaved?.invoke(true, "Saved to Movies/Super8")
                        } else {
                            Log.e(TAG, "Recording error: ${event.cause}")
                            onRecordingSaved?.invoke(false, "Recording failed: ${event.cause?.message}")
                        }
                    }
                    else -> Unit
                }
            }
    }

    fun isRecording(): Boolean = activeRecording != null

    fun release() {
        activeRecording?.stop()
        activeRecording = null
        cameraProvider?.unbindAll()
        filmEffect?.release()
    }
}
