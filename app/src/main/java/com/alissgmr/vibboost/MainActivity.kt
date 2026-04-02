package com.alissgmr.vibboost

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSION_REQUEST_CODE = 2026
    private lateinit var statusText: TextView
    private lateinit var actionBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Tasarımı (PC Versiyonu Esintili Dark Tema)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF000000.toInt())
            setPadding(80, 80, 80, 80)
        }

        statusText = TextView(this).apply {
            text = "VIB-BOOST PRO\nREADY FOR ENGAGEMENT"
            setTextColor(0xFF00FFCC.toInt())
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 120)
        }

        actionBtn = Button(this).apply {
            text = "START ENGINE"
            setBackgroundColor(0xFF1A1A1A.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(40, 40, 40, 40)
            setOnClickListener { handleServiceToggle() }
        }

        root.addView(statusText)
        root.addView(actionBtn)
        setContentView(root)

        checkAndRequestPermissions()
    }

    private fun handleServiceToggle() {
        val serviceIntent = Intent(this, VibBoostService::class.java)
        
        if (VibBoostService.isRunning) {
            stopService(serviceIntent)
            statusText.text = "ENGINE: OFFLINE"
            actionBtn.text = "START ENGINE"
        } else {
            // Android 8.0+ için Foreground Service başlatma kuralı
            ContextCompat.startForegroundService(this, serviceIntent)
            statusText.text = "ENGINE: ACTIVE (BACKGROUND)"
            actionBtn.text = "STOP ENGINE"
            // Uygulamayı arka plana at (Servis çalışmaya devam eder)
            moveTaskToBack(true)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        // Android 13+ için bildirim izni (Arka plan servisi için şart)
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

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, gr: IntArray) {
        super.onRequestPermissionsResult(rc, p, gr)
        if (rc == PERMISSION_REQUEST_CODE) {
            if (gr.isNotEmpty() && gr[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions Secured. Engine Ready.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Critical Permissions Denied!", Toast.LENGTH_LONG).show()
            }
        }
    }
}
