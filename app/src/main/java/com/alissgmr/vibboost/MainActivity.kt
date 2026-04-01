package com.alissgmr.vibboost

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    private var isEngineRunning = false
    private val PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF121212.toInt())
            setPadding(64, 64, 64, 64)
        }

        val statusLabel = TextView(this).apply {
            text = "VIB-BOOST: IDLE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            setPadding(0, 0, 0, 80)
        }

        val actionButton = Button(this).apply {
            text = "START ENGINE"
            setBackgroundColor(0xFF3700B3.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                if (checkPermissions()) toggleEngine(statusLabel, this)
                else requestPermissions()
            }
        }

        rootLayout.addView(statusLabel)
        rootLayout.addView(actionButton)
        setContentView(rootLayout)

        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun toggleEngine(status: TextView, btn: Button) {
        if (isEngineRunning) {
            stopDSP()
            isEngineRunning = false
            status.text = "VIB-BOOST: IDLE"
            btn.text = "START ENGINE"
        } else {
            if (startDSP()) {
                isEngineRunning = true
                status.text = "VIB-BOOST: ACTIVE (BASS)"
                btn.text = "STOP ENGINE"
            }
        }
    }

    private fun startDSP(): Boolean {
        return try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return
                        var magnitude = 0f
                        // Bass Frekans Analizi (DSP)
                        for (i in 0 until 6 step 2) {
                            val mag = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            if (mag > magnitude) magnitude = mag
                        }
                        if (magnitude > 18f) processHaptic(magnitude)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "DSP Error: Global Audio Restricted", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun processHaptic(mag: Float) {
        val intensity = ((mag / 100f) * 255).toInt().coerceIn(1, 255)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(25L, intensity))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    }

    private fun stopDSP() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
    }

    private fun checkPermissions() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS), PERMISSION_CODE)
}
