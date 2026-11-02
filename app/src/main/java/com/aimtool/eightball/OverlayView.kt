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
 * يحافظ على: خط تصويب، تصادم، شبكة، مقابض
 * يضيف: معايرة 4 زوايا للطاولة + علامات كرات يدوية
 */
class OverlayView(context: Context) : View(context) {

    private val table = TableGeometry()
    private val balls = ArrayList<BallMarker>()
    private val lines = ArrayList<FloatArray>() // x1,y1,x2,y2,mode

    private var isEditMode = true
    private var showGrid = false
    private var calibrateMode = false
    private var ballPlaceMode = false

    private var activeLine = -1
    private var dragStart = true
    private var activeCorner = -1
    private var activeBall = -1

    private val handleR = 40f

    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00"); strokeWidth = 7f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val cueGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00"); strokeWidth = 16f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; alpha = 50
    }
    private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676"); strokeWidth = 6.5f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val objectGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676"); strokeWidth = 14f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; alpha = 50
    }
    private val deflectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF"); strokeWidth = 6f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val deflectGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF"); strokeWidth = 13f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; alpha = 50
    }
    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744"); strokeWidth = 5f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.FILL
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00"); style = Paint.Style.STROKE; strokeWidth = 3.5f
    }
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF"); style = Paint.Style.FILL
    }
    private val ghostStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pocketFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDFFFFFF"); style = Paint.Style.FILL
    }
    private val pocketRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00"); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFEA00"); strokeWidth = 5f; style = Paint.Style.STROKE
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9100"); style = Paint.Style.FILL
    }
    private val cornerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val cueBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFDE7"); style = Paint.Style.FILL
    }
    private val objBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935"); style = Paint.Style.FILL
    }
    private val ballRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 28f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        table.initFromScreen(w.toFloat(), h.toFloat())
    }

    fun addAimLine() {
        val cx = table.centerX
        val cy = table.centerY
        lines.add(floatArrayOf(cx - 200f, cy + 80f, cx + 160f, cy - 40f, 0f))
        invalidate()
    }

    fun addCollisionShot() {
        val cx = table.centerX
        val cy = table.centerY
        // إذا في كرة بيضاء وكرة أخرى معلّمة — اربط بينهما
        val cue = balls.firstOrNull { it.isCue }
        val obj = balls.firstOrNull { !it.isCue }
        if (cue != null && obj != null) {
            lines.add(floatArrayOf(cue.x, cue.y, obj.x, obj.y, 1f))
        } else {
            lines.add(floatArrayOf(cx - 180f, cy + 100f, cx + 120f, cy - 20f, 1f))
        }
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeLine = -1
        showGrid = false
        invalidate()
    }

    fun clearBalls() {
        balls.clear()
        activeBall = -1
        invalidate()
    }

    fun toggleGrid() {
        showGrid = !showGrid
        invalidate()
    }

    fun toggleCalibrate() {
        calibrateMode = !calibrateMode
        ballPlaceMode = false
        invalidate()
    }

    fun toggleBallPlace() {
        ballPlaceMode = !ballPlaceMode
        calibrateMode = false
        invalidate()
    }

    fun addBallMarker(isCue: Boolean) {
        val x = table.centerX + if (isCue) -60f else 60f
        val y = table.centerY
        balls.add(BallMarker(x, y, isCue, 28f))
        invalidate()
    }

    fun enableEditMode(enabled: Boolean) {
        isEditMode = enabled
        invalidate()
    }

    fun tableInfo(): String {
        val rel = if (table.isReliable()) "موثوق" else "غير موثوق"
        return "طاولة ${table.width.toInt()}×${table.height.toInt()} | $rel | كرات ${balls.size}"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // إطار الطاولة المعاير دائماً خفيف
        canvas.drawRect(table.left, table.top, table.right, table.bottom, borderPaint)

        if (calibrateMode) {
            drawCalibration(canvas)
        }
        if (showGrid) drawGrid(canvas)
        drawBalls(canvas)

        for (line in lines) {
            val x1 = line[0]; val y1 = line[1]; val x2 = line[2]; val y2 = line[3]
            val isCollision = line[4] > 0.5f

            canvas.drawLine(x1, y1, x2, y2, cueGlow)
            canvas.drawLine(x1, y1, x2, y2, cuePaint)
            drawArrow(canvas, x1, y1, x2, y2, cuePaint.color)

            if (isCollision) drawCollisionPhysics(canvas, x1, y1, x2, y2)
            else drawBouncePath(canvas, x2, y2, x2 - x1, y2 - y1, bankPaint, 8)

            if (isEditMode && !calibrateMode) {
                canvas.drawCircle(x1, y1, handleR, handleFill)
                canvas.drawCircle(x1, y1, handleR, handleStroke)
                canvas.drawCircle(x2, y2, handleR, handleFill)
                canvas.drawCircle(x2, y2, handleR, handleStroke)
            }
        }

        // معلومات المعايرة
        canvas.drawText(tableInfo(), 24f, height - 36f, infoPaint)
        if (calibrateMode) {
            canvas.drawText("معايرة: اسحب الزوايا الأربع البرتقالية", 24f, 80f, infoPaint)
        } else if (ballPlaceMode) {
            canvas.drawText("كرات: اضغط لإضافة | اسحب للتحريك", 24f, 80f, infoPaint)
        }
    }

    private fun drawCalibration(canvas: Canvas) {
        for (i in 0 until 4) {
            val n = (i + 1) % 4
            canvas.drawLine(
                table.corners[i][0], table.corners[i][1],
                table.corners[n][0], table.corners[n][1],
                borderPaint
            )
        }
        for (i in 0 until 4) {
            val x = table.corners[i][0]
            val y = table.corners[i][1]
            canvas.drawCircle(x, y, 22f, cornerPaint)
            canvas.drawCircle(x, y, 22f, cornerRing)
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val pockets = table.pockets()
        for (i in pockets.indices) {
            val p = Paint(cuePaint)
            p.strokeWidth = 4f
            p.alpha = 150
            p.color = when (i % 3) {
                0 -> Color.parseColor("#FFEA00")
                1 -> Color.parseColor("#00E676")
                else -> Color.parseColor("#00B0FF")
            }
            canvas.drawLine(table.centerX, table.centerY, pockets[i][0], pockets[i][1], p)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 14f, pocketFill)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 14f, pocketRing)
        }
    }

    private fun drawBalls(canvas: Canvas) {
        for (b in balls) {
            val fill = if (b.isCue) cueBallPaint else objBallPaint
            canvas.drawCircle(b.x, b.y, b.radius, fill)
            canvas.drawCircle(b.x, b.y, b.radius, ballRing)
        }
    }

    private fun drawCollisionPhysics(canvas: Canvas, cx: Float, cy: Float, ox: Float, oy: Float) {
        val dx = ox - cx
        val dy = oy - cy
        val len = hypot(dx, dy)
        if (len < 1f) return
        val ux = dx / len
        val uy = dy / len
        val r = balls.firstOrNull { !it.isCue }?.radius ?: 28f

        canvas.drawCircle(ox, oy, r, ghostFill)
        canvas.drawCircle(ox, oy, r, ghostStroke)

        drawBouncePath(canvas, ox, oy, ux, uy, objectPaint, 10, objectGlow)

        val px = -uy
        val py = ux
        val side = if (ux * py - uy * px >= 0f) 1f else -1f
        var dxCue = ux * 0.25f + px * side * 0.85f
        var dyCue = uy * 0.25f + py * side * 0.85f
        val clen = hypot(dxCue, dyCue)
        if (clen > 1e-4f) {
            dxCue /= clen
            dyCue /= clen
        }
        val leaveX = ox - ux * (r * 0.3f)
        val leaveY = oy - uy * (r * 0.3f)
        drawBouncePath(canvas, leaveX, leaveY, dxCue, dyCue, deflectPaint, 8, deflectGlow)
    }

    private fun drawBouncePath(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        dirX0: Float,
        dirY0: Float,
        paint: Paint,
        maxBounces: Int,
        glow: Paint? = null
    ) {
        var dirX = dirX0
        var dirY = dirY0
        var len = hypot(dirX, dirY)
        if (len < 1e-4f) return
        dirX /= len
        dirY /= len
        var cx = startX
        var cy = startY

        for (b in 0..maxBounces) {
            val hit = table.findHit(cx, cy, dirX, dirY)
            if (hit == null) {
                val ex = cx + dirX * 2200f
                val ey = cy + dirY * 2200f
                if (glow != null) canvas.drawLine(cx, cy, ex, ey, glow)
                canvas.drawLine(cx, cy, ex, ey, paint)
                drawArrow(canvas, cx, cy, ex, ey, paint.color)
                break
            }
            if (glow != null) canvas.drawLine(cx, cy, hit[0], hit[1], glow)
            canvas.drawLine(cx, cy, hit[0], hit[1], paint)
            hitPaint.color = paint.color
            canvas.drawCircle(hit[0], hit[1], 9f, hitPaint)

            val nx = hit[2]
            val ny = hit[3]
            val dot = dirX * nx + dirY * ny
            dirX -= 2f * dot * nx
            dirY -= 2f * dot * ny
            len = hypot(dirX, dirY)
            if (len > 1e-4f) {
                dirX /= len
                dirY /= len
            }
            cx = hit[0] + dirX * 2.5f
            cy = hit[1] + dirY * 2.5f
        }
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        arrowPaint.color = color
        val angle = atan2(y2 - y1, x2 - x1)
        val size = 26f
        val path = Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - size * cos(angle - 0.5f), y2 - size * sin(angle - 0.5f))
        path.lineTo(x2 - size * cos(angle + 0.5f), y2 - size * sin(angle + 0.5f))
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (calibrateMode) {
                    for (i in 0 until 4) {
                        if (hypot(event.x - table.corners[i][0], event.y - table.corners[i][1]) < 48f) {
                            activeCorner = i
                            return true
                        }
                    }
                    return true
                }

                // كرات
                for (i in balls.indices.reversed()) {
                    val b = balls[i]
                    if (hypot(event.x - b.x, event.y - b.y) < b.radius + 24f) {
                        activeBall = i
                        return true
                    }
                }

                if (ballPlaceMode) {
                    // ضغطة = إضافة كرة هدف (أو بيضاء إذا مافي)
                    val hasCue = balls.any { it.isCue }
                    balls.add(BallMarker(event.x, event.y, !hasCue, 28f))
                    invalidate()
                    return true
                }

                for (i in lines.indices) {
                    val line = lines[i]
                    if (hypot(event.x - line[0], event.y - line[1]) < handleR * 2.1f) {
                        activeLine = i; dragStart = true; return true
                    }
                    if (hypot(event.x - line[2], event.y - line[3]) < handleR * 2.1f) {
                        activeLine = i; dragStart = false; return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (calibrateMode && activeCorner in 0..3) {
                    table.corners[activeCorner][0] = event.x
                    table.corners[activeCorner][1] = event.y
                    invalidate()
                    return true
                }
                if (activeBall in balls.indices) {
                    balls[activeBall].x = event.x
                    balls[activeBall].y = event.y
                    // تحديث خطوط التصادم المربوطة تقريباً
                    invalidate()
                    return true
                }
                if (activeLine in lines.indices) {
                    val line = lines[activeLine]
                    if (dragStart) {
                        line[0] = event.x; line[1] = event.y
                    } else {
                        line[2] = event.x; line[3] = event.y
                    }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCorner = -1
                activeBall = -1
                activeLine = -1
            }
        }
        return activeCorner >= 0 || activeBall >= 0 || activeLine >= 0 || calibrateMode || ballPlaceMode
    }
}
