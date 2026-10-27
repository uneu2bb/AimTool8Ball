package com.aimtool.eightball

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
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
    private val REQ_CAPTURE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnToggle).setOnClickListener {
            if (isServiceRunning) stopOverlay() else checkAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startOverlay(resultCode, data)
            } else {
                Toast.makeText(this, "تشغيل يدوي بدون تتبع تلقائي", Toast.LENGTH_SHORT).show()
                startOverlay(0, null)
            }
        }
    }

    private fun startOverlay(resultCode: Int, data: Intent?) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("resultCode", resultCode)
        if (data != null) intent.putExtra("data", data)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        updateUI()
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
