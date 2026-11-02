package com.aimtool.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * عرض احترافي: خطوط سميكة، ارتداد دقيق، معايرة طاولة، تتبع تلقائي ناعم
 */
class OverlayView(context: Context) : View(context) {

    private val pathColors = intArrayOf(
        Color.parseColor("#FF1744"),
        Color.parseColor("#00E676"),
        Color.parseColor("#FFEA00"),
        Color.parseColor("#D500F9"),
        Color.parseColor("#00B0FF"),
        Color.parseColor("#FF6D00"),
        Color.parseColor("#FFFFFF")
    )

    private val pathPaints = Array(pathColors.size) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pathColors[i]
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    private val glowPaints = Array(pathColors.size) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pathColors[i]
            strokeWidth = 16f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            alpha = 55
        }
    }

    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        strokeWidth = 8.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        strokeWidth = 18f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 60
    }
    private val pocketFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEFFFFFF")
        style = Paint.Style.FILL
    }
    private val pocketRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFEA00")
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true
    private var showGrid = false

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 38f

    // حدود الطاولة (قابلة للتحديث من الكشف التلقائي)
    private var tableL = 0f
    private var tableT = 0f
    private var tableR = 0f
    private var tableB = 0f
    private var tableCX = 0f
    private var tableCY = 0f
    private val pockets = Array(6) { FloatArray(2) }
    private var boundsCalibrated = false

    // تتبع تلقائي مع تنعيم
    private var tx1 = 0f
    private var ty1 = 0f
    private var tx2 = 0f
    private var ty2 = 0f
    private var hasTrack = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!boundsCalibrated) {
            // نسب افتراضية أدق لطاولة 8 Ball الأفقية
            applyBounds(w * 0.118f, h * 0.188f, w * 0.882f, h * 0.812f)
        }
    }

    fun applyBounds(l: Float, t: Float, r: Float, b: Float) {
        tableL = l
        tableT = t
        tableR = r
        tableB = b
        tableCX = (l + r) / 2f
        tableCY = (t + b) / 2f
        pockets[0][0] = l; pockets[0][1] = t
        pockets[1][0] = tableCX; pockets[1][1] = t
        pockets[2][0] = r; pockets[2][1] = t
        pockets[3][0] = l; pockets[3][1] = b
        pockets[4][0] = tableCX; pockets[4][1] = b
        pockets[5][0] = r; pockets[5][1] = b
        boundsCalibrated = true
        post { invalidate() }
    }

    fun addNewLine() {
        val bank = if (bankMode) 1f else 0f
        lines.add(floatArrayOf(tableCX - 240f, tableCY, tableCX + 240f, tableCY, bank))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        showGrid = false
        hasTrack = false
        invalidate()
    }

    fun clearAutoLine() {
        hasTrack = false
        invalidate()
    }

    fun setAutoLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        // تنعيم أقوى لاستقرار السهم
        val a = 0.32f
        if (hasTrack) {
            tx1 = tx1 * (1 - a) + x1 * a
            ty1 = ty1 * (1 - a) + y1 * a
            tx2 = tx2 * (1 - a) + x2 * a
            ty2 = ty2 * (1 - a) + y2 * a
        } else {
            tx1 = x1; ty1 = y1; tx2 = x2; ty2 = y2
        }
        hasTrack = true
        post { invalidate() }
    }

    fun toggleBankMode() { bankMode = !bankMode }
    fun toggleGrid() { showGrid = !showGrid; invalidate() }
    fun enableEditMode(enabled: Boolean) { isEditMode = enabled; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (showGrid) drawProGrid(canvas)

        if (hasTrack) {
            canvas.drawLine(tx1, ty1, tx2, ty2, trackGlow)
            canvas.drawLine(tx1, ty1, tx2, ty2, trackPaint)
            drawArrowHead(canvas, tx1, ty1, tx2, ty2, trackPaint.color)
            drawProTrajectory(canvas, tx1, ty1, tx2, ty2, 0)
        }

        for ((index, line) in lines.withIndex()) {
            val ci = index % pathPaints.size
            canvas.drawLine(line[0], line[1], line[2], line[3], glowPaints[ci])
            canvas.drawLine(line[0], line[1], line[2], line[3], pathPaints[ci])
            drawArrowHead(canvas, line[0], line[1], line[2], line[3], pathColors[ci])

            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }
            drawProTrajectory(canvas, line[0], line[1], line[2], line[3], ci)
        }
    }

    private fun drawProGrid(canvas: Canvas) {
        // خطوط للجيوب
        for (i in pockets.indices) {
            val p = Paint(pathPaints[i % pathPaints.size])
            p.strokeWidth = 6f
            p.alpha = 175
            canvas.drawLine(tableCX, tableCY, pockets[i][0], pockets[i][1], p)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 16f, pocketFill)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 16f, pocketRing)
        }
        // شبكة بنود خفيفة
        val grid = Paint(borderPaint)
        grid.strokeWidth = 2.5f
        grid.alpha = 90
        val cols = 6
        val rows = 3
        for (i in 1 until cols) {
            val x = tableL + (tableR - tableL) * i / cols
            canvas.drawLine(x, tableT, x, tableB, grid)
        }
        for (i in 1 until rows) {
            val y = tableT + (tableB - tableT) * i / rows
            canvas.drawLine(tableL, y, tableR, y, grid)
        }
        canvas.drawRect(tableL, tableT, tableR, tableB, borderPaint)
    }

    /**
     * ارتداد هندسي دقيق: انعكاس على الحدود + حتى 12 قفزة
     */
    private fun drawProTrajectory(
        canvas: Canvas,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        startColor: Int
    ) {
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy)
        if (len < 1f) return

        var dirX = dx / len
        var dirY = dy / len
        var cx = ex
        var cy = ey

        for (bounce in 0..12) {
            val ci = (startColor + bounce) % pathPaints.size
            val paint = pathPaints[ci]
            val glow = glowPaints[ci]
            val hit = findHit(cx, cy, dirX, dirY)
            if (hit == null) {
                canvas.drawLine(cx, cy, cx + dirX * 2400f, cy + dirY * 2400f, glow)
                canvas.drawLine(cx, cy, cx + dirX * 2400f, cy + dirY * 2400f, paint)
                break
            }
            canvas.drawLine(cx, cy, hit[0], hit[1], glow)
            canvas.drawLine(cx, cy, hit[0], hit[1], paint)
            hitPaint.color = pathColors[ci]
            canvas.drawCircle(hit[0], hit[1], 11f, hitPaint)

            // انعكاس مثالي: R = V - 2(V·N)N
            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny

            // تطبيع بسيط لمنع تراكم الخطأ
            val nlen = hypot(dirX, dirY)
            if (nlen > 1e-4f) {
                dirX /= nlen
                dirY /= nlen
            }
            cx = hit[0] + dirX * 2.5f
            cy = hit[1] + dirY * 2.5f
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        arrowPaint.color = color
        val angle = atan2(y2 - y1, x2 - x1)
        val size = 32f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - size * cos(angle - 0.48f), y2 - size * sin(angle - 0.48f))
        path.lineTo(x2 - size * cos(angle + 0.48f), y2 - size * sin(angle + 0.48f))
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun findHit(cx: Float, cy: Float, dirX: Float, dirY: Float): FloatArray? {
        var tHit = Float.MAX_VALUE
        var hx = 0f
        var hy = 0f
        var nx = 0f
        var ny = 0f

        if (dirX < -1e-5f) {
            val t = (tableL - cx) / dirX
            if (t > 0.8f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= tableT - 2 && yy <= tableB + 2) {
                    tHit = t; hx = tableL; hy = yy; nx = 1f; ny = 0f
                }
            }
        }
        if (dirX > 1e-5f) {
            val t = (tableR - cx) / dirX
            if (t > 0.8f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= tableT - 2 && yy <= tableB + 2) {
                    tHit = t; hx = tableR; hy = yy; nx = -1f; ny = 0f
                }
            }
        }
        if (dirY < -1e-5f) {
            val t = (tableT - cy) / dirY
            if (t > 0.8f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= tableL - 2 && xx <= tableR + 2) {
                    tHit = t; hx = xx; hy = tableT; nx = 0f; ny = 1f
                }
            }
        }
        if (dirY > 1e-5f) {
            val t = (tableB - cy) / dirY
            if (t > 0.8f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= tableL - 2 && xx <= tableR + 2) {
                    tHit = t; hx = xx; hy = tableB; nx = 0f; ny = -1f
                }
            }
        }
        return if (tHit < Float.MAX_VALUE) floatArrayOf(hx, hy, nx, ny) else null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditMode) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in lines.indices) {
                    val line = lines[i]
                    if (hypot(event.x - line[0], event.y - line[1]) < handleR * 2.1f) {
                        activeIndex = i; dragStart = true; return true
                    }
                    if (hypot(event.x - line[2], event.y - line[3]) < handleR * 2.1f) {
                        activeIndex = i; dragStart = false; return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeIndex in lines.indices) {
                    val line = lines[activeIndex]
                    if (dragStart) { line[0] = event.x; line[1] = event.y }
                    else { line[2] = event.x; line[3] = event.y }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeIndex = -1
        }
        return activeIndex >= 0
    }
}
