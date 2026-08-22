package com.aistudio.fruitninjabot.fnxbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class AutoTouchService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoTouchService"
        var instance: AutoTouchService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        BotStateController.addLog("Accessibility AutoTouchService connected and active.")
        Log.d(TAG, "AutoTouchService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener for system accessibility events
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutoTouchService interrupted")
        BotStateController.addLog("Accessibility service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _isServiceConnected.value = false
        BotStateController.addLog("Accessibility service disconnected.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _isServiceConnected.value = false
        super.onDestroy()
    }

    /**
     * Dispatches a single linear gesture slice between two screen points.
     */
    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 50L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, max(25L, durationMs))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSliceCount(1)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Swipe gesture cancelled at ($startX, $startY) -> ($endX, $endY)")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a multi-point continuous slice trajectory (combo slash).
     */
    fun performMultiSwipe(
        points: List<PointF>,
        durationMs: Long = 80L,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (points.size < 2) return false

        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, max(35L, durationMs))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                BotStateController.incrementSliceCount(points.size - 1)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Multi-point combo gesture cancelled")
                onComplete?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a click/tap event at screen coordinates.
     */
    fun performClick(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y + 1f)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 40L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Click gesture cancelled at ($x, $y)")
                onComplete?.invoke(false)
            }
        }, null)
    }
}
