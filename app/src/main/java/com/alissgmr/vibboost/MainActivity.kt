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
    private lateinit var themeSpinner: Spinner
    private var currentHue = 0f
    private var targetIntensity = 0
    private var targetDuration = 0L
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        attachListener()
        startAnimationLoop() 
    }

    private fun setupUI() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }

        val themes = arrayOf("AMOLED", "WHITE", "OCEAN", "DEEP")
        val hexColors = mapOf("AMOLED" to "#000000", "WHITE" to "#FFFFFF", "OCEAN" to "#001F3F", "DEEP" to "#1A1A1A")
        
        themeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, themes)
            setPadding(0, 40, 0, 40)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val hex = hexColors[themes[position]] ?: "#000000"
                    rootLayout.setBackgroundColor(Color.parseColor(hex))
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 260).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 100, 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
            }
            text = "START ENGINE"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }

        rootLayout.addView(themeSpinner)
        rootLayout.addView(actionBtn)
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
            attachListener()
            actionBtn.text = "ENGINE ACTIVE"
        } else {
            stopService(intent)
            resetUIToZero()
        }
    }

    private fun resetUIToZero() {
        targetIntensity = 0
        targetDuration = 0L
        currentDisplayIntensity = 0f
        actionBtn.text = "START ENGINE"
        actionBtn.translationX = 0f
        actionBtn.translationY = 0f
        (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#333333"))
    }

    private fun startAnimationLoop() {
        Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // Eğer servis bildirim üzerinden kapatılırsa UI'yi anında sıfırla
                if (!VibBoostService.isRunning && actionBtn.text != "START ENGINE") {
                    resetUIToZero()
                } else if (VibBoostService.isRunning || currentDisplayIntensity > 0.1f) {
                    updateVisuals()
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        })
    }

    private fun updateVisuals() {
        currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor

        if (currentDisplayIntensity > 2) {
            val shake = (currentDisplayIntensity / 255f) * 40f
            actionBtn.translationX = ((Math.random() - 0.5) * 2 * shake).toFloat()
            actionBtn.translationY = ((Math.random() - 0.5) * 2 * shake).toFloat()
            
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
