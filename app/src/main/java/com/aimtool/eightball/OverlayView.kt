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
 * يدوي احترافي:
 * - خط تصويب (أصفر)
 * - وضع تصادم: مسار الكرة الهدف (أخضر) + انحراف البيضة (أزرق)
 * - ارتداد على البنود
 *
 * بيانات الخط: [x1,y1,x2,y2, mode]
 * mode: 0 = aim فقط, 1 = تصادم (collision)
 */
class OverlayView(context: Context) : View(context) {

    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val cueGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        strokeWidth = 16f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 50
    }
    private val objectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 6.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val objectGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        strokeWidth = 14f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 50
    }
    private val deflectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val deflectGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00B0FF")
        strokeWidth = 13f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        alpha = 50
    }
    private val bankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }
    private val ghostFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.FILL
    }
    private val ghostStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val hitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pocketFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDFFFFFF")
        style = Paint.Style.FILL
    }
    private val pocketRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEA00")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAFFEA00")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    // كل خط: x1,y1,x2,y2,mode
    private val lines = ArrayList<FloatArray>()
    private var isEditMode = true
    private var showGrid = false

    private var activeIndex = -1
    private var dragStart = true
    private val handleR = 40f
    private val ballR = 28f // نصف قطر تقريبي للكرة على الشاشة

    private var tableL = 0f
    private var tableT = 0f
    private var tableR = 0f
    private var tableB = 0f
    private var tableCX = 0f
    private var tableCY = 0f
    private val pockets = Array(6) { FloatArray(2) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tableL = w * 0.118f
        tableR = w * 0.882f
        tableT = h * 0.188f
        tableB = h * 0.812f
        tableCX = (tableL + tableR) / 2f
        tableCY = (tableT + tableB) / 2f
        pockets[0][0] = tableL; pockets[0][1] = tableT
        pockets[1][0] = tableCX; pockets[1][1] = tableT
        pockets[2][0] = tableR; pockets[2][1] = tableT
        pockets[3][0] = tableL; pockets[3][1] = tableB
        pockets[4][0] = tableCX; pockets[4][1] = tableB
        pockets[5][0] = tableR; pockets[5][1] = tableB
    }

    fun addAimLine() {
        lines.add(floatArrayOf(tableCX - 200f, tableCY + 80f, tableCX + 160f, tableCY - 40f, 0f))
        invalidate()
    }

    fun addCollisionShot() {
        lines.add(floatArrayOf(tableCX - 180f, tableCY + 100f, tableCX + 120f, tableCY - 20f, 1f))
        invalidate()
    }

    fun clearAll() {
        lines.clear()
        activeIndex = -1
        showGrid = false
        invalidate()
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

        if (showGrid) drawGrid(canvas)

        for (line in lines) {
            val x1 = line[0]
            val y1 = line[1]
            val x2 = line[2]
            val y2 = line[3]
            val isCollision = line[4] > 0.5f

            // خط التصويب (من البيضة لنقطة التصادم)
            canvas.drawLine(x1, y1, x2, y2, cueGlow)
            canvas.drawLine(x1, y1, x2, y2, cuePaint)
            drawArrow(canvas, x1, y1, x2, y2, cuePaint.color)

            if (isCollision) {
                drawCollisionPhysics(canvas, x1, y1, x2, y2)
            } else {
                // aim عادي: امتداد + ارتداد من الاتجاه
                drawBouncePath(canvas, x2, y2, x2 - x1, y2 - y1, bankPaint, 8)
            }

            if (isEditMode) {
                canvas.drawCircle(x1, y1, handleR, handleFill)
                canvas.drawCircle(x1, y1, handleR, handleStroke)
                canvas.drawCircle(x2, y2, handleR, handleFill)
                canvas.drawCircle(x2, y2, handleR, handleStroke)
            }
        }
    }

    /**
     * فيزياء يدوية مبسطة لنقطة التصادم:
     * - x1,y1 = مركز البيضة تقريباً
     * - x2,y2 = مركز الكرة الهدف عند التصادم (ghost ball)
     *
     * مسار الكرة الهدف = امتداد خط مراكز الكرات بعد التصادم
     * انحراف البيضة = عمودي تقريباً على خط التصادم (قطع)
     */
    private fun drawCollisionPhysics(canvas: Canvas, cx: Float, cy: Float, ox: Float, oy: Float) {
        val dx = ox - cx
        val dy = oy - cy
        val len = hypot(dx, dy)
        if (len < 1f) return

        val ux = dx / len
        val uy = dy / len

        // دائرة الشبح عند نقطة التصادم
        canvas.drawCircle(ox, oy, ballR, ghostFill)
        canvas.drawCircle(ox, oy, ballR, ghostStroke)

        // مسار الكرة الهدف (أخضر) — باتجاه خط المراكز + ارتداد
        drawBouncePath(canvas, ox, oy, ux, uy, objectPaint, 10, objectGlow)

        // انحراف البيضة (أزرق):
        // في التصادم المثالي ثنائي الأبعاد، مركبة السرعة على خط المراكز تنتقل للهدف
        // والبيضة تحتفظ بالمركبة العمودية → اتجاه العمودي على خط التصادم
        // لعرض أوضح: نرسم مسار البيضة بعد التصادم بزاوية قطع
        // dir_cue_after ≈ عمودي على (ux,uy) مع اختيار الجهة الأقرب لاستمرار الحركة
        val px = -uy
        val py = ux

        // جهة الانحراف: نختار التي تميل مع اتجاه الضربة الأصلي
        val side = if (ux * py - uy * px >= 0f) 1f else -1f
        // مزج بسيط: جزء من الاتجاه الأصلي + جزء عمودي (محاكاة قطع)
        var dxCue = ux * 0.25f + px * side * 0.85f
        var dyCue = uy * 0.25f + py * side * 0.85f
        val clen = hypot(dxCue, dyCue)
        if (clen > 1e-4f) {
            dxCue /= clen
            dyCue /= clen
        }

        // نقطة خروج البيضة بعد التصادم (شوي برا الشبح)
        val leaveX = ox - ux * (ballR * 0.3f)
        val leaveY = oy - uy * (ballR * 0.3f)
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
            val hit = findHit(cx, cy, dirX, dirY)
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

    private fun drawGrid(canvas: Canvas) {
        for (i in pockets.indices) {
            val p = Paint(cuePaint)
            p.strokeWidth = 4f
            p.alpha = 140
            p.color = when (i % 3) {
                0 -> Color.parseColor("#FFEA00")
                1 -> Color.parseColor("#00E676")
                else -> Color.parseColor("#00B0FF")
            }
            canvas.drawLine(tableCX, tableCY, pockets[i][0], pockets[i][1], p)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 14f, pocketFill)
            canvas.drawCircle(pockets[i][0], pockets[i][1], 14f, pocketRing)
        }
        canvas.drawRect(tableL, tableT, tableR, tableB, borderPaint)
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> activeIndex = -1
        }
        return activeIndex >= 0
    }
}
