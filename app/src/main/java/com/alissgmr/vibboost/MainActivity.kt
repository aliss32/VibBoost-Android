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
    private var targetHz = 0 // Yeni eklenen frekans değeri
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f 
    
    private var isWhiteTheme = false 
    private var shakeMode = 0 

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        attachListener()
        startAnimationLoop() // Derleme hatasına yol açan eksik fonksiyon eklendi
    }

    private fun setupUI() {
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
            typeface = android.graphics.Typeface.DEFAULT_BOLD // Yazı kalınlaştırıldı
            setOnClickListener { toggleService() }
        }

        // --- 2. SEÇİM MENÜLERİ (Yan Yana) ---
        val spinnerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(60, 0, 60, 40)
        }

        val shakeOptions = arrayOf("Doğrusal (Anlık)", "Yumuşak (Ease-out)")
        shakeSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = createCustomAdapter(shakeOptions)
            setSelection(0) 
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    shakeMode = position
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        val themeOptions = arrayOf("AMOLED", "BEYAZ", "OKYANUS", "GECE")
        val themeHexCodes = arrayOf("#000000", "#FFFFFF", "#001F3F", "#121212")
        themeSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = createCustomAdapter(themeOptions)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val hex = themeHexCodes[position]
                    rootLayout.setBackgroundColor(Color.parseColor(hex))
                    isWhiteTheme = (hex == "#FFFFFF")
                    updateUIColors()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        spinnerLayout.addView(shakeSpinner)
        spinnerLayout.addView(themeSpinner)

        thresholdLabel = TextView(this).apply {
            text = "Titreşim Eşiği (Gate): %${VibBoostService.gateThresholdPercent.toInt()}"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 10)
        }

        thresholdSeekBar = SeekBar(this).apply {
            max = 100
            progress = VibBoostService.gateThresholdPercent.toInt()
            val seekParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            seekParams.setMargins(100, 10, 100, 50)
            layoutParams = seekParams

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    VibBoostService.gateThresholdPercent = progress.toFloat()
                    thresholdLabel.text = "Titreşim Eşiği (Gate): %$progress"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        rootLayout.addView(actionBtn)
        rootLayout.addView(spinnerLayout)
        rootLayout.addView(thresholdLabel)
        rootLayout.addView(thresholdSeekBar)
        
        setContentView(rootLayout)
    
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    private fun createCustomAdapter(items: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(if (isWhiteTheme) Color.BLACK else Color.WHITE)
                view.gravity = Gravity.CENTER
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                view.setPadding(30, 30, 30, 30)
                return view
            }
        }
    }

    private fun updateUIColors() {
        val color = if (isWhiteTheme) Color.BLACK else Color.WHITE
        thresholdLabel.setTextColor(color)
        (shakeSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        (themeSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    private fun attachListener() {
        // Listener 3 parametre (intensity, duration, hz) alacak şekilde güncellendi
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
            targetHz = 0 
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
        if (shakeMode == 0) {
            currentDisplayIntensity = targetIntensity.toFloat()
        } else {
            currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor
        }

        if (currentDisplayIntensity > 2 && targetIntensity > 0) {
            val shake = (currentDisplayIntensity / 255f) * 45f
            actionBtn.translationX = ((Math.random() - 0.5) * 2 * shake).toFloat()
            actionBtn.translationY = ((Math.random() - 0.5) * 2 * shake).toFloat()
             
            currentHue = (currentHue + (currentDisplayIntensity / 30f)) % 360f
            val color = Color.HSVToColor(floatArrayOf(currentHue, 0.8f, 1.0f))
            (actionBtn.background as GradientDrawable).setColor(color)
            
            // Buton üzerinde FREKANS verisi gösteriliyor
            actionBtn.text = "GÜÇ: ${targetIntensity}\nSÜRE: ${targetDuration}ms\nFREKANS: ~${targetHz}Hz"
        } else {
            if (VibBoostService.isRunning) {
                actionBtn.text = "DİNLENİYOR..."
                actionBtn.translationX = 0f
                actionBtn.translationY = 0f
                (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#333333"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        attachListener()
    }
}
