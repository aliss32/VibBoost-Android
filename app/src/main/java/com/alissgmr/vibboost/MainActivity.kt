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

        // Temalar
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
                setOnClickListener { rootLayout.setBackgroundColor(Color.parseColor(hex)) }
            })
        }

        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 260).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
            }
            text = "START ENGINE"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }

        rootLayout.addView(actionBtn)
        rootLayout.addView(themeBar)
        setContentView(rootLayout)
        
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE, Manifest.permission.POST_NOTIFICATIONS), 1)
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
            startService(intent)
            attachListener() // Başlatınca tekrar bağla (Bug çözümü)
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
            
            // Rainbow efekti (Kaldığı yerden devam eder)
            currentHue = (currentHue + (currentDisplayIntensity / 40f)) % 360f
            val color = Color.HSVToColor(floatArrayOf(currentHue, 0.8f, 1.0f))
            (actionBtn.background as GradientDrawable).setColor(color)
            
            actionBtn.text = "PWR: ${targetIntensity}\nTIME: ${targetDuration}ms"
        } else {
            if (VibBoostService.isRunning) {
                actionBtn.text = "LISTENING..."
                actionBtn.translationX = 0f
                actionBtn.translationY = 0f
                // Rainbow'u bass yokken olduğu renkte sabit tut
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachListener() // Uygulamaya dönünce tekrar bağla
    }
}
