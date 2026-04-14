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
        private const val CHANNEL_ID = "VibBoostChannel"
        private const val NOTIFICATION_ID = 1
        const val UPDATE_ACTION = "com.alissgmr.vibboost.UPDATE"
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    
    // C# Program.cs'den alınan Mühendislik Parametreleri
    private val attackSpeed = 0.85f
    private val releaseSpeed = 0.15f // Mobil LRA için 0.06'dan biraz daha hızlı bıraktık
    private val noiseGate = 15f      // Dip sesleri engellemek için eşik
    private var smoothedBass = 0f
    private var nextVibTime = 0L

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
            .setContentTitle("VibBoost Pro: DSP Aktif")
            .setContentText("C# Envelope Haptic Engine çalışıyor...")
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
                captureSize = 512 // Daha hızlı tepki süresi (düşük gecikme)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, w: ByteArray?, s: Int) {}
                    
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, s: Int) {
                        if (fft == null || !isRunning) return

                        // FFT Bins: CaptureSize 512, Rate 44.1kHz -> Bin başına ~86Hz
                        // Sadece index 2,3 (86Hz - Sub/Mid Bass) ve 4,5 (172Hz - Upper Bass) kısımlarını tarıyoruz
                        var currentPeak = 0f
                        for (i in 2 until 6 step 2) {
                            val magnitude = hypot(fft[i].toFloat(), fft[i + 1].toFloat())
                            currentPeak = max(currentPeak, magnitude)
                        }

                        // PC'deki Attack / Release Smoothing Mantığı (Sinyal Zarfı)
                        smoothedBass = if (currentPeak > smoothedBass) {
                            (smoothedBass * (1 - attackSpeed)) + (currentPeak * attackSpeed)
                        } else {
                            (smoothedBass * (1 - releaseSpeed)) + (currentPeak * releaseSpeed)
                        }

                        // Yüzdelik hesaplama (Max beklenen değer genelde 128-150 arasıdır)
                        val soundLevelPct = ((currentPeak / 130f) * 100).toInt().coerceIn(0, 100)
                        var motorPct = 0
                        
                        val currentTime = System.currentTimeMillis()

                        if (smoothedBass > noiseGate) {
                            // Dinamik Şiddet ve Süre
                            val intensity = ((smoothedBass / 130f) * 255).toInt().coerceIn(30, 255)
                            motorPct = ((intensity / 255f) * 100).toInt()
                            val duration = ((smoothedBass / 130f) * 80).toLong().coerceIn(20L, 80L)

                            // Tık-tık-tık hissini yok eden Overlap-Prevent kuralı (10ms tolerans)
                            if (currentTime >= nextVibTime - 10) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, intensity))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(duration)
                                }
                                nextVibTime = currentTime + duration
                            }
                        }

                        // UI'a Telemetri Gönder
                        broadcastTelemetry(soundLevelPct, motorPct)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var lastBroadcastTime = 0L
    private fun broadcastTelemetry(soundLvl: Int, motorLvl: Int) {
        val now = System.currentTimeMillis()
        // Saniyede 30 kereden fazla UI güncelleyip telefonu kastırmayalım (33ms)
        if (now - lastBroadcastTime > 33) {
            val intent = Intent(UPDATE_ACTION).apply {
                putExtra("soundLevel", soundLvl)
                putExtra("motorPercent", motorLvl)
            }
            sendBroadcast(intent)
            lastBroadcastTime = now
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "VibBoost Engine",
                NotificationManager.IMPORTANCE_LOW
            )
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
