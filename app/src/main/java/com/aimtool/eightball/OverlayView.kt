package com.aimtool.eightball

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
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

    // كل خط: startX, startY, endX, endY, isBank(0/1)
    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true
    private var showGrid = false

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 34f

    // حدود الطاولة – نسب أدق للوضع الأفقي بلعبة 8 Ball
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

        gridPaint.color = Color.parseColor("#80FFFFFF")
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
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // نسب تقريبية لطاولة 8 Ball Pool الأفقية
        tableL = w * 0.12f
        tableR = w * 0.88f
        tableT = h * 0.18f
        tableB = h * 0.82f
        tableCX = (tableL + tableR) / 2f
        tableCY = (tableT + tableB) / 2f
    }

    fun addNewLine() {
        val bank = if (bankMode) 1f else 0f
        lines.add(floatArrayOf(tableCX - 200f, tableCY, tableCX + 200f, tableCY, bank))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        showGrid = false
        invalidate()
    }

    fun clearAutoLine() {
        invalidate()
    }

    fun setAutoLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        // معطّل
    }

    fun toggleBankMode() {
        bankMode = !bankMode
    }

    fun toggleGrid() {
        showGrid = !showGrid
        invalidate()
    }

    fun enableEditMode(enabled: Boolean) {
        isEditMode = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (showGrid) {
            drawCenterGrid(canvas)
        }

        for (line in lines) {
            canvas.drawLine(line[0], line[1], line[2], line[3], aimPaint)

            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }

            // دائماً نمدد المسار من اتجاه الخط (حتى لو مش Bank)
            // وإذا Bank نرسم ارتدادات متعددة
            if (line[4] > 0.5f) {
                drawLongPath(canvas, line[0], line[1], line[2], line[3], true)
            }
        }
    }

    /** شبكة خطوط من وسط الطاولة لكل الاتجاهات */
    private fun drawCenterGrid(canvas: Canvas) {
        val count = 24 // كل 15 درجة
        val radius = maxOf(tableR - tableL, tableB - tableT)
        for (i in 0 until count) {
            val angle = (Math.PI * 2.0 * i / count).toFloat()
            val ex = tableCX + cos(angle) * radius
            val ey = tableCY + sin(angle) * radius
            // خط من المركز مع ارتداد بسيط على الحدود
            drawRayWithBounce(canvas, tableCX, tableCY, cos(angle), sin(angle), gridPaint, 3)
        }
    }

    private fun drawRayWithBounce(
        canvas: Canvas,
        ox: Float,
        oy: Float,
        dirX0: Float,
        dirY0: Float,
        paint: Paint,
        maxBounces: Int
    ) {
        var cx = ox
        var cy = oy
        var dirX = dirX0
        var dirY = dirY0
        val maxLen = 2000f

        for (b in 0..maxBounces) {
            val hit = findHit(cx, cy, dirX, dirY)
            if (hit == null) {
                canvas.drawLine(cx, cy, cx + dirX * maxLen, cy + dirY * maxLen, paint)
                break
            }
            canvas.drawLine(cx, cy, hit[0], hit[1], paint)
            // انعكاس
            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            cx = hit[0] + dirX * 2f
            cy = hit[1] + dirY * 2f
        }
    }

    /**
     * مسار طويل من نقطة البداية باتجاه النهاية
     * مع ارتدادات متعددة على حدود الطاولة
     */
    private fun drawLongPath(
        canvas: Canvas,
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        withBounces: Boolean
    ) {
        var dx = ex - sx
        var dy = ey - sy
        var len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        var dirX = dx / len
        var dirY = dy / len

        // نبدأ من نهاية الخط الأصفر ونكمل بنفس الاتجاه
        var cx = ex
        var cy = ey
        val maxBounces = if (withBounces) 8 else 0
        val maxSeg = 2500f

        for (bounce in 0..maxBounces) {
            val hit = findHit(cx, cy, dirX, dirY)
            if (hit == null) {
                canvas.drawLine(cx, cy, cx + dirX * maxSeg, cy + dirY * maxSeg, bankPaint)
                break
            }
            val hitX = hit[0]
            val hitY = hit[1]
            val nx = hit[2]
            val ny = hit[3]

            canvas.drawLine(cx, cy, hitX, hitY, bankPaint)
            canvas.drawCircle(hitX, hitY, 9f, hitPaint)

            if (bounce == maxBounces) break

            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            cx = hitX + dirX * 3f
            cy = hitY + dirY * 3f
        }
    }

    /**
     * يرجع: [hitX, hitY, normalX, normalY] أو null
     */
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
