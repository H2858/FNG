package com.aistudio.fruitninjabot.fnxbot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aistudio.fruitninjabot.fnxbot.MainActivity
import com.aistudio.fruitninjabot.fnxbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingControlService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_hud_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP_OVERLAY = "ACTION_STOP_FLOATING_OVERLAY"

        var isRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObserverJob: Job? = null

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
                description = "Controls floating overlay widget for game automation"
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
            .setContentText("Floating HUD is active on screen")
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
            x = 40
            y = 180
        }

        floatingView = buildDynamicHudView()
        windowManager?.addView(floatingView, layoutParams)
    }

    private fun buildDynamicHudView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(android.R.drawable.dialog_holo_dark_frame)
            setPadding(24, 18, 24, 20)
            elevation = 20f
        }

        // Header Row with Title, Drag Handle & Close
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTv = TextView(this).apply {
            text = "⚡ AUTO-SPLASHER"
            textSize = 12.5f
            setTextColor(0xFFFFCC00.toInt())
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 15f
            setTextColor(0xFFCCCCCC.toInt())
            setPadding(16, 4, 12, 4)
            setOnClickListener {
                AutoTouchService.instance?.stopAutoSlash()
                stopSelf()
            }
        }

        headerLayout.addView(titleTv)
        headerLayout.addView(closeBtn)
        container.addView(headerLayout)

        // Status Badge Row
        val statusTv = TextView(this).apply {
            id = View.generateViewId()
            tag = "status_tv"
            text = "Status: IDLE"
            textSize = 13f
            setTextColor(0xFFFF4444.toInt())
            paint.isFakeBoldText = true
            setPadding(0, 8, 0, 4)
        }
        container.addView(statusTv)

        // Telemetry Metrics Row
        val metricsTv = TextView(this).apply {
            id = View.generateViewId()
            tag = "metrics_tv"
            text = "Slashes: 0 | 0.0/s"
            textSize = 11.5f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 10)
        }
        container.addView(metricsTv)

        // Control Buttons Row
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val toggleBtn = Button(this).apply {
            id = View.generateViewId()
            tag = "toggle_btn"
            text = "START"
            textSize = 12f
            setTextColor(0xFF000000.toInt())
            setBackgroundColor(0xFF00E676.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                100,
                1f
            ).apply {
                marginEnd = 8
            }
            setOnClickListener {
                val current = BotStateController.botRunState.value
                val touch = AutoTouchService.instance
                if (current == BotRunState.SLASHING) {
                    touch?.stopAutoSlash()
                } else {
                    if (touch != null) {
                        touch.startAutoSlash()
                    } else {
                        BotStateController.addLog("Enable Accessibility Service first!")
                    }
                }
            }
        }

        val speedBtn = Button(this).apply {
            text = "SPEED"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF333344.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                100
            )
            setOnClickListener {
                val cfg = BotStateController.config.value
                val newDelay = when (cfg.delayBetweenSwipesMs) {
                    in 0..50 -> 75L
                    in 51..85 -> 120L
                    else -> 45L
                }
                BotStateController.updateConfig(cfg.copy(delayBetweenSwipesMs = newDelay))
                BotStateController.addLog("Slash speed adjusted: ${newDelay}ms delay")
            }
        }

        buttonsLayout.addView(toggleBtn)
        buttonsLayout.addView(speedBtn)
        container.addView(buttonsLayout)

        // Drag listener for moving the floating overlay smoothly across the screen
        setupDragListener(container)

        return container
    }

    private fun setupDragListener(view: View) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null || layoutParams == null) return false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return false // Let children handle click events if not dragged
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                            layoutParams!!.x = initialX + dx
                            layoutParams!!.y = initialY + dy
                            windowManager?.updateViewLayout(floatingView, layoutParams)
                            return true
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        return !isClick
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
                    val statusTv = floatingView?.findViewWithTag<TextView>("status_tv")
                    val toggleBtn = floatingView?.findViewWithTag<Button>("toggle_btn")

                    if (state == BotRunState.SLASHING) {
                        statusTv?.text = "Status: SLASHING"
                        statusTv?.setTextColor(0xFF00FF7F.toInt()) // Vibrant Green
                        toggleBtn?.text = "STOP"
                        toggleBtn?.setBackgroundColor(0xFFFF3333.toInt())
                    } else {
                        statusTv?.text = "Status: IDLE"
                        statusTv?.setTextColor(0xFFFF4444.toInt()) // Vibrant Red
                        toggleBtn?.text = "START"
                        toggleBtn?.setBackgroundColor(0xFF00E676.toInt())
                    }
                }
            }

            // Collect slashes and speed
            launch {
                BotStateController.slashCount.collectLatest { slashes ->
                    val sps = BotStateController.slashesPerSecond.value
                    val metricsTv = floatingView?.findViewWithTag<TextView>("metrics_tv")
                    metricsTv?.text = "Slashes: $slashes | ${String.format(java.util.Locale.US, "%.1f", sps)}/s"
                }
            }

            launch {
                BotStateController.slashesPerSecond.collectLatest { sps ->
                    val slashes = BotStateController.slashCount.value
                    val metricsTv = floatingView?.findViewWithTag<TextView>("metrics_tv")
                    metricsTv?.text = "Slashes: $slashes | ${String.format(java.util.Locale.US, "%.1f", sps)}/s"
                }
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        stateObserverJob?.cancel()
        BotStateController.setFloatingOverlayActive(false)
        BotStateController.addLog("Floating HUD overlay closed.")

        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        floatingView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
