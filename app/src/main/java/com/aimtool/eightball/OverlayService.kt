package com.aimtool.eightball

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var controlsView: View? = null
    private var controlsParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "الصلاحية غير مفعلة", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            setupOverlay()
            setupControls()
            Toast.makeText(this, "الأداة تعمل - افتح اللعبة", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val channelId = "aim_tool_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Aim Tool 8 Ball")
            .setContentText("الأداة تعمل الآن")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlay() {
        overlayView = OverlayView(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(overlayView, params)
    }

    private fun setupControls() {
        controlsView = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 300
        }

        // سحب لوحة التحكم
        controlsView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = controlsParams?.x ?: 0
                        initialY = controlsParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        controlsParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                        controlsParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(controlsView, controlsParams)
                        return true
                    }
                }
                return false
            }
        })

        controlsView?.findViewById<ImageButton>(R.id.btnAddLine)?.setOnClickListener {
            overlayView?.addNewLine()
            Toast.makeText(this, "تم إضافة خط", Toast.LENGTH_SHORT).show()
        }

        controlsView?.findViewById<ImageButton>(R.id.btnClear)?.setOnClickListener {
            overlayView?.clearAll()
            Toast.makeText(this, "تم المسح", Toast.LENGTH_SHORT).show()
        }

        controlsView?.findViewById<ImageButton>(R.id.btnBank)?.setOnClickListener {
            overlayView?.toggleBankMode()
            Toast.makeText(this, "Bank Mode", Toast.LENGTH_SHORT).show()
        }

        controlsView?.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            stopSelf()
        }

        windowManager?.addView(controlsView, controlsParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let { windowManager?.removeView(it) }
            controlsView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        controlsView = null
    }
}
