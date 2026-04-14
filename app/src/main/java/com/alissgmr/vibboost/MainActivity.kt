package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val actionBtn = Button(this).apply {
            text = if (VibBoostService.isRunning) "STOP ENGINE" else "START LINEAR ENGINE"
            setPadding(80, 40, 80, 40)
            setOnClickListener {
                val intent = Intent(this@MainActivity, VibBoostService::class.java)
                if (!VibBoostService.isRunning) {
                    startForegroundService(intent)
                    text = "STOP ENGINE"
                } else {
                    stopService(intent)
                    text = "START LINEAR ENGINE"
                }
            }
        }

        root.addView(actionBtn)
        setContentView(root)
        
        // Gerekli izinleri iste
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.RECORD_AUDIO, 
            Manifest.permission.VIBRATE
        ), 1)
    }
}
