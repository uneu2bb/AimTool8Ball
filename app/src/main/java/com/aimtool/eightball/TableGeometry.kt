package com.aimtool.eightball

import kotlin.math.hypot

/**
 * هندسة الطاولة من 4 زوايا قابلة للمعايرة (مو أبعاد ثابتة وهمية).
 * الزوايا: 0=أعلى يسار, 1=أعلى يمين, 2=أسفل يمين, 3=أسفل يسار
 */
data class TableGeometry(
    val corners: Array<FloatArray> = Array(4) { FloatArray(2) }
) {
    val left: Float get() = minOf(corners[0][0], corners[3][0])
    val right: Float get() = maxOf(corners[1][0], corners[2][0])
    val top: Float get() = minOf(corners[0][1], corners[1][1])
    val bottom: Float get() = maxOf(corners[2][1], corners[3][1])
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val aspect: Float get() = if (height > 1f) width / height else 2f

    fun pockets(): Array<FloatArray> = arrayOf(
        floatArrayOf(left, top),
        floatArrayOf(centerX, top),
        floatArrayOf(right, top),
        floatArrayOf(left, bottom),
        floatArrayOf(centerX, bottom),
        floatArrayOf(right, bottom)
    )

    /** موثوقية بسيطة: شكل منطقي لطاولة */
    fun isReliable(): Boolean {
        if (width < 80f || height < 40f) return false
        val a = aspect
        return a in 1.2f..3.5f
    }

    fun initFromScreen(w: Float, h: Float) {
        corners[0][0] = w * 0.118f; corners[0][1] = h * 0.188f
        corners[1][0] = w * 0.882f; corners[1][1] = h * 0.188f
        corners[2][0] = w * 0.882f; corners[2][1] = h * 0.812f
        corners[3][0] = w * 0.118f; corners[3][1] = h * 0.812f
    }

    /**
     * أقرب اصطدام مع حدود المستطيل المعاير.
     * يرجع [hitX, hitY, nx, ny] أو null
     */
    fun findHit(cx: Float, cy: Float, dirX: Float, dirY: Float): FloatArray? {
        val L = left
        val R = right
        val T = top
        val B = bottom
        var tHit = Float.MAX_VALUE
        var hx = 0f
        var hy = 0f
        var nx = 0f
        var ny = 0f

        if (dirX < -1e-5f) {
            val t = (L - cx) / dirX
            if (t > 0.8f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= T - 2 && yy <= B + 2) {
                    tHit = t; hx = L; hy = yy; nx = 1f; ny = 0f
                }
            }
        }
        if (dirX > 1e-5f) {
            val t = (R - cx) / dirX
            if (t > 0.8f && t < tHit) {
                val yy = cy + dirY * t
                if (yy >= T - 2 && yy <= B + 2) {
                    tHit = t; hx = R; hy = yy; nx = -1f; ny = 0f
                }
            }
        }
        if (dirY < -1e-5f) {
            val t = (T - cy) / dirY
            if (t > 0.8f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= L - 2 && xx <= R + 2) {
                    tHit = t; hx = xx; hy = T; nx = 0f; ny = 1f
                }
            }
        }
        if (dirY > 1e-5f) {
            val t = (B - cy) / dirY
            if (t > 0.8f && t < tHit) {
                val xx = cx + dirX * t
                if (xx >= L - 2 && xx <= R + 2) {
                    tHit = t; hx = xx; hy = B; nx = 0f; ny = -1f
                }
            }
        }
        return if (tHit < Float.MAX_VALUE) floatArrayOf(hx, hy, nx, ny) else null
    }

    companion object {
        fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            hypot(x1 - x2, y1 - y2)
    }
}

/** علامة كرة يدوية للدقة */
data class BallMarker(
    var x: Float,
    var y: Float,
    var isCue: Boolean = false,
    var radius: Float = 28f
)
