package com.ninjabot.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * نتيجة الكشف: نقطة (مركز الجسم) + نصف قطره التقريبي + نوعه.
 */
data class Detection(
    val x: Int,
    val y: Int,
    val radius: Int,
    val type: ObjectType
)

enum class ObjectType { FRUIT, BOMB, UNKNOWN }

/**
 * يحلل إطار (Bitmap) من الشاشة ويستخرج مواقع الفواكه والقنابل.
 *
 * الفكرة: نأخذ عينة من البكسلات (لتسريع الحساب)، نصنّف كل بكسل بحسب لونه
 * (فاكهة = لون مشبّع وساطع، قنبلة = لون قاتم قريب من الأسود مع بريق معدني رمادي)،
 * ثم نجمع البكسلات المتقاربة في "كتل" (blobs) باستعمال خوارزمية بسيطة لتجميع
 * الشبكة (grid clustering) بدل flood-fill الكامل لأن الأداء مهم هنا (real-time).
 */
class FrameAnalyzer(
    private val sampleStep: Int = 6,      // نأخذ بكسل واحد كل sampleStep بكسل (لتسريع التحليل)
    private val minBlobPixels: Int = 12,  // أقل عدد بكسلات معتمدة عشان نعتبرها جسم حقيقي وليس ضجيج
    private val cellSize: Int = 24        // حجم خلية الشبكة المستعملة للتجميع
) {

    fun analyze(bitmap: Bitmap): List<Detection> {
        val width = bitmap.width
        val height = bitmap.height

        // نحدد خلفية اللعبة (غالبا داكنة/سوداء) عشان لا نصنّفها قنبلة خطأً.
        // نبني خريطة تصنيف لكل خلية في الشبكة: هل تحتوي فاكهة أو قنبلة أو لا شيء.
        val cols = (width / cellSize) + 1
        val rows = (height / cellSize) + 1
        val cellVotesFruit = IntArray(cols * rows)
        val cellVotesBomb = IntArray(cols * rows)
        val cellColorSum = IntArray(cols * rows) // نجمع قيمة اللون التقريبية (للتلوين لاحقا إن احتجنا)

        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                val classification = classifyPixel(pixel)
                if (classification != ObjectType.UNKNOWN) {
                    val cx = x / cellSize
                    val cy = y / cellSize
                    val idx = cy * cols + cx
                    if (idx in cellVotesFruit.indices) {
                        if (classification == ObjectType.FRUIT) cellVotesFruit[idx]++
                        else cellVotesBomb[idx]++
                    }
                }
                y += sampleStep
            }
            x += sampleStep
        }

        // نحول خلايا الشبكة المفعّلة إلى كتل متجاورة (تجميع بسيط بالجيرة الرباعية)
        val visited = BooleanArray(cols * rows)
        val detections = mutableListOf<Detection>()

        for (cy in 0 until rows) {
            for (cx in 0 until cols) {
                val idx = cy * cols + cx
                if (visited[idx]) continue
                val isFruitCell = cellVotesFruit[idx] > cellVotesBomb[idx] && cellVotesFruit[idx] > 0
                val isBombCell = cellVotesBomb[idx] >= cellVotesFruit[idx] && cellVotesBomb[idx] > 0
                if (!isFruitCell && !isBombCell) continue

                val targetType = if (isFruitCell) ObjectType.FRUIT else ObjectType.BOMB
                val blobCells = mutableListOf<Int>()
                val stack = ArrayDeque<Int>()
                stack.add(idx)
                visited[idx] = true

                var minCx = cx; var maxCx = cx
                var minCy = cy; var maxCy = cy
                var pixelWeight = 0

                while (stack.isNotEmpty()) {
                    val cur = stack.removeLast()
                    val curCx = cur % cols
                    val curCy = cur / cols
                    val curIsFruit = cellVotesFruit[cur] > cellVotesBomb[cur] && cellVotesFruit[cur] > 0
                    val curIsBomb = cellVotesBomb[cur] >= cellVotesFruit[cur] && cellVotesBomb[cur] > 0
                    if (curIsFruit != (targetType == ObjectType.FRUIT) &&
                        curIsBomb != (targetType == ObjectType.BOMB)
                    ) continue

                    blobCells.add(cur)
                    pixelWeight += max(cellVotesFruit[cur], cellVotesBomb[cur])
                    minCx = min(minCx, curCx); maxCx = max(maxCx, curCx)
                    minCy = min(minCy, curCy); maxCy = max(maxCy, curCy)

                    val neighbors = intArrayOf(
                        cur - 1, cur + 1, cur - cols, cur + cols
                    )
                    for (n in neighbors) {
                        if (n in 0 until (cols * rows) && !visited[n]) {
                            val nFruit = cellVotesFruit[n] > cellVotesBomb[n] && cellVotesFruit[n] > 0
                            val nBomb = cellVotesBomb[n] >= cellVotesFruit[n] && cellVotesBomb[n] > 0
                            val matches = (targetType == ObjectType.FRUIT && nFruit) ||
                                (targetType == ObjectType.BOMB && nBomb)
                            if (matches) {
                                visited[n] = true
                                stack.add(n)
                            }
                        }
                    }
                }

                if (pixelWeight >= minBlobPixels) {
                    val centerX = ((minCx + maxCx + 1) * cellSize) / 2
                    val centerY = ((minCy + maxCy + 1) * cellSize) / 2
                    val radius = max(maxCx - minCx + 1, maxCy - minCy + 1) * cellSize / 2
                    detections.add(Detection(centerX, centerY, max(radius, cellSize / 2), targetType))
                }
            }
        }

        return detections
    }

    /**
     * تصنيف بكسل واحد:
     * - القنابل في Fruit Ninja سوداء/رمادية داكنة مع بريق فولاذي (تدرج رمادي منخفض التشبع).
     * - الفواكه ألوان زاهية عالية التشبع (أحمر، برتقالي، أصفر، أخضر، أرجواني...).
     * - نتجاهل الخلفية شبه السوداء التي لا "تلمع" (فرق بسيط عن القنبلة عبر السطوع المتوسط).
     */
    private fun classifyPixel(pixel: Int): ObjectType {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val brightness = maxC / 255f
        val saturation = if (maxC == 0) 0f else (maxC - minC) / maxC.toFloat()

        // القنبلة: رمادي/أسود معدني - سطوع متوسط إلى منخفض، تشبع منخفض جدا
        val looksLikeBomb = saturation < 0.15f && brightness in 0.12f..0.55f

        // الفاكهة: لون مشبّع بشكل واضح وساطع بما يكفي ليكون جسم اللعبة لا الخلفية
        val looksLikeFruit = saturation > 0.45f && brightness > 0.35f

        return when {
            looksLikeFruit -> ObjectType.FRUIT
            looksLikeBomb -> ObjectType.BOMB
            else -> ObjectType.UNKNOWN
        }
    }
}
