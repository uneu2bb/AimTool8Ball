package com.aimtool.eightball

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aimtool.eightball.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServiceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener {
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
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
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
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        isServiceRunning = false
        updateUI()
    }

    private fun updateUI() {
        if (isServiceRunning) {
            binding.btnToggle.text = getString(R.string.stop_overlay)
            binding.statusText.text = "الحالة: يعمل ✓"
        } else {
            binding.btnToggle.text = getString(R.string.start_overlay)
            binding.statusText.text = "الحالة: متوقف"
        }
    }
}
