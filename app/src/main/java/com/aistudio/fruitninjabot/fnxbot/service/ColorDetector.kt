package com.aistudio.fruitninjabot.fnxbot.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import com.aistudio.fruitninjabot.fnxbot.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class ColorProfile(
    val name: String,
    val minHue: Float,
    val maxHue: Float,
    val minSat: Float,
    val maxSat: Float = 1.0f,
    val minVal: Float = 0.35f,
    val maxVal: Float = 1.0f
)

data class SliceTrajectory(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long = 50L,
    val targetType: String = "Fruit"
)

data class DetectionResult(
    val fruits: List<FruitTarget>,
    val bombs: List<FruitTarget>,
    val menuActions: List<PointF>,
    val adSkips: List<PointF>,
    val trajectories: List<SliceTrajectory>
)

object ColorDetector {

    private val fruitProfiles = listOf(
        // Watermelon / Strawberry / Apple Red (wraps around 0/360)
        ColorProfile("Red_Low", 0f, 15f, 0.45f, 1.0f, 0.38f, 1.0f),
        ColorProfile("Red_High", 345f, 360f, 0.45f, 1.0f, 0.38f, 1.0f),
        // Orange (strict saturation to filter wood dojo)
        ColorProfile("Orange", 18f, 42f, 0.55f, 1.0f, 0.42f, 1.0f),
        // Banana / Lemon Yellow
        ColorProfile("Yellow", 45f, 68f, 0.42f, 1.0f, 0.45f, 1.0f),
        // Kiwi / Lime / Green Apple
        ColorProfile("Green", 72f, 160f, 0.38f, 1.0f, 0.35f, 1.0f),
        // Dragonfruit / Passionfruit Magenta
        ColorProfile("Magenta", 280f, 344f, 0.38f, 1.0f, 0.38f, 1.0f)
    )

    /**
     * Analyzes a downscaled screen bitmap using fast HSV spatial grid sampling.
     */
    fun analyzeFrame(
        bitmap: Bitmap,
        config: BotConfig,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return DetectionResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        val scaleX = screenWidth.toFloat() / width.toFloat()
        val scaleY = screenHeight.toFloat() / height.toFloat()

        val step = max(4, width / 90) // fast grid step
        val topLimit = (height * config.detectionRegionTop).toInt()
        val bottomLimit = (height * config.detectionRegionBottom).toInt()

        val hsv = FloatArray(3)
        val rawFruitPoints = mutableListOf<Triple<Float, Float, String>>()
        val rawBombPoints = mutableListOf<Pair<Float, Float>>()
        val rawAdPoints = mutableListOf<Pair<Float, Float>>()
        val rawMenuPoints = mutableListOf<Pair<Float, Float>>()

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (y in topLimit until bottomLimit step step) {
            val rowOffset = y * width
            for (x in step until (width - step) step step) {
                val pixel = pixels[rowOffset + x]
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 100) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // Check for Bomb / Obstacle (dark metallic spheres)
                if (value < 0.15f || (r < 35 && g < 35 && b < 35)) {
                    rawBombPoints.add(x.toFloat() to y.toFloat())
                    continue
                }

                // Check for Ad Close "X" button in top corners (white/high brightness button in corners)
                if (config.autoSkipAds && y < height * 0.18f && (x < width * 0.22f || x > width * 0.78f)) {
                    if (value > 0.90f && sat < 0.12f) {
                        rawAdPoints.add(x.toFloat() to y.toFloat())
                        continue
                    }
                }

                // Check for Lobby Play Fruit in center-lower zone
                if (config.autoStartGame && y > height * 0.55f && y < height * 0.85f && x > width * 0.28f && x < width * 0.72f) {
                    if ((hue in 75f..150f || hue in 345f..360f || hue in 0f..15f) && sat > 0.50f && value > 0.45f) {
                        rawMenuPoints.add(x.toFloat() to y.toFloat())
                    }
                }

                // Check for Fruit Profiles
                for (profile in fruitProfiles) {
                    if (profile.name.startsWith("Red") && !config.activeFruitColors.contains("Red")) continue
                    if (profile.name == "Orange" && !config.activeFruitColors.contains("Orange")) continue
                    if (profile.name == "Yellow" && !config.activeFruitColors.contains("Yellow")) continue
                    if (profile.name == "Green" && !config.activeFruitColors.contains("Green")) continue
                    if (profile.name == "Magenta" && !config.activeFruitColors.contains("Magenta")) continue

                    if (hue in profile.minHue..profile.maxHue &&
                        sat in profile.minSat..profile.maxSat &&
                        value in profile.minVal..profile.maxVal
                    ) {
                        rawFruitPoints.add(Triple(x.toFloat(), y.toFloat(), profile.name))
                        break
                    }
                }
            }
        }

