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

    companion object { 
        var isRunning = false 
        // UI'ın anlık verileri okuyabilmesi için dinleyici
        var hapticListener: ((intensity: Int, duration: Long) -> Unit)? = null
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // --- NOTHING PHONE 1 HARDWARE AYARLARI ---
    private val NOISE_GATE = 30f       
    private val BASS_CEILING = 180f    

    // Tık-tık engelleme (Anti-Stutter) değişkenleri
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
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Aktif")
            .setContentText("Arka planda bas frekansları dinleniyor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
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
                captureSize = Visualizer.getCaptureSizeRange()[1]
                
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
            // Ses kesildiğinde butonu sıfırlamak için UI'a 0 gönder
            hapticListener?.invoke(0, 0L)
            return
        }

        val mappedIntensity = ((bassLevel / BASS_CEILING) * 255).toInt()
        val finalIntensity = mappedIntensity.coerceIn(1, 255)
        
        // --- DİNAMİK SÜRE (DURATION) AYARLAMASI ---
        // Şiddete göre titreşim süresini belirliyoruz
        val dynamicDuration = when {
            finalIntensity > 200 -> 200L // En sert vuruşlar
            finalIntensity > 150 -> 150L // Orta-sert
            finalIntensity > 100 -> 100L // Normal
            else -> 60L                  // Hafif tıkırtılar
        }
        
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastVibTime
        
        // --- GELİŞMİŞ TIK-TIK ENGELLEYİCİ ---
        // Eğer motor hala bir önceki titreşimi gerçekleştiriyorsa ve yeni gelen ses
        // aniden çok yüksek bir patlama değilse, araya girip motoru frenleme.
        if (timeSinceLast < currentDuration && finalIntensity < currentAmplitude + 25) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(dynamicDuration, finalIntensity))
            lastVibTime = now
            currentAmplitude = finalIntensity
            currentDuration = dynamicDuration
            
            // UI'ı güncellemek için veriyi yolla
            hapticListener?.invoke(finalIntensity, dynamicDuration)
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
        hapticListener = null // Hafıza sızıntısını önle
        visualizer?.enabled = false
        visualizer?.release()
        vibrator?.cancel()
        super.onDestroy()
    }
}
