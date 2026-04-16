package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// ... (İmportları aynı bırak)

class MainActivity : AppCompatActivity() {

    private lateinit var actionBtn: Button
    private lateinit var rootLayout: LinearLayout
    
    // UI Bileşenleri
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var shakeSpinner: Spinner
    private lateinit var themeSpinner: Spinner

    // Görsel Efekt ve Durum Değişkenleri
    private var currentHue = 0f
    private var targetIntensity = 0
    private var targetDuration = 0L
    private var targetHz = 0 // YENİ EKLENDİ
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f 
    
    private var isWhiteTheme = false 
    private var shakeMode = 0 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        attachListener()
        startAnimationLoop() 
    }

    private fun setupUI() {
        // ... (Senin orjinal rootLayout ve diğer UI kurulumların aynı kalacak, sadece butona kalın yazı ekliyoruz)
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }

        // --- 1. ANA BUTON (Üst Kısım) ---
        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 260).toInt()
            val btnParams = LinearLayout.LayoutParams(size, size)
            btnParams.setMargins(0, 0, 0, 60)
            layoutParams = btnParams
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
            }
            text = if (VibBoostService.isRunning) "ENGINE ACTIVE" else "START ENGINE"
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD // YAZIYI KALINLAŞTIRDIK
            setOnClickListener { toggleService() }
        }

        // (Diğer setup UI kodların tamamen aynı kalacak: Spinner, ThresholdLabel vb.)
        // ... (Kod kalabalığı olmaması için atlıyorum, kendi setupUI bloğunun geri kalanını bozma)
        // ...
    }

    // ... (createCustomAdapter, updateUIColors vb. aynı kalsın)

    private fun attachListener() {
        // Listener yapısını 3 parametre (intensity, duration, hz) alacak şekilde güncelledik
        VibBoostService.hapticListener = { intensity, duration, hz ->
            targetIntensity = intensity
            targetDuration = duration
            targetHz = hz
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
            targetHz = 0 // Sıfırladık
            currentDisplayIntensity = 0f
            actionBtn.text = "START ENGINE"
            actionBtn.translationX = 0f
            actionBtn.translationY = 0f
            (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#333333"))
        }
    }

    // ... (startAnimationLoop aynı kalacak)

    private fun updateVisuals() {
        if (shakeMode == 0) {
            currentDisplayIntensity = targetIntensity.toFloat()
        } else {
            currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor
        }

        // Sadece güçlü titreşimler ekrana yansısın
        if (currentDisplayIntensity > 2 && targetIntensity > 0) {
            val shake = (currentDisplayIntensity / 255f) * 45f
            actionBtn.translationX = ((Math.random() - 0.5) * 2 * shake).toFloat()
            actionBtn.translationY = ((Math.random() - 0.5) * 2 * shake).toFloat()
             
            currentHue = (currentHue + (currentDisplayIntensity / 30f)) % 360f
            val color = Color.HSVToColor(floatArrayOf(currentHue, 0.8f, 1.0f))
            (actionBtn.background as GradientDrawable).setColor(color)
            
            // FREKANS BİLGİSİ EKLENDİ
            actionBtn.text = "GÜÇ: ${targetIntensity}\nSÜRE: ${targetDuration}ms\nFREKANS: ~${targetHz}Hz"
        } else {
            if (VibBoostService.isRunning) {
                actionBtn.text = "DİNLENİYOR..."
                actionBtn.translationX = 0f
                actionBtn.translationY = 0f
                (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#333333")) // Dinlenirken orijinal renge dönsün
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachListener()
    }
}
