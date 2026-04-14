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

    private lateinit var visualizerBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var mainBtn: Button

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra("lvl", 0) ?: 0
            // Visualizer barını saniyede onlarca kez güncelliyoruz
            visualizerBar.progress = level
            statusText.text = "ENGINE ACTIVE | LOAD: %$level"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(60, 60, 60, 60)
        }

        statusText = TextView(this).apply {
            text = "ENGINE STANDBY"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, 50)
        }

        // --- CANLI VISUALIZER BAR ---
        visualizerBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable.setTint(Color.parseColor("#FF0055")) // Sert mod rengi: Magenta
            scaleY = 8f // Barı daha kalın ve görünür yaptık
            setPadding(0, 50, 0, 80)
        }

        mainBtn = Button(this).apply {
            text = "INITIALIZE HARD MODE"
            setPadding(0, 40, 0, 40)
            setOnClickListener {
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) {
                    startForegroundService(intent)
                    text = "SHUTDOWN"
                    setBackgroundColor(Color.RED)
                } else {
                    stopService(intent)
                    text = "INITIALIZE HARD MODE"
                    setBackgroundColor(Color.DKGRAY)
                    visualizerBar.progress = 0
                    statusText.text = "ENGINE STANDBY"
                }
            }
        }

        root.addView(statusText)
        root.addView(visualizerBar)
        root.addView(mainBtn)
        
        setContentView(root)
        
        // İzinleri alalım
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.RECORD_AUDIO, 
            Manifest.permission.VIBRATE
        ), 1)
    }

    override fun onResume() {
        super.onResume()
        // Broadcast dinleyiciyi kaydet
        registerReceiver(receiver, IntentFilter(VibBoostService.UPDATE_ACTION), RECEIVER_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }
}
