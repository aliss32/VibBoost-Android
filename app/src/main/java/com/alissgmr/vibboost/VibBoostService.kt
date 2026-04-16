package com.alissgmr.vibboost

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.max

class VibBoostService : Service() {

    companion object { 
        var isRunning = false 
        var hapticListener: ((intensity: Int, duration: Long) -> Unit)? = null
        const val ACTION_STOP = "com.alissgmr.vibboost.STOP"
        var noiseGateThreshold = 25f // UI üzerinden kontrol edilecek dinamik eşik değeri
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
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
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, VibBoostService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Pro")
            .setContentText("Motor Hazır ve Dinliyor")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DURDUR", pendingStopIntent)
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
                captureSize = 256
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return
                        val rawBass = max(hypot(fft[2].toFloat(), fft[3].toFloat()), hypot(fft[4].toFloat(), fft[5].toFloat()))
                        
                        // Sabit NOISE_GATE yerine, arayüzden ayarlanan dinamik değeri kullanıyoruz
                        if (rawBass < noiseGateThreshold) {
                            vibrator?.cancel() 
                            hapticListener?.invoke(0, 0L)
                            return
                        }

                        // Titreşim Gücü (0 - 255 | %0 - %100)
                        val finalIntensity = ((rawBass / BASS_CEILING) * 255).toInt().coerceIn(0, 255)
                        
                        // Titreşim Süresi (Anlık bass şiddetine göre 20ms - 1000ms arası)
                        val dynamicDuration = ((rawBass / BASS_CEILING) * 1000L).toLong().coerceIn(20L, 1000L)

                        val now = System.currentTimeMillis()
                        if (now - lastVibTime >= currentDuration || finalIntensity > currentAmplitude + 20) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(dynamicDuration, finalIntensity))
                                lastVibTime = now
                                currentAmplitude = finalIntensity
                                currentDuration = dynamicDuration
                            }
                        }
                        
                        hapticListener?.invoke(finalIntensity, dynamicDuration)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel("VibBoostChannel", "VibBoost Engine", NotificationManager.IMPORTANCE_LOW)
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
