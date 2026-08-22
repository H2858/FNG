package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutoTouchService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoTouchService"

        private var _instance: AutoTouchService? = null
        val instance: AutoTouchService?
            get() = _instance

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        fun isRunning(): Boolean = _instance != null && _isServiceConnected.value
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance = this
        _isServiceConnected.value = true
        BotStateController.addLog("Accessibility AutoTouchService connected and active.")
        Log.d(TAG, "AutoTouchService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events can be observed if needed for UI transitions
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoTouchService interrupted.")
        BotStateController.addLog("AutoTouchService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (_instance == this) {
            _instance = null
            _isServiceConnected.value = false
        }
        BotStateController.addLog("AutoTouchService destroyed.")
        Log.d(TAG, "AutoTouchService destroyed.")
    }

    /**
     * Performs a linear swipe gesture between two coordinates.
     * Ideal for quick fruit slicing.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 60L,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Gesture dispatch requires Android 7.0+ (API 24)")
            onResult?.invoke(false)
            return false
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val clampedDuration = durationMs.coerceIn(20L, 500L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, clampedDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSliceCount(1)
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Swipe gesture cancelled at ($startX,$startY)->($endX,$endY)")
                onResult?.invoke(false)
            }
        }, null)

        if (!dispatched) {
            Log.e(TAG, "Failed to dispatch swipe gesture.")
            onResult?.invoke(false)
        }
        return dispatched
    }

    /**
     * Performs a single tap/click gesture at specified coordinate.
     * Ideal for UI buttons, retry dialogs, and ad skip prompts.
     */
    fun performClick(
        x: Float,
        y: Float,
        durationMs: Long = 40L,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onResult?.invoke(false)
            return false
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(10L, 100L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.addLog("Click performed at (${x.toInt()}, ${y.toInt()})")
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onResult?.invoke(false)
            }
        }, null)
    }

    /**
     * Performs a multi-point continuous slice gesture (combo/curve cut).
     */
    fun performMultiSwipe(
        points: List<Pair<Float, Float>>,
        durationMs: Long = 100L,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (points.size < 2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onResult?.invoke(false)
            return false
        }

        val path = Path().apply {
            moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                lineTo(points[i].first, points[i].second)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceIn(30L, 800L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSliceCount(1)
                onResult?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                onResult?.invoke(false)
            }
        }, null)
    }
}
