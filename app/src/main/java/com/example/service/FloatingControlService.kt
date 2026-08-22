package com.example.service

import android.annotation.SuppressLint
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
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class FloatingControlService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_STOP_FLOATING = "action_stop_floating"
        private const val CHANNEL_ID = "channel_floating_control"
        private const val NOTIF_ID = 3003

        private var _instance: FloatingControlService? = null
        val instance: FloatingControlService?
            get() = _instance

        fun isOverlayRunning(): Boolean = _instance != null
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    // Lifecycle & SavedState for ComposeView
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        _instance = this
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        startForegroundWithNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initFloatingOverlay()

        BotStateController.setFloatingOverlayActive(true)
        BotStateController.addLog("Floating overlay controller opened.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_FLOATING) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @SuppressLint("RtlHardcoded")
    private fun initFloatingOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 40
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(this@FloatingControlService)
            setViewTreeViewModelStoreOwner(this@FloatingControlService)
            setViewTreeSavedStateRegistryOwner(this@FloatingControlService)

            setContent {
                FloatingOverlayUi(
                    onDrag = { dx, dy ->
                        params?.let { p ->
                            p.x = (p.x + dx.toInt()).coerceAtLeast(0)
                            p.y = (p.y + dy.toInt()).coerceAtLeast(50)
                            windowManager.updateViewLayout(this@apply, p)
                        }
                    },
                    onClose = {
                        stopSelf()
                    },
                    onOpenApp = {
                        val appIntent = Intent(this@FloatingControlService, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(appIntent)
                    }
                )
            }
        }

        floatingView = composeView
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            BotStateController.addLog("Failed to add floating window: ${e.message}")
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingControlService::class.java).apply {
            action = ACTION_STOP_FLOATING
        }
        val pendingStop = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fruit Ninja Floating Controller")
            .setContentText("Overlay button active. Tap or drag on screen.")
            .setSmallIcon(R.drawable.fruit_ninja_bot_icon_1787345790782)
            .setContentIntent(pendingOpen)
            .addAction(R.drawable.fruit_ninja_bot_icon_1787345790782, "Close Overlay", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Overlay Controller",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating action button controls for Fruit Ninja Bot"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()

        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        floatingView = null

        if (_instance == this) {
            _instance = null
        }
        BotStateController.setFloatingOverlayActive(false)
        BotStateController.addLog("Floating overlay controller closed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingOverlayUi(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClose: () -> Unit,
    onOpenApp: () -> Unit
) {
    val runState by BotStateController.botRunState.collectAsState()
    val sliceCount by BotStateController.sliceCount.collectAsState()
    val detectedCount by BotStateController.detectedCount.collectAsState()
    val adSkipsCount by BotStateController.adSkipsCount.collectAsState()
    val visionLatency by BotStateController.visionLatencyMs.collectAsState()
    val isCapturing by BotStateController.screenCaptureActive.collectAsState()
    val config by BotStateController.config.collectAsState()

    var isExpanded by remember { mutableStateOf(false) }

    val statusColor = when (runState) {
        BotRunState.RUNNING -> Color(0xFF00FF88) // Neon Green
        BotRunState.PAUSED -> Color(0xFFFFB800)  // Amber
        BotRunState.STOPPED -> Color(0xFFFF2D55) // Crimson
    }

    val isRunning = runState == BotRunState.RUNNING

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        // Floating circular primary pill
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xEE161B22),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(statusColor, statusColor.copy(alpha = 0.6f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .clip(RoundedCornerShape(28.dp))
                .clickable {
                    isExpanded = !isExpanded
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Glowing indicator dot
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(statusColor, CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Short status label
                Text(
                    text = if (isRunning) "BOT: ON ($sliceCount)" else if (runState == BotRunState.PAUSED) "PAUSED" else "BOT: OFF",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Quick Play/Pause button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(statusColor.copy(alpha = 0.25f), CircleShape)
                        .clickable {
                            if (isRunning) {
                                BotStateController.setRunState(BotRunState.PAUSED)
                            } else {
                                BotStateController.setRunState(BotRunState.RUNNING)
                            }
                        }
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Run",
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Expanded Action Drawer
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xF010141C),
                tonalElevation = 12.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(240.dp)
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vision Automation HUD",
                            color = Color(0xFF00F0FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${visionLatency}ms",
                            color = Color(0xFFFFB800),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stats breakdown row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B2230),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Hits", color = Color(0xFF8B949E), fontSize = 9.sp)
                                Text("$sliceCount", color = Color(0xFF00FF88), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B2230),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Seen", color = Color(0xFF8B949E), fontSize = 9.sp)
                                Text("$detectedCount", color = Color(0xFF00F0FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B2230),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Skips", color = Color(0xFF8B949E), fontSize = 9.sp)
                                Text("$adSkipsCount", color = Color(0xFFFFB800), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mode Switcher row
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B2230))
                            .clickable {
                                val nextMode = when (config.sliceMode) {
                                    SliceMode.INSTANT_SLASH -> SliceMode.COMBO_SLASH
                                    SliceMode.COMBO_SLASH -> SliceMode.BOMB_SAFE_SLASH
                                    SliceMode.BOMB_SAFE_SLASH -> SliceMode.MULTI_SWEEP
                                    SliceMode.MULTI_SWEEP -> SliceMode.INSTANT_SLASH
                                }
                                BotStateController.updateConfig(config.copy(sliceMode = nextMode))
                                BotStateController.addLog("Mode set to: ${nextMode.label}")
                            }
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Mode",
                                tint = Color(0xFFFFB800),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = config.sliceMode.label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Next >",
                            color = Color(0xFF8B949E),
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Auto-Skip Ads Quick Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161D29))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-Skip Ads / X",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Switch(
                            checked = config.autoSkipAds,
                            onCheckedChange = {
                                BotStateController.updateConfig(config.copy(autoSkipAds = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00FF88),
                                checkedTrackColor = Color(0xFF00FF88).copy(alpha = 0.3f),
                                uncheckedThumbColor = Color(0xFF6E7681),
                                uncheckedTrackColor = Color(0xFF21262D)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Gesture Test Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Quick Slice Test button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF238636))
                                .clickable {
                                    AutoTouchService.instance?.let { service ->
                                        val display = service.resources.displayMetrics
                                        val cx = display.widthPixels / 2f
                                        val cy = display.heightPixels / 2f
                                        service.performSwipe(cx - 200f, cy + 200f, cx + 200f, cy - 200f, 60L)
                                        BotStateController.addLog("Triggered test slash from overlay")
                                    } ?: run {
                                        BotStateController.addLog("Accessibility service not connected!")
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = "Test Slash",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Slash",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Quick Click/Tap Test button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F6FEB))
                                .clickable {
                                    AutoTouchService.instance?.let { service ->
                                        val display = service.resources.displayMetrics
                                        service.performClick(display.widthPixels / 2f, display.heightPixels / 2f)
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Test Tap",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tap",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Row (Open App + Close Overlay)
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onOpenApp() }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Open App",
                                tint = Color(0xFF58A6FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "App",
                                color = Color(0xFF58A6FF),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onClose() }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Overlay",
                                tint = Color(0xFFF85149),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Close",
                                color = Color(0xFFF85149),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
