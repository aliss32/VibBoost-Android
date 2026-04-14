package com.alissgmr.vibboost

import android.app.*
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.max

class VibBoostService : Service() {

    companion object { var isRunning = false }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // --- LINEER ÇALIŞMA AYARLARI ---
    private val noiseGate = 18f       // Bu seviyenin altındaki sesleri tamamen yok sayar
    private val bassCeiling = 140f    // Maksimum titreşim için gereken bas üst sınırı
    private var lastVibTime = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost: LINEAR MODE")
            .setContentText("Haptic engine following music envelope...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
        if (!isRunning) {
            initializeLinearEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun initializeLinearEngine() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = 128 // En hızlı analiz hızı
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        // Sadece derin bas frekanslarına (30Hz - 110Hz) odaklanıyoruz
                        var bassMagnitude = 0f
                        for (i in 2..8 step 2) { 
                            val mag = hypot(fft[i].toFloat(), fft[i+1].toFloat())
                            bassMagnitude = max(bassMagnitude, mag)
                        }

                        applyLinearHaptics(bassMagnitude)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyLinearHaptics(bass: Float) {
        val now = System.currentTimeMillis()

        // Müzik ile uyum için 20ms'lik çok kısa pencerelerle çalışıyoruz
        // Bu süre motorun tık-tık yapmadan akıcı titreşmesini sağlar
        if (bass > noiseGate) {
            if (now - lastVibTime > 20) {
                // Lineer Dönüşüm: Bas şiddetini 0-255 arasına oranlıyoruz
                val rawIntensity = ((bass / bassCeiling) * 255).toInt()
                val intensity = rawIntensity.coerceIn(40, 255) // Minimum vuruş hissi için 40'tan başlar

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // OneShot yerine motorun o anki şiddetini sürekli güncelleyen kısa darbeler
                    vibrator?.vibrate(VibrationEffect.createOneShot(25, intensity))
                }
                lastVibTime = now
            }
        } else {
            // Bas kesildiği anda titreşimi kes (Noise Gate aktif)
            vibrator?.cancel()
        }
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
        vibrator?.cancel()
        super.onDestroy()
    }
}
