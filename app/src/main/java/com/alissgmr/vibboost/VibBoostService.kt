package com.alissgmr.vibboost

import android.app.*
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.max

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        const val UPDATE_ACTION = "com.alissgmr.vibboost.UPDATE"
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // Keskinlik Ayarları
    private val noiseGate = 30f      // Bass yoksa sıfır titreşim
    private val maxIntensity = 255   // Tam güç
    private var lastTriggerTime = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost: HARD TRIGGER")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
        if (!isRunning) {
            startDirectEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun startDirectEngine() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = 128
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        // Saf Bass Analizi (40-100Hz)
                        var bassPower = 0f
                        for (i in 2..6 step 2) { 
                            val mag = hypot(fft[i].toFloat(), fft[i+1].toFloat())
                            bassPower = max(bassPower, mag)
                        }

                        // Sert Tetikleme: Yumuşatma (Smoothing) tamamen kaldırıldı
                        if (bassPower > noiseGate) {
                            val currentTime = System.currentTimeMillis()
                            // Kesikliği önlemek için 30ms'lik bloklar halinde tam vuruş
                            if (currentTime - lastTriggerTime > 30) {
                                val level = ((bassPower / 120f) * 255).toInt().coerceIn(150, maxIntensity)
                                vibrator?.vibrate(VibrationEffect.createOneShot(35, level))
                                lastTriggerTime = currentTime
                            }
                        }

                        val intent = Intent(UPDATE_ACTION).apply {
                            putExtra("lvl", if (bassPower > noiseGate) ((bassPower / 120f) * 100).toInt().coerceIn(0, 100) else 0)
                        }
                        sendBroadcast(intent)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("VibBoostChannel", "VibBoost", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        isRunning = false
        visualizer?.enabled = false
        visualizer?.release()
        super.onDestroy()
    }
}
