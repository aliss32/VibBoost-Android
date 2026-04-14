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
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            visualizerBar.progress = intent?.getIntExtra("lvl", 0) ?: 0
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

        visualizerBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressDrawable.setTint(Color.RED)
            scaleY = 10f
            setPadding(0, 0, 0, 100)
        }

        val btn = Button(this).apply {
            text = "TOGGLE ENGINE"
            setOnClickListener {
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) startForegroundService(intent) else stopService(intent)
            }
        }

        root.addView(visualizerBar)
        root.addView(btn)
        setContentView(root)
        
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.VIBRATE), 1)
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