        // Cluster raw points into distinct fruit targets
        val clusteredFruits = clusterPoints(rawFruitPoints, clusterRadius = step * 3.5f)
            .map { (x, y, colorType) ->
                FruitTarget(
                    x = x * scaleX,
                    y = y * scaleY,
                    radius = 55f,
                    colorType = colorType,
                    isBomb = false
                )
            }

        // Cluster bombs
        val clusteredBombs = clusterSimplePoints(rawBombPoints, clusterRadius = step * 3.0f)
            .map { (x, y) ->
                FruitTarget(
                    x = x * scaleX,
                    y = y * scaleY,
                    radius = 70f,
                    colorType = "Bomb",
                    isBomb = true
                )
            }

        // Ad Skip points
        val adSkips = clusterSimplePoints(rawAdPoints, clusterRadius = step * 4f)
            .map { PointF(it.first * scaleX, it.second * scaleY) }

        // Menu Start points
        val menuActions = clusterSimplePoints(rawMenuPoints, clusterRadius = step * 4f)
            .map { PointF(it.first * scaleX, it.second * scaleY) }

        // Calculate safe slice trajectories
        val trajectories = calculateTrajectories(
            fruits = clusteredFruits,
            bombs = clusteredBombs,
            config = config,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        return DetectionResult(
            fruits = clusteredFruits,
            bombs = clusteredBombs,
            menuActions = menuActions,
            adSkips = adSkips,
            trajectories = trajectories
        )
    }

    private fun clusterPoints(
        points: List<Triple<Float, Float, String>>,
        clusterRadius: Float
    ): List<Triple<Float, Float, String>> {
        val clusters = mutableListOf<Triple<Float, Float, String>>()
        val visited = BooleanArray(points.size)

        for (i in points.indices) {
            if (visited[i]) continue
            var sumX = points[i].first
            var sumY = points[i].second
            var count = 1
            val colorType = points[i].third
            visited[i] = true

            for (j in i + 1 until points.size) {
                if (visited[j]) continue
                val dist = hypot(points[i].first - points[j].first, points[i].second - points[j].second)
                if (dist < clusterRadius) {
                    sumX += points[j].first
                    sumY += points[j].second
                    count++
                    visited[j] = true
                }
            }

            if (count >= 3) { // Filter out isolated noise pixels
                clusters.add(Triple(sumX / count, sumY / count, colorType))
            }
        }
        return clusters
    }

    private fun clusterSimplePoints(
        points: List<Pair<Float, Float>>,
        clusterRadius: Float
    ): List<Pair<Float, Float>> {
        val clusters = mutableListOf<Pair<Float, Float>>()
        val visited = BooleanArray(points.size)

        for (i in points.indices) {
            if (visited[i]) continue
            var sumX = points[i].first
            var sumY = points[i].second
            var count = 1
            visited[i] = true

            for (j in i + 1 until points.size) {
                if (visited[j]) continue
                val dist = hypot(points[i].first - points[j].first, points[i].second - points[j].second)
                if (dist < clusterRadius) {
                    sumX += points[j].first
                    sumY += points[j].second
                    count++
                    visited[j] = true
                }
            }

            if (count >= 4) {
                clusters.add(sumX / count to sumY / count)
            }
        }
        return clusters
    }

