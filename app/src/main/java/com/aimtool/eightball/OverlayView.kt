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
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 34f

    // حدود الطاولة (نسبة من الشاشة – أدق من القيم الثابتة)
    private var tableL = 0f
    private var tableT = 0f
    private var tableR = 0f
    private var tableB = 0f

    init {
        aimPaint.color = Color.parseColor("#FFEB3B")
        aimPaint.strokeWidth = 5f
        aimPaint.style = Paint.Style.STROKE
        aimPaint.strokeCap = Paint.Cap.ROUND

        bankPaint.color = Color.parseColor("#00E676")
        bankPaint.strokeWidth = 4.5f
        bankPaint.style = Paint.Style.STROKE
        bankPaint.strokeCap = Paint.Cap.ROUND
        bankPaint.pathEffect = DashPathEffect(floatArrayOf(14f, 9f), 0f)

        handleFill.color = Color.WHITE
        handleFill.style = Paint.Style.FILL

        handleStroke.color = Color.parseColor("#FFEB3B")
        handleStroke.style = Paint.Style.STROKE
        handleStroke.strokeWidth = 3f

        hitPaint.color = Color.parseColor("#00E676")
        hitPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // تقدير حدود الطاولة حسب نسبة شاشة اللعبة الشائعة
        tableL = w * 0.08f
        tableR = w * 0.92f
        tableT = h * 0.14f
        tableB = h * 0.86f
    }

    fun addNewLine() {
        val cx = width / 2f
        val cy = height / 2f
        val bank = if (bankMode) 1f else 0f
        lines.add(floatArrayOf(cx - 180f, cy, cx + 180f, cy, bank))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        invalidate()
    }

    fun clearAutoLine() {
        // معطّل حالياً
        invalidate()
    }

    fun setAutoLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        // معطّل – الكشف الأحمر كان غير دقيق
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

        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], aimPaint)

            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }

            if (line[4] > 0.5f) {
                drawMultiBank(canvas, line[0], line[1], line[2], line[3])
            }
        }
    }

    /**
     * ارتداد متعدد: يمتد الخط ويرتد على الحواف عدة مرات
     * حتى يقطع مسافة طويلة أو يخرج من الطاولة
     */
    private fun drawMultiBank(canvas: Canvas, sx: Float, sy: Float, ex: Float, ey: Float) {
        var x0 = sx
        var y0 = sy
        var x1 = ex
        var y1 = ey

        var dx = x1 - x0
        var dy = y1 - y0
        var len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        var dirX = dx / len
        var dirY = dy / len

        // نبدأ من نهاية الخط الأساسي
        var cx = x1
        var cy = y1
        val maxBounces = 6
        val segmentLen = 900f

        for (bounce in 0 until maxBounces) {
            // ابحث عن أقرب اصطدام مع حافة
            var tHit = Float.MAX_VALUE
            var nx = 0f
            var ny = 0f
            var hitX = cx
            var hitY = cy

            // حافة يسار
            if (dirX < -0.0001f) {
                val t = (tableL - cx) / dirX
                if (t > 0.5f && t < tHit) {
                    val yy = cy + dirY * t
                    if (yy >= tableT && yy <= tableB) {
                        tHit = t; hitX = tableL; hitY = yy; nx = 1f; ny = 0f
                    }
                }
            }
            // حافة يمين
            if (dirX > 0.0001f) {
                val t = (tableR - cx) / dirX
                if (t > 0.5f && t < tHit) {
                    val yy = cy + dirY * t
                    if (yy >= tableT && yy <= tableB) {
                        tHit = t; hitX = tableR; hitY = yy; nx = -1f; ny = 0f
                    }
                }
            }
            // حافة فوق
            if (dirY < -0.0001f) {
                val t = (tableT - cy) / dirY
                if (t > 0.5f && t < tHit) {
                    val xx = cx + dirX * t
                    if (xx >= tableL && xx <= tableR) {
                        tHit = t; hitX = xx; hitY = tableT; nx = 0f; ny = 1f
                    }
                }
            }
            // حافة تحت
            if (dirY > 0.0001f) {
                val t = (tableB - cy) / dirY
                if (t > 0.5f && t < tHit) {
                    val xx = cx + dirX * t
                    if (xx >= tableL && xx <= tableR) {
                        tHit = t; hitX = xx; hitY = tableB; nx = 0f; ny = -1f
                    }
                }
            }

            if (tHit == Float.MAX_VALUE || tHit > segmentLen) {
                // مافي اصطدام قريب – ارسم امتداد طويل وانتهي
                canvas.drawLine(cx, cy, cx + dirX * segmentLen, cy + dirY * segmentLen, bankPaint)
                break
            }

            // ارسم لحد نقطة الاصطدام
            canvas.drawLine(cx, cy, hitX, hitY, bankPaint)
            canvas.drawCircle(hitX, hitY, 10f, hitPaint)

            // انعكاس: R = V - 2(V·N)N
            val dot = dirX * nx + dirY * ny
            dirX = dirX - 2f * dot * nx
            dirY = dirY - 2f * dot * ny

            // نقطة البداية للقطعة التالية (شوي جوا الطاولة عشان ما يعلق)
            cx = hitX + dirX * 2f
            cy = hitY + dirY * 2f
        }
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
