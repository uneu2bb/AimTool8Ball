package com.aimtool.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true
    private var showGrid = false

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 34f

    private var tableL = 0f
    private var tableT = 0f
    private var tableR = 0f
    private var tableB = 0f
    private var tableCX = 0f
    private var tableCY = 0f

    init {
        aimPaint.color = Color.parseColor("#FFEB3B")
        aimPaint.strokeWidth = 5f
        aimPaint.style = Paint.Style.STROKE
        aimPaint.strokeCap = Paint.Cap.ROUND

        bankPaint.color = Color.parseColor("#00E676")
        bankPaint.strokeWidth = 4f
        bankPaint.style = Paint.Style.STROKE
        bankPaint.strokeCap = Paint.Cap.ROUND
        bankPaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)

        gridPaint.color = Color.parseColor("#66FFFFFF")
        gridPaint.strokeWidth = 2f
        gridPaint.style = Paint.Style.STROKE
        gridPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)

        handleFill.color = Color.WHITE
        handleFill.style = Paint.Style.FILL
        handleStroke.color = Color.parseColor("#FFEB3B")
        handleStroke.style = Paint.Style.STROKE
        handleStroke.strokeWidth = 3f

        hitPaint.color = Color.parseColor("#00E676")
        hitPaint.style = Paint.Style.FILL

        arrowPaint.color = Color.parseColor("#FFEB3B")
        arrowPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // معايرة لطاولة 8 Ball Pool الأفقية
        tableL = w * 0.115f
        tableR = w * 0.885f
        tableT = h * 0.175f
        tableB = h * 0.825f
        tableCX = (tableL + tableR) / 2f
        tableCY = (tableT + tableB) / 2f
    }

    fun addNewLine() {
        val bank = if (bankMode) 1f else 0f
        lines.add(floatArrayOf(tableCX - 220f, tableCY, tableCX + 220f, tableCY, bank))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        showGrid = false
        invalidate()
    }

    fun clearAutoLine() { invalidate() }
    fun setAutoLine(x1: Float, y1: Float, x2: Float, y2: Float) { }

    fun toggleBankMode() { bankMode = !bankMode }
    fun toggleGrid() { showGrid = !showGrid; invalidate() }
    fun enableEditMode(enabled: Boolean) { isEditMode = enabled; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (showGrid) drawCenterGrid(canvas)

        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], aimPaint)
            drawArrowHead(canvas, line[0], line[1], line[2], line[3])

            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }
            if (line[4] > 0.5f) {
                drawLongPath(canvas, line[0], line[1], line[2], line[3])
            } else {
                // حتى الخط العادي يمتد شوي بعد النهاية
                extendForward(canvas, line[0], line[1], line[2], line[3], 1.8f)
            }
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        val angle = atan2(y2 - y1, x2 - x1)
        val size = 26f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - size * cos(angle - 0.5f), y2 - size * sin(angle - 0.5f))
        path.lineTo(x2 - size * cos(angle + 0.5f), y2 - size * sin(angle + 0.5f))
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    private fun extendForward(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float, factor: Float) {
        val dx = ex - sx
        val dy = ey - sy
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        canvas.drawLine(ex, ey, ex + ux * len * factor, ey + uy * len * factor, bankPaint)
    }

    private fun drawCenterGrid(canvas: Canvas) {
        val count = 24
        for (i in 0 until count) {
            val angle = (Math.PI * 2.0 * i / count).toFloat()
            drawRay(canvas, tableCX, tableCY, cos(angle), sin(angle), gridPaint, 3)
        }
    }

    private fun drawRay(canvas: Canvas, ox: Float, oy: Float, dirX0: Float, dirY0: Float, paint: Paint, maxBounces: Int) {
        var cx = ox
        var cy = oy
        var dirX = dirX0
        var dirY = dirY0
        for (b in 0..maxBounces) {
            val hit = findHit(cx, cy, dirX, dirY) ?: run {
                canvas.drawLine(cx, cy, cx + dirX * 2000f, cy + dirY * 2000f, paint)
                return
            }
            canvas.drawLine(cx, cy, hit[0], hit[1], paint)
            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            cx = hit[0] + dirX * 2f
            cy = hit[1] + dirY * 2f
        }
    }

    private fun drawLongPath(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float) {
        val dx = ex - sx
        val dy = ey - sy
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        var dirX = dx / len
        var dirY = dy / len
        var cx = ex
        var cy = ey
        for (bounce in 0..8) {
            val hit = findHit(cx, cy, dirX, dirY) ?: run {
                canvas.drawLine(cx, cy, cx + dirX * 2500f, cy + dirY * 2500f, bankPaint)
                return
            }
            canvas.drawLine(cx, cy, hit[0], hit[1], bankPaint)
            canvas.drawCircle(hit[0], hit[1], 9f, hitPaint)
            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            cx = hit[0] + dirX * 3f
            cy = hit[1] + dirY * 3f
        }
    }

    private fun findHit(cx: Float, cy: Float, dirX: Float, dirY: Float): FloatArray? {
        var tHit = Float.MAX_VALUE
        var hx = 0f
        var hy = 0f
        var nx = 0f
        var ny = 0f
        if (dirX < -1e-4f) {
            val t = (tableL - cx) / dirX
            if (t > 1f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= tableT - 1 && yy <= tableB + 1) {
                    tHit = t; hx = tableL; hy = yy; nx = 1f; ny = 0f
                }
            }
        }
        if (dirX > 1e-4f) {
            val t = (tableR - cx) / dirX
            if (t > 1f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= tableT - 1 && yy <= tableB + 1) {
                    tHit = t; hx = tableR; hy = yy; nx = -1f; ny = 0f
                }
            }
        }
        if (dirY < -1e-4f) {
            val t = (tableT - cy) / dirY
            if (t > 1f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= tableL - 1 && xx <= tableR + 1) {
                    tHit = t; hx = xx; hy = tableT; nx = 0f; ny = 1f
                }
            }
        }
        if (dirY > 1e-4f) {
            val t = (tableB - cy) / dirY
            if (t > 1f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= tableL - 1 && xx <= tableR + 1) {
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
                    if (dist(event.x, event.y, line[0], line[1]) < handleR * 2f) {
                        activeIndex = i; dragStart = true; return true
                    }
                    if (dist(event.x, event.y, line[2], line[3]) < handleR * 2f) {
                        activeIndex = i; dragStart = false; return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeIndex >= 0 && activeIndex < lines.size) {
                    val line = lines[activeIndex]
                    if (dragStart) { line[0] = event.x; line[1] = event.y }
                    else { line[2] = event.x; line[3] = event.y }
                    invalidate(); return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeIndex = -1
        }
        return activeIndex >= 0
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val a = x1 - x2; val b = y1 - y2
        return sqrt(a * a + b * b)
    }
}
