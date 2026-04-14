package com.alissgmr.vibboost

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 2026
    private lateinit var statusText: TextView
    private lateinit var actionBtn: Button
    
    // Telemetri UI Bileşenleri
    private lateinit var soundBar: ProgressBar
    private lateinit var motorBar: ProgressBar
    private lateinit var soundText: TextView
    private lateinit var motorText: TextView

    // Servisten gelen verileri dinleyen Receiver
    private val telemetryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VibBoostService.UPDATE_ACTION) {
                val soundLvl = intent.getIntExtra("soundLevel", 0)
                val motorLvl = intent.getIntExtra("motorPercent", 0)
                
                // UI Güncelleme
                soundBar.progress = soundLvl
                motorBar.progress = motorLvl
                soundText.text = "BASS INPUT LEVEL: %${soundLvl}"
                motorText.text = "HAPTIC MOTOR LOAD: %${motorLvl}"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Dinamik Arayüz Kurulumu (Canlı Telemetri Destekli)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#050505")) // Tam Siyah (OLED)
            setPadding(60, 60, 60, 60)
        }

        statusText = TextView(this).apply {
            text = "VIB-BOOST DSP ENGINE\nOFFLINE"
            setTextColor(Color.parseColor("#00FFCC"))
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        // --- VISUALIZER BÖLÜMÜ ---
        soundText = TextView(this).apply {
            text = "BASS INPUT LEVEL: %0"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            setPadding(0, 20, 0, 10)
        }
        soundBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setTint(Color.parseColor("#00FFCC"))
            setPadding(0, 0, 0, 40)
        }

        motorText = TextView(this).apply {
            text = "HAPTIC MOTOR LOAD: %0"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            setPadding(0, 20, 0, 10)
        }
        motorBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setTint(Color.parseColor("#FF3366")) // Motor zorlanmasını kırmızımsı gösterelim
            setPadding(0, 0, 0, 100)
        }
        // -------------------------

        actionBtn = Button(this).apply {
            text = "INITIALIZE ENGINE"
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setTextColor(Color.WHITE)
            setPadding(0, 40, 0, 40)
            textSize = 18f
        }

        root.addView(statusText)
        root.addView(soundText)
        root.addView(soundBar)
        root.addView(motorText)
        root.addView(motorBar)
        root.addView(actionBtn)

        setContentView(root)
        checkAndRequestPermissions()

        actionBtn.setOnClickListener {
            val intent = Intent(this, VibBoostService::class.java)
            if (!VibBoostService.isRunning) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                statusText.text = "VIB-BOOST DSP ENGINE\nONLINE"
                statusText.setTextColor(Color.parseColor("#00FFCC"))
                actionBtn.text = "TERMINATE ENGINE"
                actionBtn.setBackgroundColor(Color.parseColor("#440000"))
            } else {
                stopService(intent)
                VibBoostService.isRunning = false
                statusText.text = "VIB-BOOST DSP ENGINE\nOFFLINE"
                statusText.setTextColor(Color.GRAY)
                actionBtn.text = "INITIALIZE ENGINE"
                actionBtn.setBackgroundColor(Color.parseColor("#1A1A1A"))
                
                // Sıfırla
                soundBar.progress = 0
                motorBar.progress = 0
                soundText.text = "BASS INPUT LEVEL: %0"
                motorText.text = "HAPTIC MOTOR LOAD: %0"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Uygulama ekrandayken telemetri verilerini dinle
        val filter = IntentFilter(VibBoostService.UPDATE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(telemetryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(telemetryReceiver, filter)
        }
        
        // UI durumunu servise göre senkronize et
        if (VibBoostService.isRunning) {
            statusText.text = "VIB-BOOST DSP ENGINE\nONLINE"
            actionBtn.text = "TERMINATE ENGINE"
            actionBtn.setBackgroundColor(Color.parseColor("#440000"))
        }
    }

    override fun onPause() {
        super.onResume()
        // Uygulama arka plana atıldığında UI güncellemeyi durdur (Pil tasarrufu)
        unregisterReceiver(telemetryReceiver)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
}
