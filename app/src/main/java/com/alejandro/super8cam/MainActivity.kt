package com.alejandro.super8cam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.alejandro.super8cam.databinding.ActivityMainBinding

/** Simulated film speed used only for the on-screen footage counter (the real
 * sensor capture rate is whatever CameraX/the device picks; this is purely cosmetic). */
private const val SIMULATED_FPS = 18
private const val FRAMES_PER_FOOT = 72 // approx. for standard-8 stock, close enough for a prop counter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var soundManager: SoundManager

    private val uiHandler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var counterRunnable: Runnable? = null

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionsGranted()
        } else {
            binding.permissionOverlay.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this)
        soundManager = SoundManager(this)

        binding.grantPermissionButton.setOnClickListener { requestNeededPermissions() }
        binding.shutterButton.setOnClickListener { onShutterTapped() }

        cameraController.onRecordingStateChanged = { recording -> onRecordingStateChanged(recording) }
        cameraController.onRecordingSaved = { _, _ -> /* could show a toast here */ }

        if (hasAllPermissions()) {
            onPermissionsGranted()
        } else {
            binding.permissionOverlay.visibility = View.VISIBLE
            requestNeededPermissions()
        }
    }

    private fun requestNeededPermissions() {
        requestPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun hasAllPermissions(): Boolean {
        return listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun onPermissionsGranted() {
        binding.permissionOverlay.visibility = View.GONE
        cameraController.start(this, binding.cameraPreview)
    }

    private var startPending = false

    // The click is played *outside* the recording window so the microphone
    // never captures it: before the recording starts, or after it stops.
    private fun onShutterTapped() {
        if (cameraController.isRecording()) {
            cameraController.toggleRecording()
            soundManager.playShutterClick()
        } else if (!startPending) {
            startPending = true
            soundManager.playShutterClick()
            uiHandler.postDelayed({
                startPending = false
                cameraController.toggleRecording()
            }, 400)
        }
    }

    private fun onRecordingStateChanged(recording: Boolean) {
        binding.shutterButton.setBackgroundResource(
            if (recording) R.drawable.shutter_recording else R.drawable.shutter_idle
        )
        binding.recIndicator.visibility = if (recording) View.VISIBLE else View.INVISIBLE

        // No sound is played while recording: anything played through the
        // speaker would be picked up by the microphone and end up in the footage.
        if (recording) startCounter() else stopCounter()
    }

    private fun startCounter() {
        frameCount = 0
        val runnable = object : Runnable {
            override fun run() {
                frameCount++
                updateCounterText()
                uiHandler.postDelayed(this, 1000L / SIMULATED_FPS)
            }
        }
        counterRunnable = runnable
        uiHandler.post(runnable)
    }

    private fun stopCounter() {
        counterRunnable?.let { uiHandler.removeCallbacks(it) }
        counterRunnable = null
    }

    private fun updateCounterText() {
        val feet = frameCount / FRAMES_PER_FOOT
        val frames = frameCount % FRAMES_PER_FOOT
        binding.footageCounter.text = String.format("%d ft %02d", feet, frames)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCounter()
        soundManager.release()
        cameraController.release()
    }
}