    private fun calculateTrajectories(
        fruits: List<FruitTarget>,
        bombs: List<FruitTarget>,
        config: BotConfig,
        screenWidth: Int,
        screenHeight: Int
    ): List<SliceTrajectory> {
        val trajectories = mutableListOf<SliceTrajectory>()
        val safeBombRadius = 140f

        // Filter fruits that are too dangerously close to bombs
        val safeFruits = fruits.filter { fruit ->
            if (!config.bombAvoidance) return@filter true
            bombs.none { bomb -> hypot(fruit.x - bomb.x, fruit.y - bomb.y) < safeBombRadius }
        }

        if (safeFruits.isEmpty()) return emptyList()

        when (config.sliceMode) {
            SliceMode.INSTANT_SLASH -> {
                for (fruit in safeFruits) {
                    val halfLen = config.minSliceLength / 2f
                    // 45 degree upward cut
                    val startX = (fruit.x - halfLen * 0.7f).coerceIn(40f, screenWidth - 40f)
                    val startY = (fruit.y + halfLen * 0.7f).coerceIn(40f, screenHeight - 40f)
                    val endX = (fruit.x + halfLen * 0.7f).coerceIn(40f, screenWidth - 40f)
                    val endY = (fruit.y - halfLen * 0.7f).coerceIn(40f, screenHeight - 40f)

                    trajectories.add(SliceTrajectory(startX, startY, endX, endY, config.sliceDurationMs))
                }
            }

            SliceMode.COMBO_SLASH -> {
                // Connect nearby fruits in ascending chains
                val sortedFruits = safeFruits.sortedBy { it.x }
                var i = 0
                while (i < sortedFruits.size) {
                    val current = sortedFruits[i]
                    if (i + 1 < sortedFruits.size) {
                        val next = sortedFruits[i + 1]
                        val dist = hypot(current.x - next.x, current.y - next.y)
                        if (dist < 480f) {
                            // Check if path intersects any bomb
                            val isSafeCombo = bombs.none { b ->
                                isPointNearSegment(b.x, b.y, current.x, current.y, next.x, next.y, safeBombRadius)
                            }
                            if (isSafeCombo) {
                                val dx = next.x - current.x
                                val dy = next.y - current.y
                                val angle = atan2(dy, dx)
                                val extend = 70f
                                val startX = (current.x - cos(angle) * extend).coerceIn(40f, screenWidth - 40f)
                                val startY = (current.y - sin(angle) * extend).coerceIn(40f, screenHeight - 40f)
                                val endX = (next.x + cos(angle) * extend).coerceIn(40f, screenWidth - 40f)
                                val endY = (next.y + sin(angle) * extend).coerceIn(40f, screenHeight - 40f)

                                trajectories.add(
                                    SliceTrajectory(
                                        startX, startY, endX, endY,
                                        durationMs = max(45L, config.sliceDurationMs + 20L),
                                        targetType = "Combo (${current.colorType}+${next.colorType})"
                                    )
                                )
                                i += 2
                                continue
                            }
                        }
                    }

                    // Single slash fallback
                    val halfLen = config.minSliceLength / 2f
                    trajectories.add(
                        SliceTrajectory(
                            (current.x - halfLen).coerceIn(40f, screenWidth - 40f),
                            (current.y + halfLen * 0.5f).coerceIn(40f, screenHeight - 40f),
                            (current.x + halfLen).coerceIn(40f, screenWidth - 40f),
                            (current.y - halfLen * 0.5f).coerceIn(40f, screenHeight - 40f),
                            durationMs = config.sliceDurationMs
                        )
                    )
                    i++
                }
            }

            SliceMode.MULTI_SWEEP -> {
                // Cross sweeping slashes
                for (fruit in safeFruits) {
                    val halfLen = config.maxSliceLength / 2f
                    trajectories.add(
                        SliceTrajectory(
                            (fruit.x - halfLen).coerceIn(40f, screenWidth - 40f),
                            (fruit.y + halfLen * 0.6f).coerceIn(40f, screenHeight - 40f),
                            (fruit.x + halfLen).coerceIn(40f, screenWidth - 40f),
                            (fruit.y - halfLen * 0.6f).coerceIn(40f, screenHeight - 40f),
                            durationMs = config.sliceDurationMs
                        )
                    )
                }
            }

            SliceMode.BOMB_SAFE_SLASH -> {
                for (fruit in safeFruits) {
                    // Pick the perpendicular angle farthest from the nearest bomb
                    var bestAngle = -Math.PI.toFloat() / 4f
                    val nearestBomb = bombs.minByOrNull { hypot(fruit.x - it.x, fruit.y - it.y) }
                    if (nearestBomb != null) {
                        val angleToBomb = atan2(nearestBomb.y - fruit.y, nearestBomb.x - fruit.x)
                        bestAngle = angleToBomb + (Math.PI.toFloat() / 2f)
                    }

                    val halfLen = config.minSliceLength / 2f
                    val startX = (fruit.x - cos(bestAngle) * halfLen).coerceIn(40f, screenWidth - 40f)
                    val startY = (fruit.y - sin(bestAngle) * halfLen).coerceIn(40f, screenHeight - 40f)
                    val endX = (fruit.x + cos(bestAngle) * halfLen).coerceIn(40f, screenWidth - 40f)
                    val endY = (fruit.y + sin(bestAngle) * halfLen).coerceIn(40f, screenHeight - 40f)

                    trajectories.add(SliceTrajectory(startX, startY, endX, endY, config.sliceDurationMs))
                }
            }
        }

        return trajectories
    }

    private fun isPointNearSegment(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        threshold: Float
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1) < threshold

        val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)
        val projX = x1 + clampedT * dx
        val projY = y1 + clampedT * dy
        return hypot(px - projX, py - projY) < threshold
    }
}
