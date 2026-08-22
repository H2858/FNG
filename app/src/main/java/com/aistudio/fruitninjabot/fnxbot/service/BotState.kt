package com.aistudio.fruitninjabot.fnxbot.service

import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BotRunState {
    IDLE,
    SLASHING
}

enum class SlashPattern(val label: String, val description: String) {
    RANDOM_CHAOS("Random Multi-Angle", "Unpredictable rapid slashes across multiple diagonal & linear angles"),
    CROSS_GRID("Cross Slice Grid", "Alternating X-pattern and cross-cuts covering the fruit trajectory apex"),
    DUAL_SWEEP("Dual Sweep", "Rapid alternating left-to-right and right-to-left screen cuts"),
    WHIRLWIND("Whirlwind Vortex", "Continuous rotating multi-point slashes around the central arena")
}

data class BotConfig(
    val swipeDurationMs: Long = 40L,
    val delayBetweenSwipesMs: Long = 75L,
    val pattern: SlashPattern = SlashPattern.RANDOM_CHAOS,
    val arenaBoundsRatio: Float = 0.70f,
    val minSlashLength: Float = 260f,
    val maxSlashLength: Float = 620f
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
