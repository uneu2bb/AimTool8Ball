package com.aimtool.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val autoPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 32f

    private var tableL = 50f
    private var tableT = 150f
    private var tableR = 1000f
    private var tableB = 1800f

    // خط الكشف التلقائي
    private var autoX1 = 0f
    private var autoY1 = 0f
    private var autoX2 = 0f
    private var autoY2 = 0f
    private var hasAutoLine = false

    init {
        aimPaint.color = Color.parseColor("#FFEB3B")
        aimPaint.strokeWidth = 5f
        aimPaint.style = Paint.Style.STROKE
        aimPaint.strokeCap = Paint.Cap.ROUND

        bankPaint.color = Color.parseColor("#00E676")
        bankPaint.strokeWidth = 4.5f
        bankPaint.style = Paint.Style.STROKE
        bankPaint.strokeCap = Paint.Cap.ROUND
        bankPaint.pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)

        autoPaint.color = Color.parseColor("#FF1744")
        autoPaint.strokeWidth = 6f
        autoPaint.style = Paint.Style.STROKE
        autoPaint.strokeCap = Paint.Cap.ROUND
        autoPaint.pathEffect = DashPathEffect(floatArrayOf(22f, 12f), 0f)

        handleFill.color = Color.WHITE
        handleFill.style = Paint.Style.FILL

        handleStroke.color = Color.parseColor("#FFEB3B")
        handleStroke.style = Paint.Style.STROKE
        handleStroke.strokeWidth = 3f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tableR = w - 50f
        tableB = h - 150f
    }

    fun addNewLine() {
        val cx = width / 2f
        val cy = height / 2f
        val bank = if (bankMode) 1f else 0f
        lines.add(floatArrayOf(cx - 160f, cy, cx + 160f, cy, bank))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        invalidate()
    }

    fun clearAutoLine() {
        hasAutoLine = false
        invalidate()
    }

    fun setAutoLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        autoX1 = x1
        autoY1 = y1
        autoX2 = x2
        autoY2 = y2
        hasAutoLine = true
        post { invalidate() }
    }

    fun toggleBankMode() {
        bankMode = !bankMode
    }

    fun enableEditMode(enabled: Boolean) {
        isEditMode = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // الخط التلقائي (أحمر متقطع)
        if (hasAutoLine) {
            canvas.drawLine(autoX1, autoY1, autoX2, autoY2, autoPaint)
        }

        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], aimPaint)
            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }
            if (line[4] > 0.5f) {
                drawBank(canvas, line)
            }
        }
    }

    private fun drawBank(canvas: Canvas, line: FloatArray) {
        val dx = line[2] - line[0]
        val dy = line[3] - line[1]
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        val dirX = dx / len
        val dirY = dy / len

        var hitX = line[2]
        var hitY = line[3]
        var nx = 0f
        var ny = 0f
        var found = false
        var t = 0f

        while (t < 2500f && !found) {
            t += 5f
            val px = line[2] + dirX * t
            val py = line[3] + dirY * t
            if (px <= tableL) {
                hitX = tableL; hitY = py; nx = 1f; ny = 0f; found = true
            } else if (px >= tableR) {
                hitX = tableR; hitY = py; nx = -1f; ny = 0f; found = true
            } else if (py <= tableT) {
                hitX = px; hitY = tableT; nx = 0f; ny = 1f; found = true
            } else if (py >= tableB) {
                hitX = px; hitY = tableB; nx = 0f; ny = -1f; found = true
            }
        }
        if (!found) return

        canvas.drawLine(line[2], line[3], hitX, hitY, bankPaint)
        val dot = dirX * nx + dirY * ny
        val rx = dirX - 2f * dot * nx
        val ry = dirY - 2f * dot * ny
        canvas.drawLine(hitX, hitY, hitX + rx * 380f, hitY + ry * 380f, bankPaint)
        canvas.drawCircle(hitX, hitY, 12f, handleFill)
        canvas.drawCircle(hitX, hitY, 12f, bankPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in lines.indices) {
                    val line = lines[i]
                    if (dist(event.x, event.y, line[0], line[1]) < handleR * 2f) {
                        activeIndex = i
                        dragStart = true
                        return true
                    }
                    if (dist(event.x, event.y, line[2], line[3]) < handleR * 2f) {
                        activeIndex = i
                        dragStart = false
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeIndex >= 0 && activeIndex < lines.size) {
                    val line = lines[activeIndex]
                    if (dragStart) {
                        line[0] = event.x
                        line[1] = event.y
                    } else {
                        line[2] = event.x
                        line[3] = event.y
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeIndex = -1
            }
        }
        return activeIndex >= 0
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val a = x1 - x2
        val b = y1 - y2
        return sqrt(a * a + b * b)
    }
}
