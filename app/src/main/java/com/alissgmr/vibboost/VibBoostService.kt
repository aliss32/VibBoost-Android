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
        // Listener'a Hz parametresi eklendi
        var hapticListener: ((intensity: Int, duration: Long, hz: Int) -> Unit)? = null
        const val ACTION_STOP = "com.alissgmr.vibboost.STOP"
        
        var gateThresholdPercent = 30f 
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    private val BASS_CEILING = 150f    

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
        // ... (Bu kısımdaki Notification kodları senin orjinal kodunla aynı kalabilir)
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
            .setContentText("Dinamik Motor Aktif")
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
            initializeDynamicEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun initializeDynamicEngine() {
        try {
            visualizer = Visualizer(0).apply {
                val maxCaptureSize = Visualizer.getCaptureSizeRange()[1]
                captureSize = maxCaptureSize 

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || v == null) return

                        var maxMag = 0f
                        var dominantBin = 0

                        // Sadece Bass frekanslarını (Bin 1 ile 5 arası, yani ~43Hz ile ~215Hz arası) tara
                        for (i in 1..5) {
                            val mag = hypot(fft[i * 2].toFloat(), fft[i * 2 + 1].toFloat())
                            if (mag > maxMag) {
                                maxMag = mag
                                dominantBin = i
                            }
                        }

                        var targetIntensity = 0
                        var targetDuration = 0L
                        val dominantHz = dominantBin * 43 // Dinamik Hz hesaplaması

                        if (dominantBin == 1) {
                            // Sub-Bass Karakteri (0-43 Hz)
                            targetIntensity = ((maxMag / BASS_CEILING) * 255).toInt().coerceIn(0, 255)
                            targetDuration = ((maxMag / BASS_CEILING) * 120L).toLong().coerceIn(60L, 120L)
                        } else if (dominantBin == 2) {
                            // Punch-Bass Karakteri (43-86 Hz)
                            targetIntensity = ((maxMag / BASS_CEILING) * 204).toInt().coerceIn(0, 204)
                            targetDuration = ((maxMag / BASS_CEILING) * 50L).toLong().coerceIn(30L, 50L)
                        } else if (dominantBin in 3..5) {
                            // Mid-Bass Karakteri (86-215 Hz)
                            targetIntensity = ((maxMag / BASS_CEILING) * 127).toInt().coerceIn(0, 127)
                            targetDuration = 20L // Sabit kısa
                        }

                        // KULLANICI EŞİĞİ (GATE)
                        val activeGate = (gateThresholdPercent / 100f) * BASS_CEILING
                        
                        // --- TIKTIK (PHANTOM VIBRATION) ÇÖZÜMÜ ---
                        // 1. maxMag kullanıcının belirlediği Gate'i geçmeli
                        // 2. maxMag minimum 35f olmalı (Donanımsal dip gürültüleri tamamen yok eder)
                        // 3. Hesaplanan güç (targetIntensity) en az 40 olmalı (Çok zayıf "tık"lama titreşimlerini iptal eder)
                        if (maxMag < activeGate || maxMag < 35f || targetIntensity < 40) {
                            currentAmplitude = 0
                            hapticListener?.invoke(0, 0L, 0)
                            return
                        }

                        // Titreşimi tetikle
                        val now = System.currentTimeMillis()
                        if (now - lastVibTime >= currentDuration || targetIntensity > currentAmplitude + 30) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator?.vibrate(VibrationEffect.createOneShot(targetDuration, targetIntensity))
                                lastVibTime = now
                                currentAmplitude = targetIntensity
                                currentDuration = targetDuration
                            }
                        }
                        
                        // Hz bilgisini de UI'a gönder
                        hapticListener?.invoke(targetIntensity, targetDuration, dominantHz)
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
