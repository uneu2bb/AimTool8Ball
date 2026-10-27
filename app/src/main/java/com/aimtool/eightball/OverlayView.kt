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
import kotlin.math.sin
import kotlin.math.sqrt

class OverlayView(context: Context) : View(context) {

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pocketRing = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pathColors = intArrayOf(
        Color.parseColor("#FF1744"),
        Color.parseColor("#00E676"),
        Color.parseColor("#FFEB3B"),
        Color.parseColor("#D500F9"),
        Color.parseColor("#00B0FF"),
        Color.parseColor("#FF9100"),
        Color.parseColor("#FFFFFF")
    )

    private val pathPaints = Array(pathColors.size) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pathColors[i]
            strokeWidth = 6.5f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val cushionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99FFEB3B")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val lines = ArrayList<FloatArray>()
    private var bankMode = false
    private var isEditMode = true
    private var showGrid = false

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 36f

    private var tableL = 0f
    private var tableT = 0f
    private var tableR = 0f
    private var tableB = 0f
    private var tableCX = 0f
    private var tableCY = 0f

    // مواقع الجيوب الستة
    private val pockets = Array(6) { FloatArray(2) }

    init {
        aimPaint.color = Color.parseColor("#FFEB3B")
        aimPaint.strokeWidth = 7f
        aimPaint.style = Paint.Style.STROKE
        aimPaint.strokeCap = Paint.Cap.ROUND

        handleFill.color = Color.WHITE
        handleFill.style = Paint.Style.FILL
        handleStroke.color = Color.parseColor("#FFEB3B")
        handleStroke.style = Paint.Style.STROKE
        handleStroke.strokeWidth = 3.5f

        hitPaint.style = Paint.Style.FILL

        arrowPaint.style = Paint.Style.FILL

        pocketPaint.color = Color.parseColor("#E0FFFFFF")
        pocketPaint.style = Paint.Style.FILL

        pocketRing.color = Color.parseColor("#FFEB3B")
        pocketRing.style = Paint.Style.STROKE
        pocketRing.strokeWidth = 3f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // حدود أدق لسطح اللعب داخل الإطار الخشبي
        tableL = w * 0.125f
        tableR = w * 0.875f
        tableT = h * 0.195f
        tableB = h * 0.805f
        tableCX = (tableL + tableR) / 2f
        tableCY = (tableT + tableB) / 2f

        // 6 جيوب: 4 زوايا + وسط الأعلى والأسفل
        pockets[0][0] = tableL; pockets[0][1] = tableT           // أعلى يسار
        pockets[1][0] = tableCX; pockets[1][1] = tableT          // أعلى وسط
        pockets[2][0] = tableR; pockets[2][1] = tableT           // أعلى يمين
        pockets[3][0] = tableL; pockets[3][1] = tableB           // أسفل يسار
        pockets[4][0] = tableCX; pockets[4][1] = tableB          // أسفل وسط
        pockets[5][0] = tableR; pockets[5][1] = tableB           // أسفل يمين
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

        if (showGrid) {
            drawPocketGuides(canvas)
            drawCushionGuides(canvas)
            drawPocketMarkers(canvas)
        }

        for ((index, line) in lines.withIndex()) {
            val colorIndex = index % pathPaints.size
            val paint = pathPaints[colorIndex]

            canvas.drawLine(line[0], line[1], line[2], line[3], paint)
            drawArrowHead(canvas, line[0], line[1], line[2], line[3], paint.color)

            if (isEditMode) {
                canvas.drawCircle(line[0], line[1], handleR, handleFill)
                canvas.drawCircle(line[0], line[1], handleR, handleStroke)
                canvas.drawCircle(line[2], line[3], handleR, handleFill)
                canvas.drawCircle(line[2], line[3], handleR, handleStroke)
            }

            // مسار ارتداد طويل
            drawProTrajectory(canvas, line[0], line[1], line[2], line[3], colorIndex)

            // خطوط من نهاية التوجيه نحو كل جيب
            if (showGrid) {
                drawLinesToPockets(canvas, line[2], line[3], colorIndex)
            }
        }
    }

