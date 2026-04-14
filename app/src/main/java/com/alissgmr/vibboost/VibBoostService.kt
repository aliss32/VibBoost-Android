package com.alissgmr.vibboost

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        private const val CHANNEL_ID = "VibBoostChannel"
        private const val NOTIFICATION_ID = 1
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // Nothing Phone 1 Optimizasyonu İçin Değişkenler
    private var nextVibTime = 0L
    private var lastBass = 0f

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
            .setContentTitle("VibBoost Pro: Aktif")
            .setContentText("Haptic Motor devrede, baslar analiz ediliyor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isRunning) {
            startEngine()
            isRunning = true
        }

        return START_STICKY
    }

    private fun startEngine() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null || !isRunning) return

                        var currentMaxBass = 0f
                        // Sadece en alt frekanslara (Sub-bass ve Mid-bass) odaklan
                        for (i in 0 until 8 step 2) {
                            val magnitude = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            if (magnitude > currentMaxBass) currentMaxBass = magnitude
                        }

                        // SİNYAL YUMUŞATMA (Low-Pass Filter)
                        // Anlık sıçramaları engeller, dalga gibi pürüzsüz bir bas hissi verir
                        val smoothedBass = (currentMaxBass * 0.6f) + (lastBass * 0.4f)
                        lastBass = smoothedBass

                        val currentTime = System.currentTimeMillis()

                        // 20f eşiğini geçen baslarda ve MOTOR MÜSAİTSE tetikle
                        if (smoothedBass > 20f && currentTime >= nextVibTime) {
                            
                            // 1. DİNAMİK ŞİDDET (Amplitude): 15 ile 255 arası
                            val intensity = ((smoothedBass / 130f) * 255).toInt().coerceIn(15, 255)
                            
                            // 2. DİNAMİK SÜRE (Duration): Bas güçlüyle daha uzun sarsıntı (30ms - 120ms)
                            val duration = ((smoothedBass / 130f) * 120).toLong().coerceIn(30L, 120L)
                            
                            // Haptic Motoru Ateşle
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(duration, intensity))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator?.vibrate(duration)
                            }
                            
                            // MOTOR KİLİDİ (Cooldown): 
                            // Motor hareketini tamamlayana kadar yeni sinyal alma. "Tık tık tık" hissini öldüren satır budur!
                            nextVibTime = currentTime + duration
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "VibBoost Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VibBoost Arka Plan Analizi"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        visualizer?.apply { enabled = false; release() }
        super.onDestroy()
    }
}
