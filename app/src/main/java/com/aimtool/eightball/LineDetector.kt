package com.aimtool.eightball

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

object LineDetector {

    data class DetectedLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    fun detectAimLine(bitmap: Bitmap): DetectedLine? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return null

        val scale = 3
        val sw = w / scale
        val sh = h / scale
        val pts = ArrayList<IntArray>(4000)

        // منطقة الطاولة تقريباً (تجنب شريط اللعبة العلوي)
        val y0 = sh * 20 / 100
        val y1 = sh * 80 / 100
        val x0 = sw * 12 / 100
        val x1 = sw * 88 / 100

        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val c = bitmap.getPixel(x * scale, y * scale)
                if (isAimPixel(c)) pts.add(intArrayOf(x * scale, y * scale))
                x += 2
            }
            y += 2
        }

        if (pts.size < 25) return null

        var maxD = 0f
        var p1 = pts[0]
        var p2 = pts[0]
        val step = maxOf(1, pts.size / 120)
        var i = 0
        while (i < pts.size) {
            var j = i + step
            while (j < pts.size) {
                val a = pts[i]
                val b = pts[j]
                val dx = (a[0] - b[0]).toFloat()
                val dy = (a[1] - b[1]).toFloat()
                val d = dx * dx + dy * dy
                if (d > maxD) {
                    maxD = d
                    p1 = a
                    p2 = b
                }
                j += step
            }
            i += step
        }

        if (maxD < 60f * 60f) return null
        return DetectedLine(p1[0].toFloat(), p1[1].toFloat(), p2[0].toFloat(), p2[1].toFloat())
    }

    private fun isAimPixel(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        // خط أبيض / رمادي فاتح جداً
        val white = r > 210 && g > 210 && b > 200 && abs(r - g) < 35 && abs(g - b) < 40
        // خط أصفر باهت أحياناً
        val softYellow = r > 220 && g > 200 && b < 150 && b > 70 && (r - b) > 70
        return white || softYellow
    }

    fun extendLine(line: DetectedLine, factor: Float = 4f): DetectedLine {
        val dx = line.x2 - line.x1
        val dy = line.y2 - line.y1
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return line
        val ux = dx / len
        val uy = dy / len
        val e = len * factor
        return DetectedLine(
            line.x1 - ux * e,
            line.y1 - uy * e,
            line.x2 + ux * e,
            line.y2 + uy * e
        )
    }
}
