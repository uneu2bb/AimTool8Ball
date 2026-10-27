package com.aimtool.eightball

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat

/**
 * النسخة المستقرة: خطوط يدوية + ارتداد متعدد قوي
 * الكشف التلقائي من الشاشة متوقف مؤقتاً لأنه كان غير دقيق
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var controlsView: View? = null
    private var controlsParams: WindowManager.LayoutParams? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isEditMode = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "الصلاحية غير مفعلة", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            setupOverlay()
            setupControls()
            Toast.makeText(this, "اسحب المقابض + استخدم Bank للارتداد الطويل", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: " + e.message, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun startFg() {
        val channelId = "aim_tool_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Aim Tool", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Aim Tool 8 Ball")
            .setContentText("الأداة تعمل الآن")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    private fun setupOverlay() {
        overlayView = OverlayView(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager?.addView(overlayView, overlayParams)
    }

    private fun updateTouch(editable: Boolean) {
        val params = overlayParams ?: return
        if (editable) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        windowManager?.updateViewLayout(overlayView, params)
        overlayView?.enableEditMode(editable)
    }

    private fun setupControls() {
        val themed = ContextThemeWrapper(this, R.style.Theme_AimTool8Ball)
        controlsView = LayoutInflater.from(themed).inflate(R.layout.overlay_controls, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        controlsParams?.gravity = Gravity.TOP or Gravity.START
        controlsParams?.x = 30
        controlsParams?.y = 250

        controlsView?.setOnTouchListener(object : View.OnTouchListener {
            var ix = 0
            var iy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ix = controlsParams?.x ?: 0
                        iy = controlsParams?.y ?: 0
                        tx = e.rawX
                        ty = e.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        controlsParams?.x = ix + (e.rawX - tx).toInt()
                        controlsParams?.y = iy + (e.rawY - ty).toInt()
                        windowManager?.updateViewLayout(controlsView, controlsParams)
                        return true
                    }
                }
                return false
            }
        })

        controlsView?.findViewById<ImageButton>(R.id.btnAddLine)?.setOnClickListener {
            overlayView?.addNewLine()
            Toast.makeText(this, "اسحب المقابض البيضاء", Toast.LENGTH_SHORT).show()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClear)?.setOnClickListener {
            overlayView?.clearAll()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnBank)?.setOnClickListener {
            overlayView?.toggleBankMode()
            overlayView?.addNewLine()
            Toast.makeText(this, "Bank: حرّك الخط نحو الحافة للارتداد الطويل", Toast.LENGTH_SHORT).show()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            isEditMode = !isEditMode
            updateTouch(isEditMode)
            Toast.makeText(this, if (isEditMode) "وضع التعديل" else "وضع اللعب", Toast.LENGTH_SHORT).show()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClose)?.setOnLongClickListener {
            stopSelf()
            true
        }

        windowManager?.addView(controlsView, controlsParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            overlayView?.let { windowManager?.removeView(it) }
            controlsView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        controlsView = null
    }
}
