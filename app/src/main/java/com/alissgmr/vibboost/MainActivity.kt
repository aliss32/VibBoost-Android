package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var actionBtn: Button
    private lateinit var rootLayout: LinearLayout
    private var currentHue = 0f // Gökkuşağı için kaldığı yeri tutar
    
    // Anlık hedef değerler
    private var targetIntensity = 0
    private var targetDuration = 0L
    
    // Smooth takip değişkenleri (Ease-out için)
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f // Ease-out yumuşaklığı (0.1 - 0.3 ideal)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupUI()
        startAnimationLoop() // 120Hz Döngüsünü başlat
    }

    private fun setupUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK) // Varsayılan: AMOLED Siyah
        }

        // Tema Seçici (Alt Kısım)
        val themeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }

        val themes = mapOf(
            "AMOLED" to "#000000",
            "WHITE" to "#FFFFFF",
            "DEEP BLUE" to "#0D47A1",
            "FOREST" to "#1B5E20"
        )

        themes.forEach { (name, color) ->
            val tBtn = Button(this).apply {
                text = name
                textSize = 10f
                setOnClickListener { rootLayout.setBackgroundColor(Color.parseColor(color)) }
            }
            themeLayout.addView(tBtn)
        }

        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 250).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.DKGRAY)
            }
            text = "START ENGINE"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }

        rootLayout.addView(actionBtn)
        rootLayout.addView(themeLayout)
        setContentView(rootLayout)
        
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE, Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun toggleService() {
        val intent = Intent(this, VibBoostService::class.java)
        if (!VibBoostService.isRunning) {
            startService(intent)
            actionBtn.text = "ENGINE ACTIVE"
        } else {
            stopService(intent)
            targetIntensity = 0
            actionBtn.text = "START ENGINE"
        }
    }

    // 120Hz Akıcı Render Döngüsü
    private fun startAnimationLoop() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                updateVisuals()
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    private fun updateVisuals() {
        if (!VibBoostService.isRunning && targetIntensity == 0) {
            actionBtn.translationX = 0f
            actionBtn.translationY = 0f
            return
        }

        // 1. Ease-Out Yumuşatma (Current value moves towards target)
        currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor

        // 2. Shake Efekti (Intensity ile doğru orantılı)
        if (currentDisplayIntensity > 5) {
            val shake = (currentDisplayIntensity / 255f) * 35f
            actionBtn.translationX = ((Math.random() - 0.5) * 2 * shake).toFloat()
            actionBtn.translationY = ((Math.random() - 0.5) * 2 * shake).toFloat()
            
            // 3. Rainbow Efekti (Sadece bass varken döner)
            // Güç arttıkça renk daha hızlı döner
            currentHue = (currentHue + (currentDisplayIntensity / 50f)) % 360f
            val color = Color.HSVToColor(floatArrayOf(currentHue, 0.8f, 0.9f))
            (actionBtn.background as GradientDrawable).setColor(color)
            
            actionBtn.text = "PWR: ${targetIntensity}\n${targetDuration}ms"
        } else {
            // Bass yokken durul
            actionBtn.translationX = 0f
            actionBtn.translationY = 0f
            if (VibBoostService.isRunning) actionBtn.text = "LISTENING..."
        }
    }

    override fun onResume() {
        super.onResume()
        VibBoostService.hapticListener = { intensity, duration ->
            targetIntensity = intensity
            targetDuration = duration
        }
    }

    override fun onPause() {
        super.onPause()
        VibBoostService.hapticListener = null
    }
}
