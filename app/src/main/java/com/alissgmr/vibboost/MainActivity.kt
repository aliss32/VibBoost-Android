package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Choreographer
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var actionBtn: Button
    private lateinit var rootLayout: LinearLayout
    
    // Frekans Arayüz Değişkenleri
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdSeekBar: SeekBar

    // Görsel Efekt Değişkenleri
    private var currentHue = 0f
    private var targetIntensity = 0
    private var targetDuration = 0L
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f // 120Hz için akıcı ease-out

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        attachListener() // Servis verilerini dinlemeye başla
        startAnimationLoop() 
    }

    private fun setupUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK) // AMOLED Black
        }

        // --- BASS FREKANSI SEÇİCİ (SeekBar) ---
        thresholdLabel = TextView(this).apply {
            text = "Hedef Bass Frekansı: ${VibBoostService.targetFrequency.toInt()} Hz"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        thresholdSeekBar = SeekBar(this).apply {
            max = 210 // 40 Hz ile 250 Hz arası
            progress = VibBoostService.targetFrequency.toInt() - 40
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val freq = progress + 40f
                    VibBoostService.targetFrequency = freq
                    thresholdLabel.text = "Hedef Bass Frekansı: ${freq.toInt()} Hz"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            setPadding(0, 0, 0, 60)
        }

        // --- TEMA SEÇİCİ ---
        val themeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
        }
        
        val colors = mapOf("AMOLED" to "#000000", "WHITE" to "#FFFFFF", "OCEAN" to "#001F3F", "DEEP" to "#1A1A1A")
        colors.forEach { (name, hex) ->
            themeBar.addView(Button(this).apply {
                text = name
                textSize = 10f
                setOnClickListener { 
                    rootLayout.setBackgroundColor(Color.parseColor(hex))
                    // Beyaz temaya geçilirse frekans yazısını siyah yap ki okunsun
                    thresholdLabel.setTextColor(if(hex == "#FFFFFF") Color.BLACK else Color.WHITE)
                }
            })
        }

        // --- ANA BUTON ---
        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 260).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
            }
            text = if (VibBoostService.isRunning) "ENGINE ACTIVE" else "START ENGINE"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }

        // Bileşenleri ekrana ekle
        rootLayout.addView(thresholdLabel)
        rootLayout.addView(thresholdSeekBar)
        rootLayout.addView(actionBtn)
        rootLayout.addView(themeBar)
        
        setContentView(rootLayout)
        
        // İzinleri iste
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    private fun attachListener() {
        VibBoostService.hapticListener = { intensity, duration ->
            targetIntensity = intensity
            targetDuration = duration
        }
    }

    private fun toggleService() {
        val intent = Intent(this, VibBoostService::class.java)
        if (!VibBoostService.isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            attachListener()
            actionBtn.text = "ENGINE ACTIVE"
        } else {
            stopService(intent)
            targetIntensity = 0
            currentDisplayIntensity = 0f
            actionBtn.text = "START ENGINE"
            actionBtn.translationX = 0f
            actionBtn.translationY = 0f
            (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#333333"))
        }
    }

    // 120Hz Akıcı Animasyon Döngüsü
    private fun startAnimationLoop() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (VibBoostService.isRunning || currentDisplayIntensity > 0.1f) {
                    updateVisuals()
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    private fun updateVisuals() {
        // Ease-out interpolasyon
        currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor

        if (currentDisplayIntensity > 2) {
            // Shake efekti (Akıcı)
            val shake = (currentDisplayIntensity / 255f) * 40f
            actionBtn.translationX = ((Math.random() - 0.5) * 2 * shake).toFloat()
            actionBtn.translationY = ((Math.random() - 0.5) * 2 * shake).toFloat()
            
            // Rainbow efekti
            currentHue = (currentHue + (currentDisplayIntensity / 40f)) % 360f
            val color = Color.HSVToColor(floatArrayOf(currentHue, 0.8f, 1.0f))
            (actionBtn.background as GradientDrawable).setColor(color)
            
            actionBtn.text = "PWR: ${targetIntensity}\nTIME: ${targetDuration}ms"
        } else {
            if (VibBoostService.isRunning) {
                actionBtn.text = "LISTENING..."
                actionBtn.translationX = 0f
                actionBtn.translationY = 0f
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachListener()
    }
}
