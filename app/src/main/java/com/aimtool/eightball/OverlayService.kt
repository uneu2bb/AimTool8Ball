package com.aimtool.eightball

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: OverlayView? = null
    private var controlsView: View? = null
    private var controlsParams: WindowManager.LayoutParams? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var isEditMode = true

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var screenW = 1080
    private var screenH = 1920
    private var density = 320
    private var autoOn = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra("resultCode", 0)
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("data", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("data")
            }
            if (resultCode == Activity.RESULT_OK && data != null) {
                startCapture(resultCode, data)
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        density = metrics.densityDpi

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "الصلاحية غير مفعلة", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        try {
            setupOverlay()
            setupControls()
            Toast.makeText(this, "الأداة تعمل - وافق على التقاط الشاشة للتتبع", Toast.LENGTH_LONG).show()
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
            .setContentText("تتبع الخط التلقائي")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture() }
        }, null)

        captureThread = HandlerThread("aim-capture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AimTrack",
            screenW,
            screenH,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )

        autoOn = true
        schedule()
        Toast.makeText(this, "التتبع التلقائي شغال", Toast.LENGTH_SHORT).show()
    }

    private fun schedule() {
        captureHandler?.postDelayed(object : Runnable {
            override fun run() {
                if (autoOn) {
                    processFrame()
                    captureHandler?.postDelayed(this, 350)
                }
            }
        }, 400)
    }

    private fun processFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenW

            val bmp = Bitmap.createBitmap(
                screenW + rowPadding / pixelStride,
                screenH,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, screenW, screenH)
            bmp.recycle()

            val detected = LineDetector.detectAimLine(cropped)
            cropped.recycle()

            if (detected != null) {
                val ext = LineDetector.extendLine(detected, 3.5f)
                overlayView?.setAutoLine(ext.x1, ext.y1, ext.x2, ext.y2)
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun stopCapture() {
        autoOn = false
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
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
        controlsParams?.y = 200

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
        }
        controlsView?.findViewById<ImageButton>(R.id.btnAddLine)?.setOnLongClickListener {
            overlayView?.toggleGrid()
            Toast.makeText(this, "شبكة الجيوب", Toast.LENGTH_SHORT).show()
            true
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClear)?.setOnClickListener {
            overlayView?.clearAll()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnBank)?.setOnClickListener {
            overlayView?.toggleBankMode()
            overlayView?.addNewLine()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClose)?.setOnClickListener {
            isEditMode = !isEditMode
            updateTouch(isEditMode)
            Toast.makeText(this, if (isEditMode) "تعديل" else "لعب", Toast.LENGTH_SHORT).show()
        }
        controlsView?.findViewById<ImageButton>(R.id.btnClose)?.setOnLongClickListener {
            stopSelf()
            true
        }

        windowManager?.addView(controlsView, controlsParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        try {
            overlayView?.let { windowManager?.removeView(it) }
            controlsView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        controlsView = null
    }
}
