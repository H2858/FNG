package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * ColorDetector - High-Performance Vision & Color Detection Engine for Fruit Ninja Automation.
 *
 * Scans frame regions for:
 * 1. Fruit Colors (Watermelon Red, Banana Yellow, Kiwi/Lime Green, Orange, Magenta)
 * 2. Menu/App Play Slicing Button (Watermelon icon in lower menu region)
 * 3. Ad/Dialog Skip and Close ("X") Buttons in screen corners
 * 4. Metallic Dark Bomb Obstacles with safety radii
 */
object ColorDetector {
    private const val TAG = "ColorDetector"

    enum class TargetType {
        FRUIT,
        BOMB,
        MENU_PLAY_BUTTON,
        AD_SKIP_BUTTON
    }

    data class DetectedObject(
        val x: Float,
        val y: Float,
        val radius: Float = 35f,
        val label: String,
        val type: TargetType,
        val confidence: Float = 1.0f,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class SlicePath(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val points: List<Pair<Float, Float>> = emptyList(),
        val score: Int = 1,
        val isMenuTrigger: Boolean = false
    )

    data class VisionAnalysisResult(
        val fruits: List<DetectedObject>,
        val bombs: List<DetectedObject>,
        val menuButtons: List<DetectedObject>,
        val skipButtons: List<DetectedObject>,
        val suggestedSlices: List<SlicePath>,
        val suggestedClicks: List<Pair<Float, Float>>,
        val processDurationMs: Long = 0L
    )

    /**
     * Analyzes downscaled screen frame bitmap for game targets and UI elements.
     * Uses optimized stride sampling to achieve >60fps throughput without GC pressure.
     */
    fun analyzeFrame(
        bitmap: Bitmap,
        screenWidth: Int,
        screenHeight: Int,
        config: BotConfig
    ): VisionAnalysisResult {
        val startTime = System.currentTimeMillis()
        val bw = bitmap.width
        val bh = bitmap.height
        if (bw <= 0 || bh <= 0) {
            return VisionAnalysisResult(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        val scaleX = screenWidth.toFloat() / bw
        val scaleY = screenHeight.toFloat() / bh

        val topLimit = (bh * config.detectionRegionTop).toInt().coerceIn(0, bh - 1)
        val bottomLimit = (bh * config.detectionRegionBottom).toInt().coerceIn(0, bh - 1)

        val rawFruits = mutableListOf<DetectedObject>()
        val rawBombs = mutableListOf<DetectedObject>()
        val rawMenuButtons = mutableListOf<DetectedObject>()
        val rawSkipButtons = mutableListOf<DetectedObject>()

        val step = max(2, bw / 110) // Adaptive stride
        val hsv = FloatArray(3)

        // 1. Scan Main Game Area for Fruits and Bombs
        for (y in topLimit..bottomLimit step step) {
            for (x in step until (bw - step) step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                Color.RGBToHSV(r, g, b, hsv)
                val hue = hsv[0]
                val sat = hsv[1]
                val value = hsv[2]

                // Bomb Detection: Dark metallic sphere (low value, dark gray/black)
                if (value < 0.17f && r < 48 && g < 48 && b < 48) {
                    rawBombs.add(
                        DetectedObject(
                            x = x * scaleX,
                            y = y * scaleY,
                            radius = 48f,
                            label = "Bomb",
                            type = TargetType.BOMB
                        )
                    )
                    continue
                }

                // Fruit Color Classification
                if (sat >= 0.40f && value >= 0.35f) {
                    val colorLabel = classifyFruitColor(hue, sat, value, r, g, b)
                    if (colorLabel != null && config.activeFruitColors.contains(colorLabel)) {
                        rawFruits.add(
                            DetectedObject(
                                x = x * scaleX,
                                y = y * scaleY,
                                radius = 38f,
                                label = colorLabel,
                                type = TargetType.FRUIT
                            )
                        )
                    }
                }
            }
        }

        // 2. Scan Menu Slicing Play Buttons (Bottom-Center / Mid-Lower Screen)
        if (config.autoStartGame) {
            val menuTop = (bh * 0.55f).toInt().coerceIn(0, bh - 1)
            val menuBottom = (bh * 0.90f).toInt().coerceIn(0, bh - 1)
            val menuLeft = (bw * 0.20f).toInt().coerceIn(0, bw - 1)
            val menuRight = (bw * 0.80f).toInt().coerceIn(0, bw - 1)

            var playFruitPixelCount = 0
            var sumX = 0f
            var sumY = 0f

            for (my in menuTop..menuBottom step step) {
                for (mx in menuLeft..menuRight step step) {
                    val pixel = bitmap.getPixel(mx, my)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    Color.RGBToHSV(r, g, b, hsv)

                    // Classic Menu Play Watermelon / Arcade Banana icon in lobby
                    val isMenuFruitColor = (hsv[0] in 75f..140f && hsv[1] > 0.45f) || // Green watermelon rind / Dojo icon
                            ((hsv[0] >= 345f || hsv[0] <= 15f) && hsv[1] > 0.50f) // Red center
                    if (isMenuFruitColor) {
                        playFruitPixelCount++
                        sumX += mx * scaleX
                        sumY += my * scaleY
                    }
                }
            }

            // If a dense cluster of menu fruit pixels is found in the menu quadrant
            if (playFruitPixelCount >= 18) {
                rawMenuButtons.add(
                    DetectedObject(
                        x = sumX / playFruitPixelCount,
                        y = sumY / playFruitPixelCount,
                        radius = 65f,
                        label = "Menu Play Fruit",
                        type = TargetType.MENU_PLAY_BUTTON
                    )
                )
            }
        }

        // 3. Scan for Ad / Skip / Close "X" Buttons in Corners
        if (config.autoSkipAds) {
            val cornerSizeW = (bw * 0.22f).toInt()
            val cornerSizeH = (bh * 0.14f).toInt()

            // Top-Right Corner (Primary close "X" / "Skip Ad" location)
            scanAdButtonRegion(
                bitmap,
                startX = bw - cornerSizeW,
                endX = bw,
                startY = 0,
                endY = cornerSizeH,
                scaleX = scaleX,
                scaleY = scaleY,
                step = step,
                cornerLabel = "Top-Right Ad Close",
                outList = rawSkipButtons
            )

            // Top-Left Corner (Secondary close location)
            scanAdButtonRegion(
                bitmap,
                startX = 0,
                endX = cornerSizeW,
                startY = 0,
                endY = cornerSizeH,
                scaleX = scaleX,
                scaleY = scaleY,
                step = step,
                cornerLabel = "Top-Left Ad Close",
                outList = rawSkipButtons
            )

            // Bottom-Right Corner (Reward skip buttons)
            scanAdButtonRegion(
                bitmap,
                startX = bw - cornerSizeW,
                endX = bw,
                startY = bh - cornerSizeH,
                endY = bh,
                scaleX = scaleX,
                scaleY = scaleY,
                step = step,
                cornerLabel = "Bottom-Right Skip",
                outList = rawSkipButtons
            )
        }

        // Cluster and filter noise
        val clusteredFruits = clusterObjects(rawFruits, clusterRadius = 65f)
        val clusteredBombs = clusterObjects(rawBombs, clusterRadius = 80f)
        val clusteredMenuButtons = clusterObjects(rawMenuButtons, clusterRadius = 90f)
        val clusteredSkipButtons = clusterObjects(rawSkipButtons, clusterRadius = 50f)

        // Generate Slicing paths
        val slices = mutableListOf<SlicePath>()

        // Add regular gameplay fruit slices
        slices.addAll(generateSlices(clusteredFruits, clusteredBombs, config, screenWidth, screenHeight))

        // If in menu and no gameplay fruits detected, add menu play fruit slash
        if (clusteredFruits.isEmpty() && clusteredMenuButtons.isNotEmpty()) {
            for (menuBtn in clusteredMenuButtons) {
                slices.add(
                    SlicePath(
                        startX = (menuBtn.x - 120f).coerceIn(30f, screenWidth - 30f),
                        startY = (menuBtn.y + 110f).coerceIn(30f, screenHeight - 30f),
                        endX = (menuBtn.x + 120f).coerceIn(30f, screenWidth - 30f),
                        endY = (menuBtn.y - 110f).coerceIn(30f, screenHeight - 30f),
                        score = 1,
                        isMenuTrigger = true
                    )
                )
            }
        }

        // Suggested Click Coordinates for ad dismiss / skip buttons
        val clicks = clusteredSkipButtons.map { Pair(it.x, it.y) }

        val elapsed = System.currentTimeMillis() - startTime
        return VisionAnalysisResult(
            fruits = clusteredFruits,
            bombs = clusteredBombs,
            menuButtons = clusteredMenuButtons,
            skipButtons = clusteredSkipButtons,
            suggestedSlices = slices,
            suggestedClicks = clicks,
            processDurationMs = elapsed
        )
    }

    private fun scanAdButtonRegion(
        bitmap: Bitmap,
        startX: Int,
        endX: Int,
        startY: Int,
        endY: Int,
        scaleX: Float,
        scaleY: Float,
        step: Int,
        cornerLabel: String,
        outList: MutableList<DetectedObject>
    ) {
        val sX = startX.coerceIn(0, bitmap.width - 1)
        val eX = endX.coerceIn(0, bitmap.width)
        val sY = startY.coerceIn(0, bitmap.height - 1)
        val eY = endY.coerceIn(0, bitmap.height)

        var highContrastPixelCount = 0
        var sumX = 0f
        var sumY = 0f

        val hsv = FloatArray(3)
        for (y in sY until eY step step) {
            for (x in sX until eX step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                Color.RGBToHSV(r, g, b, hsv)

                // White "X" / Skip text or bright grey circular close button
                val isBrightButton = (hsv[1] < 0.20f && hsv[2] > 0.82f) || // Crisp white X
                        (r > 210 && g > 210 && b > 210) // Light grey badge
                if (isBrightButton) {
                    highContrastPixelCount++
                    sumX += x * scaleX
                    sumY += y * scaleY
                }
            }
        }

        // Distinct button shape indicator in corner
        if (highContrastPixelCount in 6..60) {
            outList.add(
                DetectedObject(
                    x = sumX / highContrastPixelCount,
                    y = sumY / highContrastPixelCount,
                    radius = 35f,
                    label = cornerLabel,
                    type = TargetType.AD_SKIP_BUTTON
                )
            )
        }
    }

    private fun classifyFruitColor(
        hue: Float,
        sat: Float,
        value: Float,
        r: Int,
        g: Int,
        b: Int
    ): String? {
        return when {
            // Watermelon Red / Strawberry (Hue: 345-360 or 0-18)
            (hue >= 345f || hue <= 18f) && sat > 0.45f -> "Red"
            // Orange (Hue: 19-42)
            hue in 19f..42f && sat > 0.48f -> "Orange"
            // Banana / Lemon Yellow (Hue: 43-68)
            hue in 43f..68f && sat > 0.42f -> "Yellow"
            // Lime / Kiwi / Green Apple (Hue: 72-155)
            hue in 72f..155f && sat > 0.38f -> "Green"
            // Dragonfruit / Plum / Passionfruit Magenta (Hue: 280-344)
            hue in 280f..344f && sat > 0.38f -> "Magenta"
            else -> null
        }
    }

    private fun clusterObjects(
        points: List<DetectedObject>,
        clusterRadius: Float
    ): List<DetectedObject> {
        val clusters = mutableListOf<DetectedObject>()
        val visited = BooleanArray(points.size)

        for (i in points.indices) {
            if (visited[i]) continue
            visited[i] = true

            var sumX = points[i].x
            var sumY = points[i].y
            var count = 1
            val type = points[i].type
            val label = points[i].label

            for (j in (i + 1) until points.size) {
                if (visited[j]) continue
                val dist = hypot(points[i].x - points[j].x, points[i].y - points[j].y)
                if (dist < clusterRadius) {
                    visited[j] = true
                    sumX += points[j].x
                    sumY += points[j].y
                    count++
                }
            }

            // Discard single pixel noise
            val minPoints = if (type == TargetType.AD_SKIP_BUTTON) 1 else 2
            if (count >= minPoints) {
                clusters.add(
                    DetectedObject(
                        x = sumX / count,
                        y = sumY / count,
                        radius = (clusterRadius * 0.75f).coerceAtLeast(30f),
                        label = label,
                        type = type
                    )
                )
            }
        }
        return clusters
    }

    private fun generateSlices(
        fruits: List<DetectedObject>,
        bombs: List<DetectedObject>,
        config: BotConfig,
        screenWidth: Int,
        screenHeight: Int
    ): List<SlicePath> {
        if (fruits.isEmpty()) return emptyList()

        val validFruits = if (config.bombAvoidance && bombs.isNotEmpty()) {
            fruits.filter { fruit ->
                bombs.none { bomb -> hypot(fruit.x - bomb.x, fruit.y - bomb.y) < 145f }
            }
        } else {
            fruits
        }

        if (validFruits.isEmpty()) return emptyList()

        val slices = mutableListOf<SlicePath>()

        when (config.sliceMode) {
            SliceMode.INSTANT_SLASH -> {
                for (fruit in validFruits) {
                    val halfLen = config.minSliceLength / 2f
                    val startX = (fruit.x - halfLen * 0.7f).coerceIn(20f, screenWidth - 20f)
                    val startY = (fruit.y + halfLen * 0.7f).coerceIn(20f, screenHeight - 20f)
                    val endX = (fruit.x + halfLen * 0.7f).coerceIn(20f, screenWidth - 20f)
                    val endY = (fruit.y - halfLen * 0.7f).coerceIn(20f, screenHeight - 20f)

                    if (!isSliceIntersectingBombs(startX, startY, endX, endY, bombs, config.bombAvoidance)) {
                        slices.add(SlicePath(startX, startY, endX, endY, score = 1))
                    }
                }
            }

            SliceMode.COMBO_SLASH -> {
                if (validFruits.size >= 2) {
                    val sorted = validFruits.sortedBy { it.x }
                    val chainPoints = sorted.map { Pair(it.x, it.y) }

                    val first = chainPoints.first()
                    val last = chainPoints.last()
                    val dx = last.first - first.first
                    val dy = last.second - first.second
                    val dist = hypot(dx, dy)
                    val extension = 60f
                    val sX = if (dist > 0) first.first - (dx / dist) * extension else first.first - extension
                    val sY = if (dist > 0) first.second - (dy / dist) * extension else first.second
                    val eX = if (dist > 0) last.first + (dx / dist) * extension else last.first + extension
                    val eY = if (dist > 0) last.second + (dy / dist) * extension else last.second

                    if (!isSliceIntersectingBombs(sX, sY, eX, eY, bombs, config.bombAvoidance)) {
                        slices.add(SlicePath(sX, sY, eX, eY, chainPoints, score = chainPoints.size))
                    } else {
                        for (f in validFruits) {
                            slices.add(SlicePath(f.x - 70f, f.y + 70f, f.x + 70f, f.y - 70f, score = 1))
                        }
                    }
                } else {
                    val f = validFruits.first()
                    slices.add(SlicePath(f.x - 80f, f.y + 80f, f.x + 80f, f.y - 80f, score = 1))
                }
            }

            SliceMode.MULTI_SWEEP -> {
                for (f in validFruits) {
                    val s1X = (f.x - 100f).coerceIn(10f, screenWidth - 10f)
                    val s1Y = (f.y + 90f).coerceIn(10f, screenHeight - 10f)
                    val e1X = (f.x + 100f).coerceIn(10f, screenWidth - 10f)
                    val e1Y = (f.y - 90f).coerceIn(10f, screenHeight - 10f)
                    slices.add(SlicePath(s1X, s1Y, e1X, e1Y, score = 2))
                }
            }

            SliceMode.BOMB_SAFE_SLASH -> {
                for (f in validFruits) {
                    val safeAngle = findSafeSliceAngle(f, bombs)
                    val len = config.minSliceLength * 0.8f
                    val sX = (f.x - kotlin.math.cos(safeAngle) * len).coerceIn(20f, screenWidth - 20f)
                    val sY = (f.y - kotlin.math.sin(safeAngle) * len).coerceIn(20f, screenHeight - 20f)
                    val eX = (f.x + kotlin.math.cos(safeAngle) * len).coerceIn(20f, screenWidth - 20f)
                    val eY = (f.y + kotlin.math.sin(safeAngle) * len).coerceIn(20f, screenHeight - 20f)

                    if (!isSliceIntersectingBombs(sX, sY, eX, eY, bombs, true)) {
                        slices.add(SlicePath(sX, sY, eX, eY, score = 1))
                    }
                }
            }
        }

        return slices
    }

    private fun isSliceIntersectingBombs(
        sx: Float,
        sy: Float,
        ex: Float,
        ey: Float,
        bombs: List<DetectedObject>,
        bombAvoidance: Boolean
    ): Boolean {
        if (!bombAvoidance || bombs.isEmpty()) return false

        for (bomb in bombs) {
            val dist = pointToSegmentDistance(bomb.x, bomb.y, sx, sy, ex, ey)
            if (dist < 95f) {
                return true
            }
        }
        return false
    }

    private fun pointToSegmentDistance(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            return hypot(px - x1, py - y1)
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    private fun findSafeSliceAngle(fruit: DetectedObject, bombs: List<DetectedObject>): Float {
        if (bombs.isEmpty()) return 0.785f // 45 deg
        var bestAngle = 0.785f
        var maxMinDist = 0f

        for (angleDeg in 0 until 180 step 15) {
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val dx = kotlin.math.cos(rad) * 100f
            val dy = kotlin.math.sin(rad) * 100f
            val sx = fruit.x - dx
            val sy = fruit.y - dy
            val ex = fruit.x + dx
            val ey = fruit.y + dy

            var minDistToBomb = Float.MAX_VALUE
            for (bomb in bombs) {
                val d = pointToSegmentDistance(bomb.x, bomb.y, sx, sy, ex, ey)
                if (d < minDistToBomb) minDistToBomb = d
            }
            if (minDistToBomb > maxMinDist) {
                maxMinDist = minDistToBomb
                bestAngle = rad
            }
        }
        return bestAngle
    }
}
