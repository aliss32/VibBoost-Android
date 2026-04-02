package com.alissgmr.vibboost

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.DynamicsProcessing
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
        // 1. ÖNCE BİLDİRİMİ OLUŞTUR (Kritik: Servis başlamadan hazır olmalı)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VibBoost Pro: Çalışıyor")
            .setContentText("Bas frekansları analiz ediliyor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // 2. SERVİSİ BAŞLAT (Android 14+ için tip belirterek)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Eğer izin hatası varsa burada yakalıyoruz
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
            // Session 0 (Global) üzerinden bas yakalama
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null || !isRunning) return

                        var maxBass = 0f
                        // İlk birkaç frekans aralığı basları temsil eder
                        for (i in 0 until 6 step 2) {
                            val magnitude = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            if (magnitude > maxBass) maxBass = magnitude
                        }

                        if (maxBass > 15f) {
                            val intensity = ((maxBass / 120f) * 255).toInt().coerceIn(1, 255)
                            vibrator?.vibrate(VibrationEffect.createOneShot(20L, intensity))
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
                description = "VibBoost Arka Plan Analiz Bildirimi"
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
