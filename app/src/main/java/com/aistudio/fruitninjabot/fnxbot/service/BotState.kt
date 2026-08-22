package com.aistudio.fruitninjabot.fnxbot.service

import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BotRunState {
    IDLE,
    SLASHING
}

enum class SlashPattern(val label: String, val shortName: String, val description: String) {
    INFINITY_WAVE(
        "Infinity Wave (∞)",
        "Infinity",
        "Continuous 16-segment connected looping path in upper 50% safe zone to bypass bottom bombs"
    ),
    FULL_SWEEP(
        "Edge-to-Edge Sweeps",
        "Sweeps",
        "Ultra-fast horizontal slashes extending from X=5% to X=95% of screen width"
    ),
    Z_GRID(
        "Z-Grid Blitz",
        "Z-Grid",
        "Continuous 3-point Z-shaped cuts spanning upper screen coordinates"
    ),
    DOUBLE_CROSS(
        "Double Cross-Cut (X)",
        "X-Cross",
        "Intersecting diagonal slashes covering the fruit trajectory apex"
    ),
    WHIRLWIND(
        "Whirlwind Vortex",
        "Vortex",
        "High-velocity rotating multi-point circular slashes in mid-upper arena"
    ),
    SPIRAL_WHIRLWIND(
        "Spiral Whirlwind",
        "Spiral",
        "Continuous expanding spiral loop starting from mid-screen expanding outward across upper bounds"
    )
}

data class BotConfig(
    val swipeDurationMs: Long = 35L,
    val delayBetweenSwipesMs: Long = 40L,
    val pattern: SlashPattern = SlashPattern.SPIRAL_WHIRLWIND,
    val arenaBoundsRatio: Float = 0.70f,
    val minSlashLength: Float = 280f,
    val maxSlashLength: Float = 640f,
    val autoRestartBananaEnabled: Boolean = true,
    val autoRestartBananaIntervalMs: Long = 3500L
)

/**
 * Central State Controller for Fruit Ninja Auto-Splasher Engine.
 */
object BotStateController {
    private val _botRunState = MutableStateFlow(BotRunState.IDLE)
    val botRunState: StateFlow<BotRunState> = _botRunState.asStateFlow()

    private val _config = MutableStateFlow(BotConfig())
    val config: StateFlow<BotConfig> = _config.asStateFlow()

    private val _slashCount = MutableStateFlow(0)
    val slashCount: StateFlow<Int> = _slashCount.asStateFlow()

    private val _slashesPerSecond = MutableStateFlow(0f)
    val slashesPerSecond: StateFlow<Float> = _slashesPerSecond.asStateFlow()

    private val _floatingOverlayActive = MutableStateFlow(false)
    val floatingOverlayActive: StateFlow<Boolean> = _floatingOverlayActive.asStateFlow()

    private val _recentLogs = MutableStateFlow<List<String>>(listOf("Auto-Splasher Engine ready."))
    val recentLogs: StateFlow<List<String>> = _recentLogs.asStateFlow()

    fun setRunState(state: BotRunState) {
        _botRunState.value = state
        addLog(if (state == BotRunState.SLASHING) "Auto-Splasher STARTED (Slashing active)" else "Auto-Splasher STOPPED (Idle)")
    }

    fun updateConfig(newConfig: BotConfig) {
        _config.value = newConfig
    }

    fun incrementSlashCount(count: Int = 1) {
        _slashCount.value += count
    }

    fun setSlashesPerSecond(sps: Float) {
        _slashesPerSecond.value = sps
    }

    fun setFloatingOverlayActive(active: Boolean) {
        _floatingOverlayActive.value = active
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
        _slashCount.value = 0
        _slashesPerSecond.value = 0f
    }
}
