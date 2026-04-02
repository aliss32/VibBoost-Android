package com.alissgmr.vibboost

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "VibBoostChannel"
        private const val NOTIFICATION_ID = 2026
    }

    private var visualizer: Visualizer? = null
    private var dsp: DynamicsProcessing? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VibBoost Pro: ACTIVE")
            .setContentText("Analyzing Bass Frequencies in Background...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        
        if (!isRunning) {
            startEngine()
            isRunning = true
        }

        return START_STICKY
    }

    private fun startEngine() {
        try {
            // Wavelet Tarzı DynamicsProcessing Yapılandırması
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                1, true, 1, true, 1, true, 1, true
            ).build()

            // Session 0: Global Sistem Sesine Kanca Atma
            dsp = DynamicsProcessing(0, 0, config).apply { enabled = true }

            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null || !isRunning) return

                        // PC Versiyonu: Hassas Bass Analizi (20Hz - 120Hz)
                        var maxBass = 0f
                        for (i in 0 until 4 step 2) {
                            val magnitude = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            if (magnitude > maxBass) maxBass = magnitude
                        }

                        // Gürültü Kapısı (Noise Gate) ve Yoğunluk Eşlemesi
                        if (maxBass > 18f) {
                            val intensity = ((maxBass / 130f) * 255).toInt().coerceIn(1, 255)
                            processVibration(intensity)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            isRunning = false
            stopSelf()
        }
    }

    private fun processVibration(intensity: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(25L, intensity))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "VibBoost Engine Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        visualizer?.apply { enabled = false; release() }
        dsp?.apply { enabled = false; release() }
        super.onDestroy()
    }
}
