package com.aistudio.fruitninjabot.fnxbot.service

import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BotRunState {
    STOPPED,
    RUNNING,
    PAUSED
}

enum class SliceMode(val label: String, val description: String) {
    INSTANT_SLASH("Instant Slash", "High-velocity straight cut directly through target centroid"),
    COMBO_SLASH("Combo Slash", "Chains multiple nearby targets into a single fluid multi-point swipe"),
    MULTI_SWEEP("Multi-Sweep", "Dual cross-cutting slashes across high-density fruit clusters"),
    BOMB_SAFE_SLASH("Bomb-Safe Slash", "Strict obstacle avoidance algorithm bypassing dark bomb clusters")
}

data class FruitTarget(
    val x: Float,
    val y: Float,
    val radius: Float = 40f,
    val colorType: String = "Fruit",
    val isBomb: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class BotConfig(
    val sliceDurationMs: Long = 60L,
    val sensitivityThreshold: Int = 35,
    val sliceMode: SliceMode = SliceMode.COMBO_SLASH,
    val bombAvoidance: Boolean = true,
    val autoSkipAds: Boolean = true,
    val autoStartGame: Boolean = true,
    val targetFps: Int = 30,
    val minSliceLength: Float = 160f,
    val maxSliceLength: Float = 550f,
    val continuousSweep: Boolean = false,
    val activeFruitColors: Set<String> = setOf("Red", "Orange", "Yellow", "Green", "Magenta"),
    val detectionRegionTop: Float = 0.08f,
    val detectionRegionBottom: Float = 0.94f
)

/**
 * Central State Controller for Fruit Ninja Automation Bot.
 * Single source of truth using StateFlow.
 */
object BotStateController {
    private val _botRunState = MutableStateFlow(BotRunState.STOPPED)
    val botRunState: StateFlow<BotRunState> = _botRunState.asStateFlow()

    private val _config = MutableStateFlow(BotConfig())
    val config: StateFlow<BotConfig> = _config.asStateFlow()

    private val _sliceCount = MutableStateFlow(0)
    val sliceCount: StateFlow<Int> = _sliceCount.asStateFlow()

    private val _detectedCount = MutableStateFlow(0)
    val detectedCount: StateFlow<Int> = _detectedCount.asStateFlow()

    private val _adSkipsCount = MutableStateFlow(0)
    val adSkipsCount: StateFlow<Int> = _adSkipsCount.asStateFlow()

    private val _menuClicksCount = MutableStateFlow(0)
    val menuClicksCount: StateFlow<Int> = _menuClicksCount.asStateFlow()

    private val _visionLatencyMs = MutableStateFlow(0L)
    val visionLatencyMs: StateFlow<Long> = _visionLatencyMs.asStateFlow()

    private val _floatingOverlayActive = MutableStateFlow(false)
    val floatingOverlayActive: StateFlow<Boolean> = _floatingOverlayActive.asStateFlow()

    private val _screenCaptureActive = MutableStateFlow(false)
    val screenCaptureActive: StateFlow<Boolean> = _screenCaptureActive.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(listOf("Vision & Automation Engine initialized."))
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    fun setRunState(state: BotRunState) {
        _botRunState.value = state
        addLog("Bot run state: ${state.name}")
    }

    fun updateConfig(newConfig: BotConfig) {
        _config.value = newConfig
    }

    fun incrementSliceCount(count: Int = 1) {
        _sliceCount.value += count
    }

    fun incrementDetectedCount(count: Int = 1) {
        _detectedCount.value += count
    }

    fun incrementAdSkipsCount(count: Int = 1) {
        _adSkipsCount.value += count
    }

    fun incrementMenuClicksCount(count: Int = 1) {
        _menuClicksCount.value += count
    }

    fun setVisionLatency(latencyMs: Long) {
        _visionLatencyMs.value = latencyMs
    }

    fun setFloatingOverlayActive(active: Boolean) {
        _floatingOverlayActive.value = active
    }

    fun setScreenCaptureActive(active: Boolean) {
        _screenCaptureActive.value = active
    }

    fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[$timestamp] $message"
        val current = _recentLogs.value.toMutableList()
        if (current.size >= 40) {
            current.removeAt(current.size - 1)
        }
        current.add(0, entry)
        _recentLogs.value = current
    }

    fun resetStats() {
        _sliceCount.value = 0
        _detectedCount.value = 0
        _adSkipsCount.value = 0
        _menuClicksCount.value = 0
        _visionLatencyMs.value = 0L
    }
}
