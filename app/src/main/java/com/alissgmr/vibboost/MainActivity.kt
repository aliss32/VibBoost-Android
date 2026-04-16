// MainActivity.kt
package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var actionBtn: Button
    private lateinit var freqLabel: TextView
    private lateinit var freqSeekBar: SeekBar
    private lateinit var shakeSpinner: Spinner
    private lateinit var themeSpinner: Spinner
    private lateinit var statusLabel: TextView
    
    private var lastIntensity = 0f
    private val easeInterpolator = DecelerateInterpolator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root Layout
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // Status/Visual Area
        statusLabel = TextView(this).apply {
            text = "VibBoost Pro"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }
        root.addView(statusLabel)

        // Main Action Button
        actionBtn = Button(this).apply {
            text = if (VibBoostService.isRunning) "STOP ENGINE" else "START BASS ENGINE"
            setPadding(80, 40, 80, 40)
            setOnClickListener {
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    text = "STOP ENGINE"
                } else {
                    stopService(intent)
                    text = "START BASS ENGINE"
                }
            }
        }
        root.addView(actionBtn)

        // Spacer
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })

        // Selection Menus Container (Bottom)
        val menuContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 40)
        }

        // Shake Mode Spinner
        val shakeOptions = arrayOf("Doğrusal (Varsayılan)", "Ease Out")
        shakeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, shakeOptions)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    VibBoostService.shakeMode = pos 
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        // Theme Spinner
        val themeOptions = arrayOf("Karanlık Tema", "Beyaz Tema")
        themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themeOptions)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    applyTheme(pos == 1)
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        menuContainer.addView(shakeSpinner)
        menuContainer.addView(themeSpinner)
        root.addView(menuContainer)

        // Hz Label
        freqLabel = TextView(this).apply {
            text = "Hedef Bass Frekansı: ${VibBoostService.targetFrequency.toInt()} Hz"
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        root.addView(freqLabel)

        // Hz SeekBar (At the very bottom with side margins)
        freqSeekBar = SeekBar(this).apply {
            max = 210 // 40 to 250
            progress = VibBoostService.targetFrequency.toInt() - 40
            setPadding(60, 30, 60, 30) // Horizontal margins
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val freq = p + 40f
                    VibBoostService.targetFrequency = freq
                    freqLabel.text = "Hedef Bass Frekansı: ${freq.toInt()} Hz"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(freqSeekBar)

        setContentView(root)
        applyTheme(false) // Default Dark

        // Visualizer Listener
        VibBoostService.hapticListener = { intensity, _ ->
            runOnUiThread {
                val targetScale = 1.0f + (intensity / 255f) * 0.5f
                if (VibBoostService.shakeMode == 1) { // Ease Out
                    actionBtn.animate()
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(100)
                        .setInterpolator(easeInterpolator)
                        .start()
                } else { // Linear
                    actionBtn.scaleX = targetScale
                    actionBtn.scaleY = targetScale
                }
            }
        }

        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.RECORD_AUDIO, 
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS
        ), 1)
    }

    private fun applyTheme(isWhite: Boolean) {
        val bgColor = if (isWhite) Color.WHITE else Color.parseColor("#121212")
        val textColor = if (isWhite) Color.BLACK else Color.WHITE
        
        root.setBackgroundColor(bgColor)
        statusLabel.setTextColor(textColor)
        freqLabel.setTextColor(textColor)
        
        // Button style adjustment
        actionBtn.setTextColor(if (isWhite) Color.WHITE else Color.WHITE)
        val btnBg = GradientDrawable().apply {
            cornerRadius = 20f
            setColor(if (isWhite) Color.parseColor("#333333") else Color.parseColor("#BB86FC"))
        }
        actionBtn.background = btnBg
    }
}

// VibBoostService.kt
package com.alissgmr.vibboost

import android.app.*
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.hypot

class VibBoostService : Service() {

    companion object {
        var isRunning = false
        var hapticListener: ((intensity: Int, duration: Long) -> Unit)? = null
        var targetFrequency = 80f
        var shakeMode = 0 // 0: Linear, 1: Ease Out
        const val ACTION_STOP = "com.alissgmr.vibboost.STOP"
    }

    private var visualizer: Visualizer? = null
    private var vibrator: Vibrator? = null
    private val BASS_CEILING = 180f
    private var lastVibTime = 0L

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

        val notification = NotificationCompat.Builder(this, "VibBoostChannel")
            .setContentTitle("VibBoost Aktif")
            .setContentText("Motor Çalışıyor...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1, notification)
        isRunning = true
        startEngine()
        return START_STICKY
    }

    private fun startEngine() {
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveform(v: Visualizer?, w: ByteArray?, r: Int) {}
                    override fun onFft(v: Visualizer?, fft: ByteArray?, r: Int) {
                        if (fft == null) return
                        
                        val index = (targetFrequency / (r / 2000f)).toInt().coerceIn(0, fft.size / 2 - 1)
                        val magnitude = hypot(fft[index * 2].toDouble(), fft[index * 2 + 1].toDouble()).toFloat()
                        
                        if (magnitude > 30f) {
                            val intensity = ((magnitude / BASS_CEILING) * 255).toInt().coerceIn(0, 255)
                            val duration = 120L
                            
                            val now = System.currentTimeMillis()
                            if (now - lastVibTime > 80) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, intensity))
                                }
                                lastVibTime = now
                                hapticListener?.invoke(intensity, duration)
                            }
                        }
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
        visualizer?.release()
        vibrator?.cancel()
        super.onDestroy()
    }
}
