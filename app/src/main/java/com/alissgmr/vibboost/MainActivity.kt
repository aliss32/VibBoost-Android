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
    private var isRunning = false
    private val PERMISSION_REQ_CODE = 2026

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
        }

        val infoText = TextView(this).apply {
            text = "VIB-BOOST ENGINE\nSTATUS: READY"
            setTextColor(0xFF00FF00.toInt())
            gravity = Gravity.CENTER
            textSize = 18f
            setPadding(0, 0, 0, 100)
        }

        val btn = Button(this).apply {
            text = "ENGAGE HAPTICS"
            setOnClickListener {
                if (checkPerms()) toggleEngine(infoText, this)
                else requestPerms()
            }
        }

        layout.addView(infoText)
        layout.addView(btn)
        setContentView(layout)

        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun toggleEngine(tv: TextView, b: Button) {
        if (isRunning) {
            stopEngine()
            isRunning = false
            tv.text = "STATUS: IDLE"
            b.text = "ENGAGE HAPTICS"
        } else {
            if (startEngine()) {
                isRunning = true
                tv.text = "STATUS: PROCESSING AUDIO..."
                b.text = "DISENGAGE"
            }
        }
    }

    private fun startEngine(): Boolean {
        return try {
            // Wavelet tarzi Global Session (0) yakalama
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null || !isRunning) return
                        
                        // Bass Frekans Analizi (20Hz - 150Hz arasi ilk 3 indeks)
                        var bassPower = 0f
                        for (i in 0 until 6 step 2) {
                            val mag = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            if (mag > bassPower) bassPower = mag
                        }

                        // Gürültü Filtresi ve Hassasiyet Ayarı
                        if (bassPower > 16f) {
                            val amplitude = ((bassPower / 110f) * 255).toInt().coerceIn(1, 255)
                            triggerVib(amplitude)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Audio Hook Failed: Check Global Permissions", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun triggerVib(amp: Int) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(20L, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20L)
        }
    }

    private fun stopEngine() {
        visualizer?.apply {
            enabled = false
            release()
        }
        visualizer = null
    }

    private fun checkPerms() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestPerms() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS), PERMISSION_REQ_CODE)
}
