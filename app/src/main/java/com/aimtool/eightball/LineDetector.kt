package com.aimtool.eightball

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * كشف بسيط للخط الأصفر الموجود بلعبة 8 Ball Pool
 * يعتمد على لون البكسلات الصفراء وإيجاد أفضل خط مستقيم
 */
object LineDetector {

    data class DetectedLine(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    fun detectYellowLine(bitmap: Bitmap): DetectedLine? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 50 || h < 50) return null

        // تصغير للسرعة
        val scale = 4
        val sw = w / scale
        val sh = h / scale

        val points = ArrayList<IntArray>(2000)

        // مسح الشبكة بحثاً عن أصفر
        var y = sh / 5
        while (y < sh * 4 / 5) {
            var x = sw / 8
            while (x < sw * 7 / 8) {
                val px = bitmap.getPixel(x * scale, y * scale)
                if (isYellow(px)) {
                    points.add(intArrayOf(x * scale, y * scale))
                }
                x += 2
            }
            y += 2
        }

        if (points.size < 15) return null

        // إيجاد أبعد نقطتين (تقريب لاتجاه الخط)
        var maxDist = 0f
        var p1 = points[0]
        var p2 = points[0]
        val step = maxOf(1, points.size / 80)
        var i = 0
        while (i < points.size) {
            var j = i + step
            while (j < points.size) {
                val a = points[i]
                val b = points[j]
                val dx = (a[0] - b[0]).toFloat()
                val dy = (a[1] - b[1]).toFloat()
                val d = dx * dx + dy * dy
                if (d > maxDist) {
                    maxDist = d
                    p1 = a
                    p2 = b
                }
                j += step
            }
            i += step
        }

        if (maxDist < 40f * 40f) return null

        return DetectedLine(
            p1[0].toFloat(),
            p1[1].toFloat(),
            p2[0].toFloat(),
            p2[1].toFloat()
        )
    }

    private fun isYellow(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        // أصفر/ذهبي تقريباً
        return r > 180 && g > 150 && b < 120 && abs(r - g) < 80 && r > b + 60
    }

    /** تمديد الخط خارج المقطع المكتشف */
    fun extendLine(line: DetectedLine, screenW: Int, screenH: Int, factor: Float = 2.5f): DetectedLine {
        val dx = line.x2 - line.x1
        val dy = line.y2 - line.y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return line

        val ux = dx / len
        val uy = dy / len
        val extend = len * factor

        return DetectedLine(
            line.x1 - ux * extend,
            line.y1 - uy * extend,
            line.x2 + ux * extend,
            line.y2 + uy * extend
        )
    }
}
