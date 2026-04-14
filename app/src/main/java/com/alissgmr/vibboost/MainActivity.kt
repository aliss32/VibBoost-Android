package com.alissgmr.vibboost

import android.Manifest
import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var inBar: ProgressBar
    private lateinit var outBar: ProgressBar
    private lateinit var infoText: TextView
    private lateinit var btn: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val input = intent?.getIntExtra("in", 0) ?: 0
            val output = intent?.getIntExtra("out", 0) ?: 0
            
            // Canlı Telemetri Güncelleme
            inBar.progress = input
            outBar.progress = output
            infoText.text = "ANALYSIS: %$input | MOTOR LOAD: %$output"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Minimalist & Endüstriyel Arayüz
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "VIB-BOOST MONITOR"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 100)
        }

        infoText = TextView(this).apply {
            text = "ENGINE STANDBY"
            setTextColor(Color.parseColor("#00FFCC"))
            setPadding(0, 0, 0, 20)
        }

        // Visualizer Barları
        inBar = createBar(Color.CYAN)
        outBar = createBar(Color.MAGENTA)

        btn = Button(this).apply {
            text = "START ENGINE"
            setOnClickListener { 
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) {
                    startForegroundService(intent)
                    text = "STOP ENGINE"
                } else {
                    stopService(intent)
                    text = "START ENGINE"
                }
            }
        }

        root.addView(title)
        root.addView(infoText)
        root.addView(TextView(this).apply { text = "INPUT SIGNAL"; setTextColor(Color.GRAY) })
        root.addView(inBar)
        root.addView(TextView(this).apply { text = "HAPTIC OUTPUT"; setTextColor(Color.GRAY) })
        root.addView(outBar)
        root.addView(btn)
        
        setContentView(root)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE), 1)
    }

    private fun createBar(color: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progress = 0
        progressDrawable.setTint(color)
        scaleY = 4f
        setPadding(0, 40, 0, 60)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(VibBoostService.UPDATE_ACTION), RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
