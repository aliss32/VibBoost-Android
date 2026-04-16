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
    
    // Frekans Arayüz Değişkenleri
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdSeekBar: SeekBar

    // Seçim Menüleri (Spinners)
    private lateinit var shakeSpinner: Spinner
    private lateinit var themeSpinner: Spinner

    // Görsel Efekt ve Durum Değişkenleri
    private var currentHue = 0f
    private var targetIntensity = 0
    private var targetDuration = 0L
    private var currentDisplayIntensity = 0f
    private val smoothingFactor = 0.15f // Ease-out için
    
    private var isWhiteTheme = false // Beyaz tema kontrolü
    private var shakeMode = 0 // 0: Doğrusal (Varsayılan), 1: Ease-out

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
            setBackgroundColor(Color.BLACK) // Başlangıç teması
        }

        // --- 1. ANA BUTON (En Üstte) ---
        actionBtn = Button(this).apply {
            val size = (resources.displayMetrics.density * 260).toInt()
            val btnParams = LinearLayout.LayoutParams(size, size)
            btnParams.setMargins(0, 0, 0, 80) // Altındaki menülerle boşluk bırak
            layoutParams = btnParams
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
            }
            text = if (VibBoostService.isRunning) "ENGINE ACTIVE" else "START ENGINE"
            setTextColor(Color.WHITE)
            setOnClickListener { toggleService() }
        }

        // --- 2. SEÇİM MENÜLERİ (Spinners - Yan Yana) ---
        val spinnerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(40, 0, 40, 40) // Kenarlardan ve alttan boşluk
        }

        // Titreşim Stili Menüsü
        val shakeOptions = arrayOf("Doğrusal (Anlık)", "Yumuşak (Ease-out)")
        shakeSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = createCustomAdapter(shakeOptions)
            setSelection(0) // Varsayılan: Doğrusal
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    shakeMode = position
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Tema Menüsü
        val themeOptions = arrayOf("AMOLED", "BEYAZ", "OKYANUS", "DERİN")
        val themeHexCodes = arrayOf("#000000", "#FFFFFF", "#001F3F", "#1A1A1A")
        themeSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = createCustomAdapter(themeOptions)
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val hex = themeHexCodes[position]
                    rootLayout.setBackgroundColor(Color.parseColor(hex))
                    
                    // Beyaz tema seçildiyse okunabilirlik için yazıları siyah yap
                    isWhiteTheme = (hex == "#FFFFFF")
                    updateTextColors()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        spinnerLayout.addView(shakeSpinner)
        spinnerLayout.addView(themeSpinner)

        // --- 3. BASS FREKANSI METNİ ---
        thresholdLabel = TextView(this).apply {
            text = "Hedef Bass Frekansı: ${VibBoostService.targetFrequency.toInt()} Hz"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        // --- 4. BASS FREKANSI SEÇİCİ (SeekBar - En Altta) ---
        thresholdSeekBar = SeekBar(this).apply {
            max = 210 // 40 Hz ile 250 Hz arası
            progress = VibBoostService.targetFrequency.toInt() - 40
            
            // Kenarlara yapışmaması için Margin ekliyoruz
            val seekParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            seekParams.setMargins(80, 0, 80, 40) // Sol ve sağdan 80px boşluk
            layoutParams = seekParams

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val freq = progress + 40f
                    VibBoostService.targetFrequency = freq
                    thresholdLabel.text = "Hedef Bass Frekansı: ${freq.toInt()} Hz"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // Bileşenleri sırasıyla ekrana ekle (Yukarıdan aşağıya)
        rootLayout.addView(actionBtn)
        rootLayout.addView(spinnerLayout)
        rootLayout.addView(thresholdLabel)
        rootLayout.addView(thresholdSeekBar)
        
        setContentView(rootLayout)
    
        // İzinleri iste
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    // Seçim menülerindeki metinlerin dinamik renk değiştirmesi için özel adaptör
    private fun createCustomAdapter(items: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                // Ekranda görünen metnin rengi temaya göre değişir
                view.setTextColor(if (isWhiteTheme) Color.BLACK else Color.WHITE)
                view.gravity = Gravity.CENTER
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                // Açılır liste içindeki metinler genelde siyah olursa her cihazda okunaklı olur
                view.setTextColor(Color.BLACK)
                view.setPadding(20, 30, 20, 30)
                return view
            }
        }
    }

    // Temaya göre UI metin renklerini güncelleyen fonksiyon
    private fun updateTextColors() {
        val textColor = if (isWhiteTheme) Color.BLACK else Color.WHITE
        thresholdLabel.setTextColor(textColor)
        
        // Spinner'ların adaptörlerini tetikleyerek renklerinin güncellenmesini sağla
        (shakeSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
        (themeSpinner.adapter as ArrayAdapter<*>).notifyDataSetChanged()
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
        // Seçilen moda göre titreşim hesaplaması
        if (shakeMode == 0) {
            // 0: Doğrusal (Linear) - Gelen veriye anında tepki verir
            currentDisplayIntensity = targetIntensity.toFloat()
        } else {
            // 1: Yumuşak (Ease-out) - Yavaşça azalan akıcı titreşim
            currentDisplayIntensity += (targetIntensity - currentDisplayIntensity) * smoothingFactor
        }

        if (currentDisplayIntensity > 2) {
            // Shake efekti
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
