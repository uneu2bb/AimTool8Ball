package com.aimtool.eightball

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    data class AimLine(
        var startX: Float,
        var startY: Float,
        var endX: Float,
        var endY: Float,
        var isBank: Boolean = false
    )

    private val lines = mutableListOf<AimLine>()
    private var bankMode = false
    var editMode = true

    private var activeIndex = -1
    private var draggingStart = true
    private val handleRadius = 30f

    private var tableLeft = 60f
    private var tableTop = 160f
    private var tableRight = 1000f
    private var tableBottom = 1800f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tableRight = w - 60f
        tableBottom = h - 160f
    }

    fun addNewLine() {
        val cx = width / 2f
        val cy = height / 2f
        lines.add(AimLine(cx - 180f, cy, cx + 180f, cy, bankMode))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        invalidate()
    }

    fun toggleBankMode() {
        bankMode = !bankMode
    }

    fun setEditMode(enabled: Boolean) {
        editMode = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (line in lines) {
            canvas.drawLine(line.startX, line.startY, line.endX, line.endY, aimPaint)

            if (editMode) {
                drawHandle(canvas, line.startX, line.startY)
                drawHandle(canvas, line.endX, line.endY)
            }

            if (line.isBank) {
                drawBankReflection(canvas, line)
            }
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius, handlePaint)
        canvas.drawCircle(x, y, handleRadius, handleStroke)
    }

    private fun drawBankReflection(canvas: Canvas, line: AimLine) {
        val dx = line.endX - line.startX
        val dy = line.endY - line.startY
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        val dirX = dx / len
        val dirY = dy / len

        var hitX = line.endX
        var hitY = line.endY
        var nx = 0f
        var ny = 0f
        var found = false

        var t = 0f
        while (t < 2000f && !found) {
            t += 4f
            val px = line.endX + dirX * t
            val py = line.endY + dirY * t

            if (px <= tableLeft) {
                hitX = tableLeft; hitY = py; nx = 1f; ny = 0f; found = true
            } else if (px >= tableRight) {
                hitX = tableRight; hitY = py; nx = -1f; ny = 0f; found = true
            } else if (py <= tableTop) {
                hitX = px; hitY = tableTop; nx = 0f; ny = 1f; found = true
            } else if (py >= tableBottom) {
                hitX = px; hitY = tableBottom; nx = 0f; ny = -1f; found = true
            }
        }

        if (!found) return

        canvas.drawLine(line.endX, line.endY, hitX, hitY, bankPaint)

        val dot = dirX * nx + dirY * ny
        val rx = dirX - 2f * dot * nx
        val ry = dirY - 2f * dot * ny

        val reflectLen = 400f
        canvas.drawLine(hitX, hitY, hitX + rx * reflectLen, hitY + ry * reflectLen, bankPaint)
        canvas.drawCircle(hitX, hitY, 14f, handlePaint)
        canvas.drawCircle(hitX, hitY, 14f, bankPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                for (i in lines.indices) {
                    val line = lines[i]
                    if (dist(event.x, event.y, line.startX, line.startY) < handleRadius * 2f) {
                        activeIndex = i
                        draggingStart = true
                        return true
                    }
                    if (dist(event.x, event.y, line.endX, line.endY) < handleRadius * 2f) {
                        activeIndex = i
                        draggingStart = false
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeIndex >= 0 && activeIndex < lines.size) {
                    val line = lines[activeIndex]
                    if (draggingStart) {
                        line.startX = event.x
                        line.startY = event.y
                    } else {
                        line.endX = event.x
                        line.endY = event.y
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
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }
}
