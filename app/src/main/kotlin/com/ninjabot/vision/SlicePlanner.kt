package com.ninjabot.vision

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class SlicePath(val startX: Int, val startY: Int, val endX: Int, val endY: Int)

/**
 * يحوّل قائمة الاكتشافات (فواكه + قنابل) إلى مسارات "سحب" (swipe) آمنة:
 * كل مسار يمر عبر فاكهة واحدة أو أكثر لكنه يتجنّب المرور بالقرب من أي قنبلة.
 *
 * الأمان من القنابل يُضمن بخطوتين:
 * 1) عند اختيار الفواكه المستهدفة، نستبعد أي فاكهة قريبة جدا من قنبلة (قد يؤدي
 *    التمرير القريب منها لقصّها بالخطأ بسبب سماكة خط اللمس).
 * 2) عند رسم الخط بين نقطتي البداية والنهاية، نتحقق أن أقرب مسافة بين الخط
 *    وكل قنبلة أكبر من "منطقة الأمان" (safetyMargin) المحسوبة من نصف قطر القنبلة.
 */
class SlicePlanner(
    private val safetyMargin: Int = 60 // بكسل إضافية حول القنبلة نتجنبها
) {

    fun plan(detections: List<Detection>): List<SlicePath> {
        val fruits = detections.filter { it.type == ObjectType.FRUIT }
        val bombs = detections.filter { it.type == ObjectType.BOMB }

        // استبعاد أي فاكهة قريبة جدا من قنبلة (تفاديا لخطر التماس أثناء السحب)
        val safeFruits = fruits.filterNot { fruit ->
            bombs.any { bomb ->
                distance(fruit.x, fruit.y, bomb.x, bomb.y) < (bomb.radius + safetyMargin)
            }
        }

        if (safeFruits.isEmpty()) return emptyList()

        // ترتيب الفواكه من اليسار لليمين لبناء خط تقطيع واحد ناعم قدر الإمكان
        val ordered = safeFruits.sortedBy { it.x }

        val paths = mutableListOf<SlicePath>()
        var chainStart = ordered.first()
        var chainEnd = ordered.first()

        for (i in 1 until ordered.size) {
            val candidateEnd = ordered[i]
            val candidatePath = SlicePath(chainStart.x, chainStart.y, candidateEnd.x, candidateEnd.y)

            if (isPathSafe(candidatePath, bombs)) {
                chainEnd = candidateEnd
            } else {
                // أغلق السلسلة الحالية وابدأ سلسلة جديدة بعد القنبلة
                if (chainStart != chainEnd) {
                    paths.add(SlicePath(chainStart.x, chainStart.y, chainEnd.x, chainEnd.y))
                } else {
                    // فاكهة وحيدة: اصنع مسار قصير عبرها فقط
                    paths.add(shortSwipeThrough(chainStart))
                }
                chainStart = candidateEnd
                chainEnd = candidateEnd
            }
        }

        // أضف آخر سلسلة متبقية
        if (chainStart == chainEnd) {
            paths.add(shortSwipeThrough(chainStart))
        } else {
            paths.add(SlicePath(chainStart.x, chainStart.y, chainEnd.x, chainEnd.y))
        }

        // فلترة أخيرة: تأكد أن كل مسار نهائي آمن فعليا (حماية إضافية)
        return paths.filter { isPathSafe(it, bombs) }
    }

    private fun shortSwipeThrough(d: Detection): SlicePath {
        val half = max(d.radius, 20)
        return SlicePath(d.x - half, d.y, d.x + half, d.y)
    }

    private fun isPathSafe(path: SlicePath, bombs: List<Detection>): Boolean {
        for (bomb in bombs) {
            val dist = pointToSegmentDistance(
                bomb.x.toDouble(), bomb.y.toDouble(),
                path.startX.toDouble(), path.startY.toDouble(),
                path.endX.toDouble(), path.endY.toDouble()
            )
            if (dist < (bomb.radius + safetyMargin)) return false
        }
        return true
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double =
        hypot((x1 - x2).toDouble(), (y1 - y2).toDouble())

    /** أقرب مسافة بين نقطة وقطعة مستقيمة (لحساب اقتراب المسار من القنبلة) */
    private fun pointToSegmentDistance(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0.0) return hypot(px - ax, py - ay)

        var t = ((px - ax) * dx + (py - ay) * dy) / lengthSq
        t = max(0.0, min(1.0, t))

        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return hypot(px - closestX, py - closestY)
    }
}
