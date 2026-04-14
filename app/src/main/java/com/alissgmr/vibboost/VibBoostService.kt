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

    companion object { var isRunning = false }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // --- NOTHING PHONE 1 HARDWARE AYARLARI ---
    private val NOISE_GATE = 30f       
    private val BASS_CEILING = 180f    
    private val VIB_DURATION = 150L    // Sert ve sürekli bir his için MS değeri artırıldı

    // Tık-tık engelleme (Anti-Stutter) değişkenleri
    private var lastVibTime = 0L
    private var currentAmplitude = 0

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Aktif")
            .setContentText("Arka planda bas frekansları dinleniyor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        
        // Android 14+ (API 34) Foreground Service Type zorunluluğu
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
                captureSize = Visualizer.getCaptureSizeRange()[1] // 1024 (Derin baslar için)
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        val subBass = hypot(fft[2].toFloat(), fft[3].toFloat())
                        val midBass = hypot(fft[4].toFloat(), fft[5].toFloat())
                        val rawBass = max(subBass, midBass)

                        applyRawHaptics(rawBass)
                    }
                }, Visualizer.getMaxCaptureRate(), false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyRawHaptics(bassLevel: Float) {
        if (bassLevel < NOISE_GATE) {
            vibrator?.cancel()
            currentAmplitude = 0
            return
        }

        val mappedIntensity = ((bassLevel / BASS_CEILING) * 255).toInt()
        val finalIntensity = mappedIntensity.coerceIn(1, 255)
        
        val now = System.currentTimeMillis()
        
        // --- TIK-TIK ENGELLEME ALGORİTMASI ---
        // Motor zaten çalışıyorsa ve şiddet değişimi %10'dan azsa, yeni komut gönderme.
        // Bu sayede motor fren yapıp tekrar başlamaz (tık-tık engellenir), uzun duration sayesinde kesintisiz akar.
        if (now - lastVibTime < 80 && abs(finalIntensity - currentAmplitude) < 15) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(VIB_DURATION, finalIntensity))
            lastVibTime = now
            currentAmplitude = finalIntensity
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("VibBoostChannel", "VibBoost Engine", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background execution for haptic engine"
                setShowBadge(false)
            }
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
