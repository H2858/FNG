package com.aistudio.fruitninjabot.fnxbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aistudio.fruitninjabot.fnxbot.MainActivity
import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingControlService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_hud_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP_OVERLAY = "ACTION_STOP_FLOATING_OVERLAY"

        var isRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var rootContainer: LinearLayout? = null
    private var collapsedFabView: LinearLayout? = null
    private var expandedPanelView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isExpanded = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null

    // Reference to pattern buttons for dynamic styling updates
    private val patternButtons = mutableMapOf<SlashPattern, TextView>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        createFloatingHud()
        observeBotState()
        isRunning = true
        BotStateController.setFloatingOverlayActive(true)
        BotStateController.addLog("Floating HUD overlay activated.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls floating overlay widget for Fruit Ninja"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fruit Ninja Auto-Splasher")
            .setContentText("HUD Overlay active. Tap to open dashboard.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createFloatingHud() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        collapsedFabView = buildCollapsedFabView()
        expandedPanelView = buildExpandedPanelView()

        rootContainer?.addView(collapsedFabView)
        rootContainer?.addView(expandedPanelView)

        // Initial state: Collapsed FAB only
        expandedPanelView?.visibility = View.GONE

        setupDragListener(rootContainer!!)
        windowManager?.addView(rootContainer, layoutParams)
    }

    /**
     * Compact Glowing Floating Action Button (FAB)
     */
    private fun buildCollapsedFabView(): LinearLayout {
        val fabBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(28f)
            setColor(Color.parseColor("#EE12151D"))
            setStroke(dpToPx(2f).toInt(), Color.parseColor("#00E676"))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fabBackground
            setPadding(dpToPx(14f).toInt(), dpToPx(10f).toInt(), dpToPx(16f).toInt(), dpToPx(10f).toInt())
            elevation = dpToPx(12f)
            tag = "collapsed_fab"

            // Icon Indicator
            val iconTv = TextView(this@FloatingControlService).apply {
                tag = "fab_icon"
                text = "⚡"
                textSize = 18f
                setPadding(0, 0, dpToPx(8f).toInt(), 0)
            }
            addView(iconTv)

            // Slash Count & Status Text
            val statsTv = TextView(this@FloatingControlService).apply {
                tag = "fab_stats"
                text = "0 Slashes"
                textSize = 13f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            addView(statsTv)

            // Expand Arrow
            val arrowTv = TextView(this@FloatingControlService).apply {
                text = " ▾"
                textSize = 14f
                setTextColor(Color.parseColor("#FFCC00"))
                paint.isFakeBoldText = true
            }
            addView(arrowTv)
        }
    }

    /**
     * Expanded Glassmorphic HUD Panel
     */
    private fun buildExpandedPanelView(): LinearLayout {
        val panelBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(18f)
            setColor(Color.parseColor("#FA141722"))
            setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#FFCC00"))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = panelBackground
            setPadding(dpToPx(16f).toInt(), dpToPx(14f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
            elevation = dpToPx(16f)
            tag = "expanded_panel"
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(290f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f).toInt()
            }
        }

        // Header Row: Title & Collapse button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "⚔️ AUTO-SPLASHER HUD"
            textSize = 13f
            setTextColor(Color.parseColor("#FFCC00"))
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#A0A8B8"))
            setPadding(dpToPx(10f).toInt(), 0, 0, 0)
            setOnClickListener {
                toggleExpandCollapse(false)
            }
        }
        header.addView(closeBtn)
        panel.addView(header)

        // Status & Speed Badge
        val statusTv = TextView(this).apply {
            tag = "expanded_status"
            text = "Status: IDLE | 0.0/s"
            textSize = 12f
            setTextColor(Color.parseColor("#FF5252"))
            paint.isFakeBoldText = true
            setPadding(0, dpToPx(6f).toInt(), 0, dpToPx(8f).toInt())
        }
        panel.addView(statusTv)

        // Big Primary Toggle Button (START / STOP)
        val toggleBtn = Button(this).apply {
            tag = "expanded_toggle_btn"
            text = "⚡ START AUTO-SPLASHER"
            textSize = 13.5f
            setTextColor(Color.BLACK)
            paint.isFakeBoldText = true
            val btnBg = GradientDrawable().apply {
                cornerRadius = dpToPx(10f)
                setColor(Color.parseColor("#00E676"))
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(44f).toInt()
            )
            setOnClickListener {
                val current = BotStateController.botRunState.value
                val touch = AutoTouchService.instance
                if (current == BotRunState.SLASHING) {
                    touch?.stopAutoSlash()
                } else {
                    if (touch != null) {
                        touch.startAutoSlash()
                    } else {
                        BotStateController.addLog("Accessibility service not connected!")
                    }
                }
            }
        }
        panel.addView(toggleBtn)

        // Patterns Header
        val patternTitle = TextView(this).apply {
            text = "PATTERNS (SAFE Y: 12%-55%)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#C4C8D4"))
            paint.isFakeBoldText = true
            setPadding(0, dpToPx(10f).toInt(), 0, dpToPx(4f).toInt())
        }
        panel.addView(patternTitle)

        // Horizontal Scrollable Chip Group for 5 Patterns
        val scrollChips = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        SlashPattern.entries.forEach { pattern ->
            val chip = TextView(this@FloatingControlService).apply {
                text = pattern.shortName
                textSize = 11f
                setPadding(dpToPx(10f).toInt(), dpToPx(6f).toInt(), dpToPx(10f).toInt(), dpToPx(6f).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6f).toInt()
                }
                updateChipStyle(this, pattern == BotStateController.config.value.pattern)
                setOnClickListener {
                    val currentCfg = BotStateController.config.value
                    BotStateController.updateConfig(currentCfg.copy(pattern = pattern))
                    BotStateController.addLog("Active pattern: ${pattern.label}")
                    updateAllChips()
                }
            }
            patternButtons[pattern] = chip
            chipsRow.addView(chip)
        }
        scrollChips.addView(chipsRow)
        panel.addView(scrollChips)

        // Speed Sliders Section
        val delayLabel = TextView(this).apply {
            tag = "delay_label"
            text = "Delay Between Swipes: ${BotStateController.config.value.delayBetweenSwipesMs}ms"
            textSize = 10.5f
            setTextColor(Color.parseColor("#00E5FF"))
            paint.isFakeBoldText = true
            setPadding(0, dpToPx(8f).toInt(), 0, 0)
        }
        panel.addView(delayLabel)

        val delaySeekBar = SeekBar(this).apply {
            tag = "delay_seekbar"
            max = 90 // 10ms to 100ms
            progress = (BotStateController.config.value.delayBetweenSwipesMs - 10).toInt().coerceIn(0, 90)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val newDelay = (progress + 10).toLong()
                        delayLabel.text = "Delay Between Swipes: ${newDelay}ms"
                        val cfg = BotStateController.config.value
                        BotStateController.updateConfig(cfg.copy(delayBetweenSwipesMs = newDelay))
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(delaySeekBar)

        // Swipe Duration Slider
        val durationLabel = TextView(this).apply {
            tag = "duration_label"
            text = "Blade Duration: ${BotStateController.config.value.swipeDurationMs}ms"
            textSize = 10.5f
            setTextColor(Color.parseColor("#FFCC00"))
            paint.isFakeBoldText = true
        }
        panel.addView(durationLabel)

        val durationSeekBar = SeekBar(this).apply {
            tag = "duration_seekbar"
            max = 60 // 20ms to 80ms
            progress = (BotStateController.config.value.swipeDurationMs - 20).toInt().coerceIn(0, 60)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val newDur = (progress + 20).toLong()
                        durationLabel.text = "Blade Duration: ${newDur}ms"
                        val cfg = BotStateController.config.value
                        BotStateController.updateConfig(cfg.copy(swipeDurationMs = newDur))
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        panel.addView(durationSeekBar)

        // Bottom Dismiss / Full Dashboard Button
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(6f).toInt(), 0, 0)
        }

        val openAppBtn = TextView(this).apply {
            text = "📱 Open App"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FFCC00"))
            paint.isFakeBoldText = true
            setPadding(dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(8f).toInt(), dpToPx(4f).toInt())
            setOnClickListener {
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(appIntent)
            }
        }
        footerRow.addView(openAppBtn)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        footerRow.addView(spacer)

        val exitOverlayBtn = TextView(this).apply {
            text = "🛑 Exit HUD"
            textSize = 11.5f
            setTextColor(Color.parseColor("#FF5252"))
            paint.isFakeBoldText = true
            setPadding(dpToPx(8f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt(), dpToPx(4f).toInt())
            setOnClickListener {
                AutoTouchService.instance?.stopAutoSlash()
                stopSelf()
            }
        }
        footerRow.addView(exitOverlayBtn)
        panel.addView(footerRow)

        return panel
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12f)
            if (isSelected) {
                setColor(Color.parseColor("#332B00"))
                setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#FFCC00"))
            } else {
                setColor(Color.parseColor("#222733"))
                setStroke(dpToPx(1f).toInt(), Color.parseColor("#3A4254"))
            }
        }
        chip.background = bg
        chip.setTextColor(if (isSelected) Color.parseColor("#FFCC00") else Color.parseColor("#C4C8D4"))
        chip.paint.isFakeBoldText = isSelected
    }

    private fun updateAllChips() {
        val currentPattern = BotStateController.config.value.pattern
        patternButtons.forEach { (pattern, chip) ->
            updateChipStyle(chip, pattern == currentPattern)
        }
    }

    private fun toggleExpandCollapse(forceExpand: Boolean? = null) {
        isExpanded = forceExpand ?: !isExpanded
        if (isExpanded) {
            expandedPanelView?.visibility = View.VISIBLE
            collapsedFabView?.findViewWithTag<TextView>("fab_icon")?.text = "▼"
        } else {
            expandedPanelView?.visibility = View.GONE
            val isSlashing = BotStateController.botRunState.value == BotRunState.SLASHING
            collapsedFabView?.findViewWithTag<TextView>("fab_icon")?.text = if (isSlashing) "⚔️" else "⚡"
        }
        updateAllChips()
    }

    private fun setupDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null || layoutParams == null) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return false // Let children handle click events if not dragged
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) {
                            isDragging = true
                            layoutParams!!.x = initialX + dx
                            layoutParams!!.y = initialY + dy
                            windowManager?.updateViewLayout(rootContainer, layoutParams)
                            return true
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Tap on collapsed FAB toggles expand/collapse
                            val hitCollapsed = (event.rawY - initialTouchY) < dpToPx(60f)
                            if (hitCollapsed) {
                                toggleExpandCollapse()
                                return true
                            }
                        }
                    }
                }
                return false
            }
        })
    }

    private fun observeBotState() {
        stateObserverJob = serviceScope.launch {
            // Collect run state
            launch {
                BotStateController.botRunState.collectLatest { state ->
                    val isSlashing = state == BotRunState.SLASHING

                    // Update Collapsed FAB stroke and icon
                    val fabBg = collapsedFabView?.background as? GradientDrawable
                    fabBg?.setStroke(
                        dpToPx(2f).toInt(),
                        if (isSlashing) Color.parseColor("#00E676") else Color.parseColor("#FF5252")
                    )
                    collapsedFabView?.findViewWithTag<TextView>("fab_icon")?.text =
                        if (isExpanded) "▼" else (if (isSlashing) "⚔️" else "⚡")

                    // Update Expanded Panel Toggle Button
                    val toggleBtn = expandedPanelView?.findViewWithTag<Button>("expanded_toggle_btn")
                    val btnBg = toggleBtn?.background as? GradientDrawable

                    if (isSlashing) {
                        toggleBtn?.text = "🛑 STOP AUTO-SPLASHER"
                        toggleBtn?.setTextColor(Color.WHITE)
                        btnBg?.setColor(Color.parseColor("#FF3333"))
                    } else {
                        toggleBtn?.text = "⚡ START AUTO-SPLASHER"
                        toggleBtn?.setTextColor(Color.BLACK)
                        btnBg?.setColor(Color.parseColor("#00E676"))
                    }

                    updateStatusTexts()
                }
            }

            // Collect slash count & SPS
            launch {
                BotStateController.slashCount.collectLatest { count ->
                    val sps = BotStateController.slashesPerSecond.value
                    collapsedFabView?.findViewWithTag<TextView>("fab_stats")?.text = "$count Slashes"
                    updateStatusTexts()
                }
            }

            launch {
                BotStateController.slashesPerSecond.collectLatest { sps ->
                    updateStatusTexts()
                }
            }

            launch {
                BotStateController.config.collectLatest {
                    updateAllChips()
                }
            }
        }
    }

    private fun updateStatusTexts() {
        val state = BotStateController.botRunState.value
        val sps = BotStateController.slashesPerSecond.value
        val isSlashing = state == BotRunState.SLASHING
        val statusTv = expandedPanelView?.findViewWithTag<TextView>("expanded_status")

        if (isSlashing) {
            statusTv?.text = "Status: SLASHING | ${String.format(java.util.Locale.US, "%.1f", sps)}/s"
            statusTv?.setTextColor(Color.parseColor("#00E676"))
        } else {
            statusTv?.text = "Status: IDLE | 0.0/s"
            statusTv?.setTextColor(Color.parseColor("#FF5252"))
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDestroy() {
        isRunning = false
        stateObserverJob?.cancel()
        BotStateController.setFloatingOverlayActive(false)
        BotStateController.addLog("Floating HUD overlay closed.")

        rootContainer?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        rootContainer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
