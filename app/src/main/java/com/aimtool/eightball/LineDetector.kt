package com.aimtool.eightball

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * كشف احترافي لخط التوجيه + معايرة حدود الطاولة من لون اللباد الأزرق
 */
object LineDetector {

    data class DetectedLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    data class TableBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun detectAimLine(bitmap: Bitmap): DetectedLine? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 120 || h < 120) return null

        val scale = 3
        val sw = w / scale
        val sh = h / scale

        // حدود بحث داخل الطاولة (نسب أولية)
        val x0 = (sw * 0.10f).toInt()
        val x1 = (sw * 0.90f).toInt()
        val y0 = (sh * 0.18f).toInt()
        val y1 = (sh * 0.82f).toInt()

        val pts = ArrayList<Pair<Int, Int>>(5000)
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val px = min(x * scale, w - 1)
                val py = min(y * scale, h - 1)
                if (isAimPixel(bitmap.getPixel(px, py))) {
                    pts.add(px to py)
                }
                x += 2
            }
            y += 2
        }

        if (pts.size < 30) return null

        // RANSAC لتثبيت اتجاه الخط ورفض الضوضاء (واجهة/كرات لامعة)
        val best = ransacLine(pts, iterations = 80, threshold = 6f)
            ?: return farthestPairLine(pts)

        return extendLine(best, 4.2f)
    }

    /** تقدير حدود الطاولة من لون اللباد الأزرق */
    fun detectTableBounds(bitmap: Bitmap): TableBounds? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 100 || h < 100) return null

        val scale = 4
        val sw = w / scale
        val sh = h / scale
        var minX = sw
        var maxX = 0
        var minY = sh
        var maxY = 0
        var count = 0

        for (y in (sh * 0.12f).toInt() until (sh * 0.88f).toInt()) {
            for (x in (sw * 0.05f).toInt() until (sw * 0.95f).toInt()) {
                val c = bitmap.getPixel(min(x * scale, w - 1), min(y * scale, h - 1))
                if (isFeltBlue(c)) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    count++
                }
            }
        }
        if (count < 200) return null

        // هامش صغير للداخل (بعيداً عن الإطار الخشبي)
        val padX = (maxX - minX) * 0.02f
        val padY = (maxY - minY) * 0.03f
        return TableBounds(
            left = (minX * scale + padX).coerceAtLeast(0f),
            top = (minY * scale + padY).coerceAtLeast(0f),
            right = (maxX * scale - padX).coerceAtMost(w.toFloat()),
            bottom = (maxY * scale - padY).coerceAtMost(h.toFloat())
        )
    }

    private fun isAimPixel(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        // أبيض نقي / رمادي فاتح جداً (خط التوجيه)
        val white = r > 215 && g > 215 && b > 205 && abs(r - g) < 28 && abs(g - b) < 32
        // أصفر فاتح أحياناً
        val softYellow = r > 225 && g > 205 && b in 60..150 && (r - b) > 80
        // استبعاد اللباد الأزرق والأخضر
        val notFelt = b < 190 || r > 160 || g > 180
        return (white || softYellow) && notFelt
    }

    private fun isFeltBlue(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        // أزرق/أزرق-فيروزي طاولة 8 Ball الشائع
        return b > 90 && b > r + 25 && b > g - 10 && r < 120 && g < 160 && g > 40
    }

    private fun ransacLine(
        pts: List<Pair<Int, Int>>,
        iterations: Int,
        threshold: Float
    ): DetectedLine? {
        if (pts.size < 20) return null
        var bestInliers = 0
        var bestA = pts[0]
        var bestB = pts[1]
        val rnd = Random(42)

        repeat(iterations) {
            val i1 = rnd.nextInt(pts.size)
            var i2 = rnd.nextInt(pts.size)
            if (i1 == i2) i2 = (i2 + 1) % pts.size
            val a = pts[i1]
            val b = pts[i2]
            val dx = (b.first - a.first).toFloat()
            val dy = (b.second - a.second).toFloat()
            val len = hypot(dx, dy)
            if (len < 40f) return@repeat

            var inliers = 0
            for (p in pts) {
                // مسافة نقطة عن خط
                val dist = abs(dy * (p.first - a.first) - dx * (p.second - a.second)) / len
                if (dist < threshold) inliers++
            }
            if (inliers > bestInliers) {
                bestInliers = inliers
                bestA = a
                bestB = b
            }
        }

        if (bestInliers < pts.size * 0.25f) return null

        // أبعد نقطتين ضمن الـ inliers التقريبيين
        val dx = (bestB.first - bestA.first).toFloat()
        val dy = (bestB.second - bestA.second).toFloat()
        val len = hypot(dx, dy)
        if (len < 1f) return null

        var minT = 0f
        var maxT = 0f
        var pMin = bestA
        var pMax = bestB
        for (p in pts) {
            val dist = abs(dy * (p.first - bestA.first) - dx * (p.second - bestA.second)) / len
            if (dist > threshold) continue
            val t = ((p.first - bestA.first) * dx + (p.second - bestA.second) * dy) / (len * len)
            if (t < minT) { minT = t; pMin = p }
            if (t > maxT) { maxT = t; pMax = p }
        }

        return DetectedLine(
            pMin.first.toFloat(), pMin.second.toFloat(),
            pMax.first.toFloat(), pMax.second.toFloat()
        )
    }

    private fun farthestPairLine(pts: List<Pair<Int, Int>>): DetectedLine? {
        var maxD = 0f
        var p1 = pts[0]
        var p2 = pts[0]
        val step = max(1, pts.size / 100)
        var i = 0
        while (i < pts.size) {
            var j = i + step
            while (j < pts.size) {
                val a = pts[i]
                val b = pts[j]
                val d = hypot((a.first - b.first).toFloat(), (a.second - b.second).toFloat())
                if (d > maxD) {
                    maxD = d
                    p1 = a
                    p2 = b
                }
                j += step
            }
            i += step
        }
        if (maxD < 50f) return null
        return DetectedLine(p1.first.toFloat(), p1.second.toFloat(), p2.first.toFloat(), p2.second.toFloat())
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
