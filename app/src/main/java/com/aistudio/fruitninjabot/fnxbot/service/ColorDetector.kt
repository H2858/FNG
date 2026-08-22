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
    val minVal: Float = 0.30f,
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

    // High tolerance Fruit HSV color profiles for Fruit Ninja
    private val fruitProfiles = listOf(
        // Watermelon / Strawberry / Apple Red (wraps around 0 / 360)
        ColorProfile("Red_Low", 0f, 16f, 0.42f, 1.0f, 0.32f, 1.0f),
        ColorProfile("Red_High", 342f, 360f, 0.42f, 1.0f, 0.32f, 1.0f),
        // Orange (strict saturation to filter out warm wooden background)
        ColorProfile("Orange", 18f, 44f, 0.58f, 1.0f, 0.40f, 1.0f),
        // Banana / Lemon Yellow
        ColorProfile("Yellow", 45f, 70f, 0.38f, 1.0f, 0.38f, 1.0f),
        // Kiwi / Lime / Green Apple / Coconut Shell Green
        ColorProfile("Green", 72f, 165f, 0.32f, 1.0f, 0.30f, 1.0f),
        // Dragonfruit / Passionfruit / Plum Magenta & Purple
        ColorProfile("Magenta", 270f, 340f, 0.35f, 1.0f, 0.32f, 1.0f)
    )

    /**
     * Analyzes a downscaled screen bitmap using high-performance spatial sampling & HSV filtering.
     * Coordinates are mapped accurately back to actual device screen dimensions.
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

        // Accurate screen coordinate scaling factors
        val scaleX = screenWidth.toFloat() / width.toFloat()
        val scaleY = screenHeight.toFloat() / height.toFloat()

        // Adaptive spatial sampling grid step based on resolution
        val step = max(3, width / 95)
        val topLimit = (height * config.detectionRegionTop).toInt().coerceIn(0, height - 1)
        val bottomLimit = (height * config.detectionRegionBottom).toInt().coerceIn(topLimit + 1, height)

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
                if (alpha < 120) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // 1. Check for Bomb / Obstacle (dark metallic spheres with low luminance)
                if (value < 0.14f || (r < 32 && g < 32 && b < 32 && value < 0.25f)) {
                    rawBombPoints.add(x.toFloat() to y.toFloat())
                    continue
                }

                // 2. Strict Dojo Wood Background Masking
                // Brown wood texture typically has Hue 18°..45°, low-to-medium Saturation (0.15..0.52), Value 0.15..0.60
                val isWoodDojo = (hue in 18f..46f) && (sat < 0.54f) && (value < 0.65f)
                if (isWoodDojo) {
                    continue
                }

                // 3. Ad Skip "X" button detection (near top corners with high luminance and low saturation)
                if (config.autoSkipAds && y < height * 0.20f && (x < width * 0.22f || x > width * 0.78f)) {
                    if (value > 0.88f && sat < 0.15f) {
                        rawAdPoints.add(x.toFloat() to y.toFloat())
                        continue
                    }
                }

                // 4. Lobby Game Start Fruit detection (center-lower arena zone)
                if (config.autoStartGame && y > height * 0.50f && y < height * 0.85f && x > width * 0.25f && x < width * 0.75f) {
                    if ((hue in 70f..155f || hue in 340f..360f || hue in 0f..18f) && sat > 0.48f && value > 0.40f) {
                        rawMenuPoints.add(x.toFloat() to y.toFloat())
                    }
                }

                // 5. Match active fruit color profiles
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
                        val label = if (profile.name.startsWith("Red")) "Red" else profile.name
                        rawFruitPoints.add(Triple(x.toFloat(), y.toFloat(), label))
                        break
                    }
                }
            }
        }

        // Cluster raw points into distinct fruit targets scaled to screen coordinates
        val clusteredFruits = clusterPoints(rawFruitPoints, clusterRadius = step * 3.5f)
            .map { (x, y, colorType) ->
                FruitTarget(
                    x = (x * scaleX).coerceIn(20f, screenWidth - 20f),
                    y = (y * scaleY).coerceIn(20f, screenHeight - 20f),
                    radius = 55f,
                    colorType = colorType,
                    isBomb = false
                )
            }

        // Cluster bombs scaled to screen coordinates
        val clusteredBombs = clusterSimplePoints(rawBombPoints, clusterRadius = step * 3.0f)
            .map { (x, y) ->
                FruitTarget(
                    x = (x * scaleX).coerceIn(20f, screenWidth - 20f),
                    y = (y * scaleY).coerceIn(20f, screenHeight - 20f),
                    radius = 70f,
                    colorType = "Bomb",
                    isBomb = true
                )
            }

        // Ad Skip points scaled to screen coordinates
        val adSkips = clusterSimplePoints(rawAdPoints, clusterRadius = step * 4f)
            .map { PointF(it.first * scaleX, it.second * scaleY) }

        // Menu Start points scaled to screen coordinates
        val menuActions = clusterSimplePoints(rawMenuPoints, clusterRadius = step * 4f)
            .map { PointF(it.first * scaleX, it.second * scaleY) }

        // Compute slash trajectories based on user configured strategy
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

            if (count >= 2) { // Low noise threshold to capture fast-moving fruits
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

            if (count >= 3) {
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

        // Filter fruits safely away from bombs if avoidance is enabled
        val safeFruits = fruits.filter { fruit ->
            if (!config.bombAvoidance) return@filter true
            bombs.none { bomb -> hypot(fruit.x - bomb.x, fruit.y - bomb.y) < safeBombRadius }
        }

        if (safeFruits.isEmpty()) return emptyList()

        when (config.sliceMode) {
            SliceMode.INSTANT_SLASH -> {
                for (fruit in safeFruits) {
                    val halfLen = config.minSliceLength / 2f
                    // 45-degree clean diagonal swipe
                    val startX = (fruit.x - halfLen * 0.7f).coerceIn(40f, screenWidth - 40f)
                    val startY = (fruit.y + halfLen * 0.7f).coerceIn(40f, screenHeight - 40f)
                    val endX = (fruit.x + halfLen * 0.7f).coerceIn(40f, screenWidth - 40f)
                    val endY = (fruit.y - halfLen * 0.7f).coerceIn(40f, screenHeight - 40f)

                    trajectories.add(SliceTrajectory(startX, startY, endX, endY, config.sliceDurationMs))
                }
            }

            SliceMode.COMBO_SLASH -> {
                // Connect closest fruits into single swift combo streaks
                val sortedFruits = safeFruits.sortedBy { it.x }
                var i = 0
                while (i < sortedFruits.size) {
                    val current = sortedFruits[i]
                    if (i + 1 < sortedFruits.size) {
                        val next = sortedFruits[i + 1]
                        val dist = hypot(current.x - next.x, current.y - next.y)
                        if (dist < 520f) {
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
