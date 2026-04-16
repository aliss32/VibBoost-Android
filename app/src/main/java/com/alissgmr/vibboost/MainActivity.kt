package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var actionBtn: Button
    private lateinit var rootLayout: LinearLayout
    private lateinit var themeSpinner: Spinner
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdSeekBar: SeekBar
    
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
            setBackgroundColor(Color.BLACK)
        }

        // Üst Kısım: Butonun dikeyde ortalanması ve diğer elementleri aşağı itmesi için weight kullanıyoruz
        val topContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
            gravity = Gravity.CENTER
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
        
        topContainer.addView(actionBtn)

        // Alt Kısım: Kaydıraç ve Tema Seçici
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(60, 40, 60, 100)
        }

        // Bass Hassasiyeti (Threshold) Etiketi
        thresholdLabel = TextView(this).apply {
            text = "Tetikleme Eşiği (Bass Hassasiyeti): ${VibBoostService.noiseGateThreshold.toInt()}"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // Bass Hassasiyeti Kaydıracı
        thresholdSeekBar = SeekBar(this).apply {
            max = 100
            progress = VibBoostService.noiseGateThreshold.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    VibBoostService.noiseGateThreshold = progress.toFloat()
                    thresholdLabel.text = "Tetikleme Eşiği (Bass Hassasiyeti): $progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            setPadding(0, 0, 0, 60)
        }

        val themes = arrayOf("AMOLED", "WHITE", "OCEAN", "DEEP")
        val hexColors = mapOf("AMOLED" to "#000000", "WHITE" to "#FFFFFF", "OCEAN" to "#001F3F", "DEEP" to "#1A1A1A")
        
        // Spinner için özel adaptör (Renk ayarlamaları için)
        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, themes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val hex = hexColors[themes[themeSpinner.selectedItemPosition]] ?: "#000000"
                view.setTextColor(if (hex == "#FFFFFF") Color.BLACK else Color.WHITE)
                view.gravity = Gravity.CENTER
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK) // Açılır menüdeki yazılar her zaman okunabilir kalsın diye siyah yapıyoruz
                view.setBackgroundColor(Color.WHITE)
                view.setPadding(40, 40, 40, 40)
                return view
            }
        }

        themeSpinner = Spinner(this).apply {
            this.adapter = adapter
            setPadding(0, 20, 0, 20)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val hex = hexColors[themes[position]] ?: "#000000"
                    rootLayout.setBackgroundColor(Color.parseColor(hex))
                    
                    // Beyaz tema seçildiğinde metinlerin görünmeme sorununu çözen mantık
                    val isWhiteTheme = hex == "#FFFFFF"
                    val textColor = if (isWhiteTheme) Color.BLACK else Color.WHITE
                    
                    thresholdLabel.setTextColor(textColor)
                    (view as? TextView)?.setTextColor(textColor)
                    background.setColorFilter(textColor, PorterDuff.Mode.SRC_ATOP) // Spinner okunun rengini günceller
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Elemanları hiyerarşiye ekleme
        bottomContainer.addView(thresholdLabel)
        bottomContainer.addView(thresholdSeekBar)
        bottomContainer.addView(themeSpinner)

        rootLayout.addView(topContainer)
        rootLayout.addView(bottomContainer)
        
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
