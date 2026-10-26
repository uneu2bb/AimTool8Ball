package com.aimtool.eightball

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

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

    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    data class AimLine(
        var start: PointF,
        var end: PointF,
        var isBank: Boolean = false
    )

    private val lines = mutableListOf<AimLine>()
    private var bankMode = false
    var editMode = true

    // للسحب
    private var activeLine: AimLine? = null
    private var draggingStart = true
    private val handleRadius = 28f

    // حدود الطاولة التقريبية (نقدر نعدلها لاحقاً)
    private var tableLeft = 80f
    private var tableTop = 180f
    private var tableRight = 0f
    private var tableBottom = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tableRight = w - 80f
        tableBottom = h - 180f
    }

    fun addNewLine() {
        val cx = width / 2f
        val cy = height / 2f
        lines.add(
            AimLine(
                PointF(cx - 180f, cy),
                PointF(cx + 180f, cy),
                bankMode
            )
        )
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeLine = null
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
            // الخط الأساسي
            canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, aimPaint)

            // مقابض السحب (بس بوضع التعديل)
            if (editMode) {
                drawHandle(canvas, line.start)
                drawHandle(canvas, line.end)
            }

            // حساب ورسم الارتداد إذا Bank
            if (line.isBank) {
                drawBankReflection(canvas, line)
            }
        }
    }

    private fun drawHandle(canvas: Canvas, point: PointF) {
        canvas.drawCircle(point.x, point.y, handleRadius, handlePaint)
        canvas.drawCircle(point.x, point.y, handleRadius, handleStroke)
    }

    /**
     * حساب انعكاس حقيقي على حدود الطاولة
     */
    private fun drawBankReflection(canvas: Canvas, line: AimLine) {
        val dx = line.end.x - line.start.x
        val dy = line.end.y - line.start.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        val dirX = dx / len
        val dirY = dy / len

        // نمدد الخط لحد ما يصطدم بحافة
        var hitX = line.end.x
        var hitY = line.end.y
        var nx = 0f // normal x
        var ny = 0f // normal y
        var found = false

        // نفحص الاصطدام مع الحواف الأربعة
        val steps = 800
        for (i in 1..steps) {
            val t = i * 3f
            val px = line.end.x + dirX * t
            val py = line.end.y + dirY * t

            when {
                px <= tableLeft -> {
                    hitX = tableLeft; hitY = py; nx = 1f; ny = 0f; found = true
                }
                px >= tableRight -> {
                    hitX = tableRight; hitY = py; nx = -1f; ny = 0f; found = true
                }
                py <= tableTop -> {
                    hitX = px; hitY = tableTop; nx = 0f; ny = 1f; found = true
                }
                py >= tableBottom -> {
                    hitX = px; hitY = tableBottom; nx = 0f; ny = -1f; found = true
                }
            }
            if (found) break
        }

        if (!found) return

        // رسم الخط لحد نقطة الاصطدام
        canvas.drawLine(line.end.x, line.end.y, hitX, hitY, bankPaint)

        // حساب الانعكاس: R = V - 2(V·N)N
        val dot = dirX * nx + dirY * ny
        val rx = dirX - 2 * dot * nx
        val ry = dirY - 2 * dot * ny

        // رسم الخط المنعكس
        val reflectLen = 350f
        val endX = hitX + rx * reflectLen
        val endY = hitY + ry * reflectLen
        canvas.drawLine(hitX, hitY, endX, endY, bankPaint)

        // نقطة الاصطدام
        canvas.drawCircle(hitX, hitY, 12f, handlePaint)
        canvas.drawCircle(hitX, hitY, 12f, bankPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // ندور على أقرب مقبض
                for (line in lines) {
                    if (distance(event.x, event.y, line.start) < handleRadius * 1.8f) {
                        activeLine = line
                        draggingStart = true
                        return true
                    }
                    if (distance(event.x, event.y, line.end) < handleRadius * 1.8f) {
                        activeLine = line
                        draggingStart = false
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                activeLine?.let { line ->
                    if (draggingStart) {
                        line.start.x = event.x
                        line.start.y = event.y
                    } else {
                        line.end.x = event.x
                        line.end.y = event.y
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeLine = null
            }
        }
        return activeLine != null
    }

    private fun distance(x: Float, y: Float, p: PointF): Float {
        return sqrt((x - p.x).pow(2) + (y - p.y).pow(2))
    }
}
