package com.aimtool.eightball

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnToggle).setOnClickListener {
            if (isServiceRunning) {
                stopOverlay()
            } else {
                checkPermissionAndStart()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkPermissionAndStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, getString(R.string.overlay_running), Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        isServiceRunning = false
        updateUI()
    }

    private fun updateUI() {
        val btn = findViewById<Button>(R.id.btnToggle)
        val status = findViewById<TextView>(R.id.statusText)
        if (isServiceRunning) {
            btn.text = getString(R.string.stop_overlay)
            status.text = "الحالة: يعمل"
        } else {
            btn.text = getString(R.string.start_overlay)
            status.text = "الحالة: متوقف"
        }
    }
}
