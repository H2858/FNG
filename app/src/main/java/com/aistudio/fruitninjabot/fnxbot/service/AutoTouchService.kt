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

    private var lastSlashTimestamp = 0L
    private var slashCountInWindow = 0
    private var windowStartTimestamp = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        updateScreenDimensions()
        BotStateController.addLog("AutoTouchService Accessibility engine active & ready.")
        Log.d(TAG, "AutoTouchService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility event monitoring hook
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
            screenWidth = max(metrics.widthPixels, 360)
            screenHeight = max(metrics.heightPixels, 640)
        } catch (e: Exception) {
            val dm = resources.displayMetrics
            screenWidth = max(dm.widthPixels, 360)
            screenHeight = max(dm.heightPixels, 640)
        }
    }

    /**
     * Starts continuous high-frequency auto slashing gestures.
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
                val gesture = generateNextSlashGesture(config)

                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        BotStateController.incrementSlashCount(1)
                        slashCountInWindow++

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
                        Log.w(TAG, "Slash gesture cancelled by system")
                    }
                }, null)

                // High speed delay between swipes (configurable: default 60ms - 100ms)
                val delayMs = config.delayBetweenSwipesMs.coerceIn(20L, 300L)
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
     * Computes dynamic swipe trajectories across the central arena bounds.
     */
    private fun generateNextSlashGesture(config: BotConfig): GestureDescription {
        val boundsRatio = config.arenaBoundsRatio.coerceIn(0.40f, 0.95f)
        val marginX = (screenWidth * (1f - boundsRatio) / 2f)
        val marginY = (screenHeight * (1f - boundsRatio) / 2f)

        val minX = marginX
        val maxX = screenWidth - marginX
        val minY = marginY
        val maxY = screenHeight - marginY

        val slashLen = Random.nextDouble(
            config.minSlashLength.toDouble(),
            config.maxSlashLength.toDouble()
        ).toFloat()

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when (config.pattern) {
            SlashPattern.RANDOM_CHAOS -> {
                // Random multi-directional diagonal, horizontal, or vertical slashes
                val centerX = Random.nextDouble(minX.toDouble(), maxX.toDouble()).toFloat()
                val centerY = Random.nextDouble(minY.toDouble(), maxY.toDouble()).toFloat()
                val angle = Random.nextDouble(0.0, Math.PI * 2)

                val half = slashLen / 2f
                startX = (centerX - cos(angle) * half).toFloat().coerceIn(minX, maxX)
                startY = (centerY - sin(angle) * half).toFloat().coerceIn(minY, maxY)
                endX = (centerX + cos(angle) * half).toFloat().coerceIn(minX, maxX)
                endY = (centerY + sin(angle) * half).toFloat().coerceIn(minY, maxY)
            }

            SlashPattern.CROSS_GRID -> {
                // Alternating diagonal cross slashes (top-left to bottom-right, or bottom-left to top-right)
                val isPositiveSlope = Random.nextBoolean()
                val midX = Random.nextDouble(minX + 80.0, maxX - 80.0).toFloat()
                val midY = Random.nextDouble(minY + 80.0, maxY - 80.0).toFloat()
                val half = slashLen / 2f

                if (isPositiveSlope) {
                    startX = (midX - half * 0.7f).coerceIn(minX, maxX)
                    startY = (midY + half * 0.7f).coerceIn(minY, maxY)
                    endX = (midX + half * 0.7f).coerceIn(minX, maxX)
                    endY = (midY - half * 0.7f).coerceIn(minY, maxY)
                } else {
                    startX = (midX - half * 0.7f).coerceIn(minX, maxX)
                    startY = (midY - half * 0.7f).coerceIn(minY, maxY)
                    endX = (midX + half * 0.7f).coerceIn(minX, maxX)
                    endY = (midY + half * 0.7f).coerceIn(minY, maxY)
                }
            }

            SlashPattern.DUAL_SWEEP -> {
                // Fast wide horizontal sweeps across the fruit apex zone
                val sweepY = Random.nextDouble(minY.toDouble(), maxY.toDouble()).toFloat()
                val leftToRight = Random.nextBoolean()
                val half = slashLen / 2f
                val centerX = screenWidth / 2f

                if (leftToRight) {
                    startX = (centerX - half).coerceIn(minX, maxX)
                    startY = (sweepY + Random.nextInt(-40, 40)).coerceIn(minY, maxY)
                    endX = (centerX + half).coerceIn(minX, maxX)
                    endY = (sweepY + Random.nextInt(-40, 40)).coerceIn(minY, maxY)
                } else {
                    startX = (centerX + half).coerceIn(minX, maxX)
                    startY = (sweepY + Random.nextInt(-40, 40)).coerceIn(minY, maxY)
                    endX = (centerX - half).coerceIn(minX, maxX)
                    endY = (sweepY + Random.nextInt(-40, 40)).coerceIn(minY, maxY)
                }
            }

            SlashPattern.WHIRLWIND -> {
                // Multi-point circular slash path
                val centerX = screenWidth / 2f + Random.nextInt(-100, 100)
                val centerY = screenHeight / 2f + Random.nextInt(-150, 150)
                val angleStart = Random.nextDouble(0.0, Math.PI * 2)
                val radius = (slashLen / 2.5f).coerceIn(100f, 300f)

                val path = Path().apply {
                    val p0X = (centerX + cos(angleStart) * radius).toFloat().coerceIn(minX, maxX)
                    val p0Y = (centerY + sin(angleStart) * radius).toFloat().coerceIn(minY, maxY)
                    moveTo(p0X, p0Y)

                    for (step in 1..3) {
                        val ang = angleStart + step * (Math.PI / 2.0)
                        val px = (centerX + cos(ang) * radius).toFloat().coerceIn(minX, maxX)
                        val py = (centerY + sin(ang) * radius).toFloat().coerceIn(minY, maxY)
                        lineTo(px, py)
                    }
                }

                val stroke = GestureDescription.StrokeDescription(
                    path,
                    0L,
                    config.swipeDurationMs.coerceIn(30L, 60L)
                )
                return GestureDescription.Builder().addStroke(stroke).build()
            }
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val duration = config.swipeDurationMs.coerceIn(25L, 60L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    /**
     * Dispatches a single manual test swipe.
     */
    fun performSingleSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 40L) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, max(25L, durationMs))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSlashCount(1)
            }
        }, null)
    }
}
