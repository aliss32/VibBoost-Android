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
        
        // Artık hedef frekans değil, "Gate" (Eşik) yüzdesi tutuyoruz (0 ile 100 arası)
        var gateThresholdPercent = 30f 
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // Ses şiddetinin maksimum varsayılan tavanı
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

                        // FFT index hesaplamaları (Her bin ~43Hz)
                        // index = bin * 2 (Real), index = bin * 2 + 1 (Imaginary)
                        
                        // 1. SUB-BASS (0-43 Hz) -> Bin 1
                        val subBassMag = hypot(fft[2].toFloat(), fft[3].toFloat())
                        
                        // 2. PUNCH-BASS (43-86 Hz) -> Bin 2
                        val punchBassMag = hypot(fft[4].toFloat(), fft[5].toFloat())
                        
                        // 3. MID-BASS (86-215 Hz) -> Bin 3, 4, 5 (En yükseğini al)
                        var midBassMag = 0f
                        for (i in 3..5) {
                            val mag = hypot(fft[i * 2].toFloat(), fft[i * 2 + 1].toFloat())
                            if (mag > midBassMag) midBassMag = mag
                        }

                        // Hangi frekans baskınsa onu seçiyoruz
                        var dominantMag = 0f
                        var targetIntensity = 0
                        var targetDuration = 0L

                        if (subBassMag > punchBassMag && subBassMag > midBassMag) {
                            dominantMag = subBassMag
                            // Sub-Bass Karakteri: %100 Güç (255), Uzun süre
                            targetIntensity = ((subBassMag / BASS_CEILING) * 255).toInt().coerceIn(0, 255)
                            targetDuration = ((subBassMag / BASS_CEILING) * 120L).toLong().coerceIn(60L, 120L)
                        } 
                        else if (punchBassMag > subBassMag && punchBassMag > midBassMag) {
                            dominantMag = punchBassMag
                            // Punch-Bass Karakteri: %80 Güç (Max ~204), Kısa sert vuruş
                            targetIntensity = ((punchBassMag / BASS_CEILING) * 204).toInt().coerceIn(0, 204)
                            targetDuration = ((punchBassMag / BASS_CEILING) * 50L).toLong().coerceIn(30L, 50L)
                        } 
                        else {
                            dominantMag = midBassMag
                            // Mid-Bass Karakteri: %50 Güç (Max ~127), Çok kısa pürüzsüz vuruş
                            targetIntensity = ((midBassMag / BASS_CEILING) * 127).toInt().coerceIn(0, 127)
                            targetDuration = 20L // Sabit kısa
                        }

                        // GATE (EŞİK) KONTROLÜ
                        // Kullanıcının seçtiği yüzdeye göre minimum şiddet eşiği hesaplanıyor
                        val activeGate = (gateThresholdPercent / 100f) * BASS_CEILING
                        
                        if (dominantMag < activeGate || dominantMag < 10f) { // 10f dip gürültüsü
                            // Gate'i geçemedi, titreşimi iptal et
                            currentAmplitude = 0
                            hapticListener?.invoke(0, 0L)
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
                        
                        hapticListener?.invoke(targetIntensity, targetDuration)
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
