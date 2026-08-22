package com.aistudio.fruitninjabot.fnxbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class AutoTouchService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoTouchService"
        var instance: AutoTouchService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var slashingJob: Job? = null
    private val isSlashingActive = AtomicBoolean(false)

    private var screenWidth = 1080
    private var screenHeight = 1920

    private var slashCountInWindow = 0
    private var windowStartTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        updateScreenDimensions()
        BotStateController.addLog("AutoTouchService ready. Safe-zone gesture engine active.")
        Log.d(TAG, "AutoTouchService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused - pure gesture dispatching
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoTouchService interrupted")
        stopAutoSlash()
        BotStateController.addLog("Accessibility service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAutoSlash()
        instance = null
        _isServiceConnected.value = false
        BotStateController.addLog("Accessibility service disconnected.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAutoSlash()
        instance = null
        _isServiceConnected.value = false
        super.onDestroy()
    }

    private fun updateScreenDimensions() {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidth = max(metrics.widthPixels, 480)
            screenHeight = max(metrics.heightPixels, 800)
        } catch (e: Exception) {
            val dm = resources.displayMetrics
            screenWidth = max(dm.widthPixels, 480)
            screenHeight = max(dm.heightPixels, 800)
        }
    }

    /**
     * Starts continuous high-frequency auto slashing gestures based on active pattern.
     */
    fun startAutoSlash() {
        if (isSlashingActive.getAndSet(true)) {
            return
        }

        updateScreenDimensions()
        BotStateController.setRunState(BotRunState.SLASHING)
        windowStartTimestamp = System.currentTimeMillis()
        slashCountInWindow = 0

        slashingJob = serviceScope.launch {
            while (isActive && isSlashingActive.get()) {
                val config = BotStateController.config.value
                val gesture = generatePatternGesture(config)

                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        val strokes = gestureDescription?.strokeCount ?: 1
                        BotStateController.incrementSlashCount(strokes)
                        slashCountInWindow += strokes

                        val now = System.currentTimeMillis()
                        val elapsed = now - windowStartTimestamp
                        if (elapsed >= 1000L) {
                            val sps = (slashCountInWindow * 1000f) / elapsed
                            BotStateController.setSlashesPerSecond(sps)
                            slashCountInWindow = 0
                            windowStartTimestamp = now
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                    }
                }, null)

                // Precise delay between consecutive swipe bursts (10ms - 100ms)
                val delayMs = config.delayBetweenSwipesMs.coerceIn(10L, 200L)
                delay(delayMs)
            }
        }
    }

    /**
     * Stops auto slashing gestures immediately.
     */
    fun stopAutoSlash() {
        if (!isSlashingActive.getAndSet(false)) {
            return
        }
        slashingJob?.cancel()
        slashingJob = null
        BotStateController.setRunState(BotRunState.IDLE)
        BotStateController.setSlashesPerSecond(0f)
    }

    /**
     * Generates gesture strokes with STRICT Y-axis bounds (12% to 55% screen height).
     */
    private fun generatePatternGesture(config: BotConfig): GestureDescription {
        val minY = screenHeight * 0.12f
        val maxY = screenHeight * 0.55f
        val minX = screenWidth * 0.05f
        val maxX = screenWidth * 0.95f

        val duration = config.swipeDurationMs.coerceIn(20L, 80L)

        return when (config.pattern) {
            SlashPattern.INFINITY_WAVE -> {
                // Pattern 1: Continuous 16-segment connected looping infinity path (Lemniscate)
                val centerX = screenWidth * 0.50f + Random.nextFloat() * 40f - 20f
                val centerY = (minY + maxY) * 0.50f + Random.nextFloat() * 30f - 15f
                val scaleX = (maxX - minX) * 0.42f
                val scaleY = (maxY - minY) * 0.45f

                val path = Path()
                val totalSegments = 16
                for (i in 0..totalSegments) {
                    val t = (i.toFloat() / totalSegments) * 2.0 * PI
                    // Lemniscate parametric: x = a*cos(t)/(1+sin^2(t)), y = b*sin(t)*cos(t)/(1+sin^2(t))
                    val denom = (1.0 + sin(t) * sin(t)).toFloat()
                    val px = (centerX + (scaleX * cos(t).toFloat()) / denom).coerceIn(minX, maxX)
                    val py = (centerY + (scaleY * (sin(t) * cos(t)).toFloat()) / denom).coerceIn(minY, maxY)

                    if (i == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }

                val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(35L))
                GestureDescription.Builder().addStroke(stroke).build()
            }

            SlashPattern.FULL_SWEEP -> {
                // Pattern 2: Full Edge-to-Edge Horizontal Sweeps (X=5% to X=95%)
                val isLeftToRight = Random.nextBoolean()
                val y1 = Random.nextDouble(minY.toDouble(), maxY.toDouble()).toFloat()
                val y2 = (y1 + Random.nextFloat() * 60f - 30f).coerceIn(minY, maxY)

                val startX = if (isLeftToRight) minX else maxX
                val endX = if (isLeftToRight) maxX else minX

                val path = Path().apply {
                    moveTo(startX, y1)
                    lineTo(endX, y2)
                }

                val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
                GestureDescription.Builder().addStroke(stroke).build()
            }

            SlashPattern.Z_GRID -> {
                // Pattern 3: Z-Grid Blitz (Continuous 3-point Z-shaped cuts spanning upper screen)
                val isReverse = Random.nextBoolean()
                val left = minX + Random.nextFloat() * 60f
                val right = maxX - Random.nextFloat() * 60f
                val topY = minY + Random.nextFloat() * 40f
                val bottomY = maxY - Random.nextFloat() * 40f

                val path = Path().apply {
                    if (!isReverse) {
                        // Top-left -> Top-right -> Bottom-left -> Bottom-right
                        moveTo(left, topY)
                        lineTo(right, topY + 20f)
                        lineTo(left, bottomY - 20f)
                        lineTo(right, bottomY)
                    } else {
                        // Top-right -> Top-left -> Bottom-right -> Bottom-left
                        moveTo(right, topY)
                        lineTo(left, topY + 20f)
                        lineTo(right, bottomY - 20f)
                        lineTo(left, bottomY)
                    }
                }

                val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(30L))
                GestureDescription.Builder().addStroke(stroke).build()
            }

            SlashPattern.DOUBLE_CROSS -> {
                // Pattern 4: Double Cross-Cut (Simultaneous intersecting diagonal strokes)
                val midX = screenWidth * 0.50f + Random.nextFloat() * 80f - 40f
                val midY = (minY + maxY) * 0.50f + Random.nextFloat() * 60f - 30f
                val spreadX = (maxX - minX) * 0.38f
                val spreadY = (maxY - minY) * 0.45f

                // Stroke 1: Top-Left to Bottom-Right
                val p1 = Path().apply {
                    moveTo((midX - spreadX).coerceIn(minX, maxX), (midY - spreadY).coerceIn(minY, maxY))
                    lineTo((midX + spreadX).coerceIn(minX, maxX), (midY + spreadY).coerceIn(minY, maxY))
                }

                // Stroke 2: Top-Right to Bottom-Left
                val p2 = Path().apply {
                    moveTo((midX + spreadX).coerceIn(minX, maxX), (midY - spreadY).coerceIn(minY, maxY))
                    lineTo((midX - spreadX).coerceIn(minX, maxX), (midY + spreadY).coerceIn(minY, maxY))
                }

                val stroke1 = GestureDescription.StrokeDescription(p1, 0L, duration)
                val stroke2 = GestureDescription.StrokeDescription(p2, 0L, duration)

                GestureDescription.Builder()
                    .addStroke(stroke1)
                    .addStroke(stroke2)
                    .build()
            }

            SlashPattern.WHIRLWIND -> {
                // Pattern 5: Whirlwind Vortex (High-velocity rotating multi-point circular slash)
                val centerX = screenWidth * 0.50f + Random.nextFloat() * 60f - 30f
                val centerY = (minY + maxY) * 0.50f + Random.nextFloat() * 40f - 20f
                val radiusX = (maxX - minX) * 0.32f
                val radiusY = (maxY - minY) * 0.42f
                val startAngle = Random.nextDouble(0.0, 2.0 * PI)

                val path = Path()
                val totalSteps = 8
                for (step in 0..totalSteps) {
                    val ang = startAngle + (step.toFloat() / totalSteps) * 2.0 * PI
                    val px = (centerX + cos(ang).toFloat() * radiusX).coerceIn(minX, maxX)
                    val py = (centerY + sin(ang).toFloat() * radiusY).coerceIn(minY, maxY)
                    if (step == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }

                val stroke = GestureDescription.StrokeDescription(path, 0L, duration.coerceAtLeast(30L))
                GestureDescription.Builder().addStroke(stroke).build()
            }
        }
    }

    /**
     * Dispatches a single manual test swipe.
     */
    fun performSingleSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 35L) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, max(20L, durationMs))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSlashCount(1)
            }
        }, null)
    }
}
