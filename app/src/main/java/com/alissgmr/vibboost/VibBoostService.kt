package com.alissgmr.vibboost

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class VibBoostService : Service() {

    companion object { 
        var isRunning = false 
        var hapticListener: ((intensity: Int, duration: Long) -> Unit)? = null
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    private val NOISE_GATE = 25f // Biraz daha hassaslaştırdık
    private val BASS_CEILING = 180f    

    private var lastVibTime = 0L
    private var currentAmplitude = 0
    private var currentDuration = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Pro Aktif")
            .setContentText("120Hz Akıcı Görselleştirme Hazır")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }

        if (!isRunning) {
            initializeRawEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun initializeRawEngine() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return
                        val rawBass = max(hypot(fft[2].toFloat(), fft[3].toFloat()), hypot(fft[4].toFloat(), fft[5].toFloat()))
                        applyRawHaptics(rawBass)
                    }
                }, Visualizer.getMaxCaptureRate(), false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyRawHaptics(bassLevel: Float) {
        if (bassLevel < NOISE_GATE) {
            hapticListener?.invoke(0, 0L)
            return
        }

        val finalIntensity = ((bassLevel / BASS_CEILING) * 255).toInt().coerceIn(1, 255)
        val dynamicDuration = when {
            finalIntensity > 200 -> 200L
            finalIntensity > 150 -> 150L
            finalIntensity > 100 -> 100L
            else -> 60L
        }
        
        val now = System.currentTimeMillis()
        if (now - lastVibTime < currentDuration && finalIntensity < currentAmplitude + 20) {
            // UI'ı akıcı tutmak için veri göndermeye devam et ama titreşimi tetikleme
            hapticListener?.invoke(finalIntensity, dynamicDuration)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(dynamicDuration, finalIntensity))
            lastVibTime = now
            currentAmplitude = finalIntensity
            currentDuration = dynamicDuration
            hapticListener?.invoke(finalIntensity, dynamicDuration)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("VibBoostChannel", "VibBoost Engine", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        isRunning = false
        hapticListener = null
        visualizer?.release()
        super.onDestroy()
    }
}