    /** خطوط سميكة من وسط الطاولة ومن نقاط مهمة نحو الجيوب */
    private fun drawPocketGuides(canvas: Canvas) {
        // من المركز لكل جيب
        for (i in pockets.indices) {
            val p = pathPaints[i % pathPaints.size]
            val thick = Paint(p)
            thick.strokeWidth = 5.5f
            thick.alpha = 180
            canvas.drawLine(tableCX, tableCY, pockets[i][0], pockets[i][1], thick)
        }

        // خطوط بين الجيوب المتقابلة (مساعدة زاوية)
        val cross = Paint(guidePaint)
        cross.strokeWidth = 4f
        cross.alpha = 140
        // زوايا متقابلة
        canvas.drawLine(pockets[0][0], pockets[0][1], pockets[5][0], pockets[5][1], cross)
        canvas.drawLine(pockets[2][0], pockets[2][1], pockets[3][0], pockets[3][1], cross)
        // وسط علوي/سفلي
        canvas.drawLine(pockets[1][0], pockets[1][1], pockets[4][0], pockets[4][1], cross)
    }

    /** خطوط مساعدة على البنود (حواشي الطاولة) */
    private fun drawCushionGuides(canvas: Canvas) {
        val n = 5 // تقسيم كل ضلع
        val paint = Paint(cushionPaint)
        paint.strokeWidth = 3.5f

        for (i in 1 until n) {
            val fx = i / n.toFloat()
            // عمودي من الأعلى للأسفل
            val x = tableL + (tableR - tableL) * fx
            canvas.drawLine(x, tableT, x, tableB, paint)
            // أفقي من اليسار لليمين
            val y = tableT + (tableB - tableT) * fx
            canvas.drawLine(tableL, y, tableR, y, paint)
        }

        // إطار الطاولة أوضح
        val border = Paint(guidePaint)
        border.strokeWidth = 6f
        border.color = Color.parseColor("#AAFFEB3B")
        canvas.drawRect(tableL, tableT, tableR, tableB, border)
    }

    private fun drawPocketMarkers(canvas: Canvas) {
        for (p in pockets) {
            canvas.drawCircle(p[0], p[1], 14f, pocketPaint)
            canvas.drawCircle(p[0], p[1], 14f, pocketRing)
        }
    }

    private fun drawLinesToPockets(canvas: Canvas, x: Float, y: Float, colorIndex: Int) {
        for (i in pockets.indices) {
            val paint = Paint(pathPaints[(colorIndex + i) % pathPaints.size])
            paint.strokeWidth = 3.5f
            paint.alpha = 160
            canvas.drawLine(x, y, pockets[i][0], pockets[i][1], paint)
        }
    }

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
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return

        var dirX = dx / len
        var dirY = dy / len
        var cx = ex
        var cy = ey

        for (bounce in 0..10) {
            val paint = Paint(pathPaints[(startColor + bounce) % pathPaints.size])
            paint.strokeWidth = 6f
            val hit = findHit(cx, cy, dirX, dirY)
            if (hit == null) {
                canvas.drawLine(cx, cy, cx + dirX * 2200f, cy + dirY * 2200f, paint)
                break
            }
            canvas.drawLine(cx, cy, hit[0], hit[1], paint)
            hitPaint.color = paint.color
            canvas.drawCircle(hit[0], hit[1], 10f, hitPaint)

            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            cx = hit[0] + dirX * 3f
            cy = hit[1] + dirY * 3f
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        arrowPaint.color = color
        val angle = atan2(y2 - y1, x2 - x1)
        val size = 28f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - size * cos(angle - 0.5f), y2 - size * sin(angle - 0.5f))
        path.lineTo(x2 - size * cos(angle + 0.5f), y2 - size * sin(angle + 0.5f))
        path.close()
        canvas.drawPath(path, arrowPaint)
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
