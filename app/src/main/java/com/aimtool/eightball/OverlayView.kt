package com.aimtool.eightball

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class OverlayView(context: Context) : View(context) {

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(15f, 8f), 0f)
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val pointStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val lines = mutableListOf<AimLine>()
    private var bankMode = false

    data class AimLine(
        var start: PointF,
        var end: PointF,
        var isBank: Boolean = false
    )

    fun addNewLine() {
        val centerX = width / 2f
        val centerY = height / 2f
        lines.add(
            AimLine(
                PointF(centerX - 150f, centerY),
                PointF(centerX + 150f, centerY),
                bankMode
            )
        )
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        invalidate()
    }

    fun toggleBankMode() {
        bankMode = !bankMode
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (line in lines) {
            val paint = if (line.isBank) bankPaint else aimPaint
            canvas.drawLine(line.start.x, line.start.y, line.end.x, line.end.y, paint)
            drawHandle(canvas, line.start)
            drawHandle(canvas, line.end)

            if (line.isBank) {
                drawSimpleBankReflection(canvas, line)
            }
        }
    }

    private fun drawHandle(canvas: Canvas, point: PointF) {
        canvas.drawCircle(point.x, point.y, 18f, pointPaint)
        canvas.drawCircle(point.x, point.y, 18f, pointStroke)
    }

    private fun drawSimpleBankReflection(canvas: Canvas, line: AimLine) {
        val dx = line.end.x - line.start.x
        val dy = line.end.y - line.start.y
        val reflectedEnd = PointF(line.end.x - dx * 0.8f, line.end.y + dy * 0.6f)

        canvas.drawLine(
            line.end.x, line.end.y,
            reflectedEnd.x, reflectedEnd.y,
            bankPaint
        )
        drawHandle(canvas, reflectedEnd)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}
