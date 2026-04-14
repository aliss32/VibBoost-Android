package com.alissgmr.vibboost

import android.app.*
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.max

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        const val UPDATE_ACTION = "com.alissgmr.vibboost.UPDATE"
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // --- SERT ÇALIŞMA PARAMETRELERİ ---
    private val noiseGate = 22f      // Bu eşiğin altı tamamen sessiz (Tık-tık engelleyici)
    private val maxBassRange = 130f  // Bas doygunluk sınırı
    private var lastVibTime = 0L

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Vibrator::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Pro: HARD MODE")
            .setContentText("Haptic Engine: Aggressive Triggering")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        
        startForeground(1, notification)
        if (!isRunning) {
            startHardEngine()
            isRunning = true
        }
        return START_STICKY
    }

    private fun startHardEngine() {
        try {
            // Visualizer 0 (Global) bazen diğer uygulamalar tarafından kilitlenebilir.
            // Bu yüzden hata payını azaltmak için gecikmeli başlatıyoruz.
            visualizer = Visualizer(0).apply {
                captureSize = 128 // En keskin tepki için örnekleme boyutunu düşürdük
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null) return

                        // C# mantığındaki BiQuad mantığını Android'in FFT binlerinde simüle ediyoruz
                        // Sadece en alt frekanslara odaklan (40Hz - 100Hz)
                        var currentBass = 0f
                        for (i in 2..6 step 2) { 
                            val mag = hypot(fft[i].toFloat(), fft[i+1].toFloat())
                            currentBass = max(currentBass, mag)
                        }

                        processHardHaptics(currentBass)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun processHardHaptics(bass: Float) {
        val currentTime = System.currentTimeMillis()
        
        // --- SERT TETİKLEME MANTIĞI ---
        // Bas seviyesi noiseGate'i geçtiği anda motoru "tok" bir vuruşla çalıştırır.
        if (bass > noiseGate) {
            // Eğer son vuruşun üzerinden 35ms geçmediyse yeni vuruş yapma (Tık-tık önleyici)
            if (currentTime - lastVibTime > 35) {
                // Şiddeti lineer değil, logaritmik artırıyoruz (Daha sert hissetmek için)
                val rawIntensity = ((bass / maxBassRange) * 255).toInt()
                val hardIntensity = if (rawIntensity > 150) 255 else rawIntensity.coerceIn(80, 255)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // 40ms sabit, sert bir vuruş. Sönümlenme beklemiyoruz.
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, hardIntensity))
                }
                lastVibTime = currentTime
            }
        }

        // UI Telemetri Gönderimi (Visualizer Fix)
        val intent = Intent(UPDATE_ACTION).apply {
            putExtra("lvl", ((bass / maxBassRange) * 100).toInt().coerceIn(0, 100))
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
        visualizer?.enabled = false
        visualizer?.release()
        super.onDestroy()
    }
}
