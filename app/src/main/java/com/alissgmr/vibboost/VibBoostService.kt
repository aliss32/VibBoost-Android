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
    
    // --- NOTHING PHONE 1 RAW HAPTIC MÜHENDİSLİĞİ ---
    private val NOISE_GATE = 30f       // Bu genliğin altı = Kesin Sessizlik (Sıfır tolerans)
    private val BASS_CEILING = 180f    // %100 motor gücü için referans bas tepe noktası
    private val VIB_DURATION = 40L     // Callback hızına tam oturan, vuruntuyu (tık-tık) önleyen süre

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost: RAW BASS MODE")
            .setContentText("Direct hardware mapping active...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
        if (!isRunning) {
            initializeRawEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun initializeRawEngine() {
        try {
            visualizer = Visualizer(0).apply {
                // Derin basları (30-100Hz) görebilmek için çözünürlüğü maksimuma (genelde 1024) çıkarıyoruz.
                captureSize = Visualizer.getCaptureSizeRange()[1] 
                
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        // 1024 Capture Size ile alt frekans indeksleri:
                        // Index 2,3 (Bin 1) = ~43Hz (Sub-bass)
                        // Index 4,5 (Bin 2) = ~86Hz (Mid-bass / Kick)
                        val subBass = hypot(fft[2].toFloat(), fft[3].toFloat())
                        val midBass = hypot(fft[4].toFloat(), fft[5].toFloat())
                        
                        // En güçlü bas frekansını baz alıyoruz
                        val rawBass = max(subBass, midBass)

                        applyRawHaptics(rawBass)
                    }
                }, Visualizer.getMaxCaptureRate(), false, true) // Maksimum hızda, kesintisiz veri akışı
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyRawHaptics(bassLevel: Float) {
        // Yumuşatma yok: Eşik altındaysa motoru anında durdur (bıçak gibi kes)
        if (bassLevel < NOISE_GATE) {
            vibrator?.cancel()
            return
        }

        // Lineer Dönüşüm: Bas şiddetini motorun çalışma yüzdesine (%1 - %100 arası -> 1-255) doğrudan haritala
        val mappedIntensity = ((bassLevel / BASS_CEILING) * 255).toInt()
        val finalIntensity = mappedIntensity.coerceIn(1, 255)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Önceki titreşim tamamen sönümlenmeden yenisi geldiği için "tık-tık" hissi kaybolur, 
            // motor genliği müziğe göre pürüzsüzce artıp azalır.
            val effect = VibrationEffect.createOneShot(VIB_DURATION, finalIntensity)
            vibrator?.vibrate(effect)
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
