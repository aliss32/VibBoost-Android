package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var actionBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Yuvarlak buton tasarımı
        val circleShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1DB954")) // Spotify yeşili gibi tatlı bir renk
        }

        actionBtn = Button(this).apply {
            background = circleShape
            setTextColor(Color.WHITE)
            textSize = 16f
            isAllCaps = true
            
            // Butonu tam bir daire yapmak için dp cinsinden boyut veriyoruz (örn: 250dp x 250dp)
            val sizeInPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 250f, resources.displayMetrics
            ).toInt()
            layoutParams = LinearLayout.LayoutParams(sizeInPx, sizeInPx)
            
            text = if (VibBoostService.isRunning) "STOP ENGINE" else "START BASS ENGINE"
            
            setOnClickListener {
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    text = "AWAITING BASS..."
                    circleShape.setColor(Color.parseColor("#E91E63")) // Çalışırken Pembe/Kırmızı
                } else {
                    stopService(intent)
                    resetButtonState()
                }
            }
        }

        root.addView(actionBtn)
        setContentView(root)
        
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO, 
            Manifest.permission.VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        // Uygulama ekrandayken servisteki dinleyiciyi bağla
        VibBoostService.hapticListener = { intensity, duration ->
            runOnUiThread {
                if (VibBoostService.isRunning) {
                    if (intensity > 0) {
                        // Canlı veriyi butona yaz
                        actionBtn.text = "PWR: $intensity\nTIME: ${duration}ms\n\nTAP TO STOP"
                        
                        // Şiddete göre sallanma (Shake) efekti oluştur
                        // Güç ne kadar yüksekse, sallanma genliği o kadar artar
                        val shakeAmplitude = (intensity / 255f) * 40f // Maksimum 40 piksel titreme
                        actionBtn.translationX = ((Math.random() - 0.5) * 2 * shakeAmplitude).toFloat()
                        actionBtn.translationY = ((Math.random() - 0.5) * 2 * shakeAmplitude).toFloat()
                    } else {
                        // Ses yoksa butonu merkezle
                        actionBtn.translationX = 0f
                        actionBtn.translationY = 0f
                        actionBtn.text = "LISTENING..."
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Uygulama arka plana atıldığında UI güncellemelerini durdur (Batarya tasarrufu)
        VibBoostService.hapticListener = null
    }

    private fun resetButtonState() {
        actionBtn.translationX = 0f
        actionBtn.translationY = 0f
        actionBtn.text = "START BASS ENGINE"
        (actionBtn.background as GradientDrawable).setColor(Color.parseColor("#1DB954"))
    }
}
