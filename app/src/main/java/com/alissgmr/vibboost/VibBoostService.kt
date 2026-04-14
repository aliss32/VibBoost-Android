package com.alissgmr.vibboost

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        const val UPDATE_ACTION = "com.alissgmr.vibboost.UPDATE"
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // C# Mühendislik Parametreleri (Dinamik Zarf Takibi)
    private var smoothedIntensity = 0f
    private val attackStep = 0.4f  // Bas vurduğunda motorun tırmanma hızı
    private val releaseStep = 0.08f // Bas bittiğinde motorun sönümlenme hızı
    private val noiseGate = 12f     // Tık-tık hissini önleyen alt eşik

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost DSP Active")
            .setContentText("Haptic Engine: Fluid Mode")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
        if (!isRunning) {
            startDrsEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun startDrsEngine() {
        try {
            // Session 0 (Global) bazen cihaz üreticisi tarafından kısıtlanır. 
            // Eğer hala ses gelmezse, müzik çalar açıkken servisi başlatmayı dene.
            visualizer = Visualizer(0).apply {
                captureSize = 256 // Daha dar pencere = daha hızlı tepki (Low Latency)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        // 40Hz - 120Hz aralığını hedefliyoruz (Sub-bass ve Kick)
                        var rawBass = 0f
                        for (i in 2..8 step 2) {
                            val mag = hypot(fft[i].toFloat(), fft[i+1].toFloat())
                            if (mag > rawBass) rawBass = mag
                        }

                        // ATTACK & RELEASE LOGIC (C# Dosyasındaki Mühendislik)
                        // Bu kısım motorun "kesik kesik" çalışmasını engeller
                        if (rawBass > smoothedIntensity) {
                            smoothedIntensity += (rawBass - smoothedIntensity) * attackStep
                        } else {
                            smoothedIntensity -= (smoothedIntensity - rawBass) * releaseStep
                        }

                        processHaptics(rawBass)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun processHaptics(currentRaw: Float) {
        // Noise Gate: Belirli bir seviyenin altındaki parazitleri motora gönderme
        val finalMag = if (smoothedIntensity < noiseGate) 0f else smoothedIntensity
        
        // Motor Yüzdesi Hesaplama (0-255 arası şiddet)
        val intensity = (finalMag * 1.8f).toInt().coerceIn(0, 255)
        
        // --- TIK-TIK ÇÖZÜMÜ ---
        // Saniyede onlarca kez vibrator.vibrate() çağırmak yerine 
        // sadece şiddet değiştiğinde çok kısa süreli ama yüksek frekanslı 
        // 'akış' sağlıyoruz.
        if (intensity > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 40ms'lik periyotlar kullanıyoruz ki bir sonraki FFT verisiyle 
                // "overlap" (örtüşme) sağlansın. Bu, motorun durmasını engeller.
                vibrator?.vibrate(VibrationEffect.createOneShot(45, intensity))
            }
        }

        // UI Telemetri Gönderimi
        val intent = Intent(UPDATE_ACTION).apply {
            putExtra("in", (currentRaw * 0.8f).toInt().coerceIn(0, 100))
            putExtra("out", (intensity / 2.55f).toInt())
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("VibBoostChannel", "VibBoost", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    override fun onDestroy() {
        isRunning = false
        visualizer?.release()
        super.onDestroy()
    }
}
