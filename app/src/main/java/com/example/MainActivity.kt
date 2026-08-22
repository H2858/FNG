package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.service.AutoTouchService
import com.example.service.BotConfig
import com.example.service.BotRunState
import com.example.service.BotStateController
import com.example.service.ColorDetector
import com.example.service.FloatingControlService
import com.example.service.FruitTarget
import com.example.service.ScreenCaptureService
import com.example.service.SliceMode
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NinjaAmber
import com.example.ui.theme.NinjaCrimson
import com.example.ui.theme.NinjaCyan
import com.example.ui.theme.NinjaGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainDashboardScreen(
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestAccessibilitySettings = { openAccessibilitySettings() },
                    onStartScreenCapture = { launchScreenCaptureIntent() },
                    onToggleFloatingOverlay = { toggleFloatingService() },
                    onStopScreenCapture = { stopScreenCapture() }
                )
            }
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA_INTENT, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            BotStateController.addLog("MediaProjection permission approved.")
        } else {
            BotStateController.addLog("MediaProjection permission declined by user.")
            Toast.makeText(this, "Screen capture permission is required for vision detection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Toast.makeText(this, "Enable 'Fruit Ninja Auto Slicer' in Accessibility Services", Toast.LENGTH_LONG).show()
    }

    private fun launchScreenCaptureIntent() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenCapture() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        startService(intent)
        BotStateController.setScreenCaptureActive(false)
    }

    private fun toggleFloatingService() {
        if (FloatingControlService.isOverlayRunning()) {
            val intent = Intent(this, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_STOP_FLOATING
            }
            startService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return
            }
            val intent = Intent(this, FloatingControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainDashboardScreen(
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilitySettings: () -> Unit,
    onStartScreenCapture: () -> Unit,
    onToggleFloatingOverlay: () -> Unit,
    onStopScreenCapture: () -> Unit
) {
    val context = LocalContext.current
    val runState by BotStateController.botRunState.collectAsState()
    val config by BotStateController.config.collectAsState()
    val sliceCount by BotStateController.sliceCount.collectAsState()
    val detectedCount by BotStateController.detectedCount.collectAsState()
    val adSkipsCount by BotStateController.adSkipsCount.collectAsState()
    val menuClicksCount by BotStateController.menuClicksCount.collectAsState()
    val visionLatency by BotStateController.visionLatencyMs.collectAsState()
    val isFloatingActive by BotStateController.floatingOverlayActive.collectAsState()
    val isCapturingActive by BotStateController.screenCaptureActive.collectAsState()
    val logs by BotStateController.recentLogs.collectAsState()
    val isAccessibilityConnected by AutoTouchService.isServiceConnected.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) BotStateController.addLog("Notification permission granted.")
    }

    // Refresh permission states on interval
    LaunchedEffect(Unit) {
        while (true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasNotificationPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            delay(1500)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Bot Dashboard", "Vision Tuning", "Slice Arena", "Event Logs")

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 700.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            HeaderSection(runState = runState)

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = NinjaCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NinjaCrimson,
                        height = 3.dp
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color.White else TextSecondary
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> DashboardTab(
                        hasOverlayPermission = hasOverlayPermission,
                        isAccessibilityConnected = isAccessibilityConnected,
                        isCapturingActive = isCapturingActive,
                        hasNotificationPermission = hasNotificationPermission,
                        isFloatingActive = isFloatingActive,
                        runState = runState,
                        sliceCount = sliceCount,
                        detectedCount = detectedCount,
                        adSkipsCount = adSkipsCount,
                        menuClicksCount = menuClicksCount,
                        visionLatency = visionLatency,
                        config = config,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onRequestAccessibilitySettings = onRequestAccessibilitySettings,
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onStartScreenCapture = onStartScreenCapture,
                        onStopScreenCapture = onStopScreenCapture,
                        onToggleFloatingOverlay = onToggleFloatingOverlay,
                        onToggleBotRun = {
                            if (runState == BotRunState.RUNNING) {
                                BotStateController.setRunState(BotRunState.PAUSED)
                            } else {
                                BotStateController.setRunState(BotRunState.RUNNING)
                            }
                        },
                        onResetStats = { BotStateController.resetStats() }
                    )
                    1 -> VisionTuningTab(config = config, onUpdateConfig = { BotStateController.updateConfig(it) })
                    2 -> PlaygroundArenaTab(config = config)
                    3 -> LogsTab(logs = logs, onClear = { BotStateController.addLog("Logs refreshed.") })
                }
            }
        }
    }
}

@Composable
fun HeaderSection(runState: BotRunState) {
    val stateColor by animateColorAsState(
        targetValue = when (runState) {
            BotRunState.RUNNING -> NinjaGreen
            BotRunState.PAUSED -> NinjaAmber
            BotRunState.STOPPED -> NinjaCrimson
        },
        label = "statusColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1015), DarkBackground)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(NinjaCrimson, Color(0xFF550818))
                        ),
                        CircleShape
                    )
                    .border(1.5.dp, NinjaCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Blade Icon",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "FRUIT NINJA BOT",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Vision AI & Gesture Automation Engine",
                    color = NinjaCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Live Badge
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = stateColor.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, stateColor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(stateColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = runState.name,
                    color = stateColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    hasOverlayPermission: Boolean,
    isAccessibilityConnected: Boolean,
    isCapturingActive: Boolean,
    hasNotificationPermission: Boolean,
    isFloatingActive: Boolean,
    runState: BotRunState,
    sliceCount: Int,
    detectedCount: Int,
    adSkipsCount: Int,
    menuClicksCount: Int,
    visionLatency: Long,
    config: BotConfig,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibilitySettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onToggleFloatingOverlay: () -> Unit,
    onToggleBotRun: () -> Unit,
    onResetStats: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Automation Control Card
        item {
            MasterControlCard(
                runState = runState,
                isAccessibilityConnected = isAccessibilityConnected,
                isCapturingActive = isCapturingActive,
                sliceMode = config.sliceMode,
                onToggleBotRun = onToggleBotRun
            )
        }

        // Live Telemetry Stats
        item {
            TelemetryGrid(
                sliceCount = sliceCount,
                detectedCount = detectedCount,
                adSkipsCount = adSkipsCount,
                menuClicksCount = menuClicksCount,
                visionLatency = visionLatency,
                fps = config.targetFps,
                onReset = onResetStats
            )
        }

        // System Permissions & Readiness Checklist
        item {
            Text(
                text = "SYSTEM PERMISSIONS & SERVICES",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )
        }

        item {
            PermissionCheckItem(
                title = "SYSTEM_ALERT_WINDOW (Overlay)",
                description = "Required to render draggable floating HUD pill on top of Fruit Ninja",
                icon = Icons.Default.PictureInPicture,
                isGranted = hasOverlayPermission,
                actionLabel = "Grant Overlay",
                onClick = onRequestOverlayPermission,
                testTag = "overlay_perm_btn"
            )
        }

        item {
            PermissionCheckItem(
                title = "Accessibility Gesture Slicer",
                description = "Required to trigger programmatic swipes, slicing strokes, and ad clicks",
                icon = Icons.Default.Accessibility,
                isGranted = isAccessibilityConnected,
                actionLabel = "Enable Service",
                onClick = onRequestAccessibilitySettings,
                testTag = "accessibility_perm_btn"
            )
        }

        item {
            PermissionCheckItem(
                title = "MediaProjection (Screen Capture)",
                description = "Required for real-time fruit & bomb color detection and trajectory parsing",
                icon = Icons.Default.ScreenShare,
                isGranted = isCapturingActive,
                actionLabel = if (isCapturingActive) "Active (Tap to Stop)" else "Start Capture",
                onClick = if (isCapturingActive) onStopScreenCapture else onStartScreenCapture,
                testTag = "screen_capture_btn"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                PermissionCheckItem(
                    title = "POST_NOTIFICATIONS",
                    description = "Keeps Foreground Service controllers alive without system kills",
                    icon = Icons.Default.Notifications,
                    isGranted = hasNotificationPermission,
                    actionLabel = "Grant Permission",
                    onClick = onRequestNotificationPermission,
                    testTag = "notification_perm_btn"
                )
            }
        }

        // Floating Overlay Controller Switch
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = null,
                                tint = NinjaCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Floating HUD Controller",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isFloatingActive) "Overlay is hovering on screen. Drag to move or tap to expand." else "Launch the draggable floating button to control the bot inside Fruit Ninja.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onToggleFloatingOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFloatingActive) NinjaCrimson else NinjaCyan,
                            contentColor = if (isFloatingActive) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("toggle_floating_overlay_button")
                    ) {
                        Text(
                            text = if (isFloatingActive) "Stop" else "Launch",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MasterControlCard(
    runState: BotRunState,
    isAccessibilityConnected: Boolean,
    isCapturingActive: Boolean,
    sliceMode: SliceMode,
    onToggleBotRun: () -> Unit
) {
    val isRunning = runState == BotRunState.RUNNING
    val buttonColor = if (isRunning) NinjaAmber else NinjaGreen

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isRunning) NinjaGreen.copy(alpha = 0.6f) else DarkBorder
        ),
        modifier = Modifier.shadow(6.dp, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AUTOPILOT ENGINE",
                        color = NinjaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isRunning) "Auto Slicing Active" else "Ready to Slice",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF141923),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Text(
                        text = sliceMode.label,
                        color = NinjaAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isAccessibilityConnected || !isCapturingActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FFB800), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = NinjaAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isAccessibilityConnected && !isCapturingActive)
                            "Please enable Accessibility Service & Screen Capture below"
                        else if (!isAccessibilityConnected)
                            "Enable Accessibility Service to perform slice gestures"
                        else
                            "Start Screen Capture to feed frame detection",
                        color = Color(0xFFFFE082),
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Button(
                onClick = onToggleBotRun,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("master_run_toggle_button")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "PAUSE AUTOMATION" else "START AUTO-SLICER",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun TelemetryGrid(
    sliceCount: Int,
    detectedCount: Int,
    adSkipsCount: Int,
    menuClicksCount: Int,
    visionLatency: Long,
    fps: Int,
    onReset: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Slices Executed Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SLICES HIT",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$sliceCount",
                        color = NinjaGreen,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Detected Targets
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "TARGETS DETECTED",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$detectedCount",
                        color = NinjaCyan,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Ad Skips
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AD SKIPS",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$adSkipsCount",
                        color = NinjaAmber,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Vision Latency
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "VISION LATENCY",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${visionLatency} ms",
                        color = Color(0xFF58A6FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Target FPS
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "SAMPLING RATE",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${fps} FPS",
                        color = NinjaAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCheckItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF131B19) else DarkSurface
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isGranted) NinjaGreen.copy(alpha = 0.5f) else DarkBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isGranted) NinjaGreen.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (isGranted) NinjaGreen else NinjaCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) Color(0xFF1F2B26) else NinjaCrimson,
                    contentColor = if (isGranted) NinjaGreen else Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(
                    text = if (isGranted) "Granted" else actionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionTuningTab(
    config: BotConfig,
    onUpdateConfig: (BotConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SLICING PATTERN MODE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(SliceMode.values()) { mode ->
            val isSelected = config.sliceMode == mode
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF221A2E) else DarkSurface
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isSelected) NinjaCyan else DarkBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUpdateConfig(config.copy(sliceMode = mode)) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                if (isSelected) NinjaCyan else Color.Transparent,
                                CircleShape
                            )
                            .border(2.dp, if (isSelected) NinjaCyan else TextTertiary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = mode.description,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Auto-Skip Ads & Dialogs Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AdsClick, contentDescription = null, tint = NinjaCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Auto-Skip Ads & Close ('X')", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Scans top/bottom screen corners and clicks 'X' or 'Skip' prompts automatically",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = config.autoSkipAds,
                        onCheckedChange = { onUpdateConfig(config.copy(autoSkipAds = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NinjaCyan,
                            checkedTrackColor = NinjaCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // Auto-Start Game Menu Watermelon Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.PlayCircle, contentDescription = null, tint = NinjaAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Auto-Start Menu Play Fruit", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Detects play watermelon in lobby and performs auto-slash to start game instantly",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = config.autoStartGame,
                        onCheckedChange = { onUpdateConfig(config.copy(autoStartGame = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NinjaAmber,
                            checkedTrackColor = NinjaAmber.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // Slicing Speed / Duration Slider
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NinjaAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Slash Gesture Duration", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "${config.sliceDurationMs} ms",
                            color = NinjaAmber,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lower duration creates razor-fast slashes; higher duration ensures smooth recognition on older devices.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = config.sliceDurationMs.toFloat(),
                        onValueChange = { onUpdateConfig(config.copy(sliceDurationMs = it.toLong())) },
                        valueRange = 30f..200f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = NinjaAmber,
                            activeTrackColor = NinjaAmber,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // Bomb Avoidance Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NinjaGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(text = "Bomb Avoidance Safety Shield", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Detects dark metallic spheres & automatically avoids cuts intersecting bomb radii",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Switch(
                        checked = config.bombAvoidance,
                        onCheckedChange = { onUpdateConfig(config.copy(bombAvoidance = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NinjaGreen,
                            checkedTrackColor = NinjaGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextTertiary,
                            uncheckedTrackColor = DarkBorder
                        )
                    )
                }
            }
        }

        // Color Presets Filter
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Target Fruit Color Profiles", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Select which fruit colors the ColorDetector scans for slicing",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val allColors = listOf(
                        "Red" to "Watermelon / Apple (Red)",
                        "Yellow" to "Banana / Lemon (Yellow)",
                        "Orange" to "Orange / Peach",
                        "Green" to "Lime / Kiwi (Green)",
                        "Magenta" to "Dragonfruit (Magenta)"
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allColors.forEach { (colorKey, label) ->
                            val isActive = config.activeFruitColors.contains(colorKey)
                            FilterChip(
                                selected = isActive,
                                onClick = {
                                    val newSet = config.activeFruitColors.toMutableSet()
                                    if (isActive) newSet.remove(colorKey) else newSet.add(colorKey)
                                    onUpdateConfig(config.copy(activeFruitColors = newSet))
                                },
                                label = { Text(text = label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NinjaCrimson.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White,
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }

        // Engine FPS Limiter
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Vision Sampling Frame Rate", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${config.targetFps} FPS",
                            color = NinjaCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                    Slider(
                        value = config.targetFps.toFloat(),
                        onValueChange = { onUpdateConfig(config.copy(targetFps = it.toInt())) },
                        valueRange = 15f..60f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = NinjaCyan,
                            activeTrackColor = NinjaCyan,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun PlaygroundArenaTab(config: BotConfig) {
    var swipePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val targets = remember { mutableStateListOf<ColorDetector.DetectedObject>() }
    var score by remember { mutableIntStateOf(0) }
    var autoSliceActive by remember { mutableStateOf(false) }

    // Spawn fruit and UI targets periodically in Arena
    LaunchedEffect(Unit) {
        while (true) {
            if (targets.size < 6) {
                val rand = Random.nextFloat()
                val target = when {
                    rand < 0.15f -> ColorDetector.DetectedObject(
                        x = Random.nextFloat() * 600f + 100f,
                        y = Random.nextFloat() * 600f + 150f,
                        radius = 45f,
                        label = "Bomb",
                        type = ColorDetector.TargetType.BOMB
                    )
                    rand < 0.25f -> ColorDetector.DetectedObject(
                        x = 400f,
                        y = 700f,
                        radius = 60f,
                        label = "Menu Play Fruit",
                        type = ColorDetector.TargetType.MENU_PLAY_BUTTON
                    )
                    rand < 0.35f -> ColorDetector.DetectedObject(
                        x = 650f,
                        y = 120f,
                        radius = 32f,
                        label = "Ad Close (X)",
                        type = ColorDetector.TargetType.AD_SKIP_BUTTON
                    )
                    else -> {
                        val color = listOf("Red", "Orange", "Yellow", "Green", "Magenta").random()
                        ColorDetector.DetectedObject(
                            x = Random.nextFloat() * 600f + 100f,
                            y = Random.nextFloat() * 600f + 150f,
                            radius = 38f,
                            label = color,
                            type = ColorDetector.TargetType.FRUIT
                        )
                    }
                }
                targets.add(target)
            }
            delay(1200)
        }
    }

    // Auto Slicer Loop in Arena
    LaunchedEffect(autoSliceActive) {
        while (autoSliceActive) {
            val nonBombs = targets.filter { it.type != ColorDetector.TargetType.BOMB }
            if (nonBombs.isNotEmpty()) {
                val target = nonBombs.random()
                swipePath = listOf(
                    Offset(target.x - 90f, target.y + 90f),
                    Offset(target.x + 90f, target.y - 90f)
                )
                targets.remove(target)
                score++
                BotStateController.incrementSliceCount(1)
            }
            delay(400)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VISION SIMULATOR ARENA",
                    color = NinjaCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Score: $score",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { autoSliceActive = !autoSliceActive },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (autoSliceActive) NinjaAmber else NinjaGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (autoSliceActive) "Pause Sim" else "Simulate Auto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        targets.clear()
                        score = 0
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkSurfaceVariant,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "Clear", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interactive Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF070A0F), RoundedCornerShape(18.dp))
                .border(1.5.dp, DarkBorder, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            swipePath = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cur = change.position
                            swipePath = swipePath + cur

                            // Check collision
                            val toRemove = targets.filter { f ->
                                hypot(f.x - cur.x, f.y - cur.y) < f.radius + 15f
                            }
                            if (toRemove.isNotEmpty()) {
                                toRemove.forEach {
                                    if (it.type != ColorDetector.TargetType.BOMB) score++ else score = kotlin.math.max(0, score - 5)
                                    targets.remove(it)
                                }
                            }
                        },
                        onDragEnd = {
                            swipePath = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw targets
                for (target in targets) {
                    val color = when (target.type) {
                        ColorDetector.TargetType.BOMB -> Color(0xFF1E212B)
                        ColorDetector.TargetType.MENU_PLAY_BUTTON -> Color(0xFF00FF88)
                        ColorDetector.TargetType.AD_SKIP_BUTTON -> Color(0xFF58A6FF)
                        ColorDetector.TargetType.FRUIT -> when (target.label) {
                            "Red" -> NinjaCrimson
                            "Yellow" -> NinjaAmber
                            "Green" -> NinjaGreen
                            "Orange" -> Color(0xFFFF7A00)
                            "Magenta" -> Color(0xFFFF00D4)
                            else -> NinjaCyan
                        }
                    }

                    // Target body
                    drawCircle(
                        color = color,
                        radius = target.radius,
                        center = Offset(target.x, target.y)
                    )

                    // Target outline glow
                    drawCircle(
                        color = if (target.type == ColorDetector.TargetType.BOMB) NinjaCrimson else Color.White,
                        radius = target.radius,
                        center = Offset(target.x, target.y),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Draw swipe trail
                if (swipePath.size >= 2) {
                    val path = Path().apply {
                        moveTo(swipePath.first().x, swipePath.first().y)
                        for (i in 1 until swipePath.size) {
                            lineTo(swipePath[i].x, swipePath[i].y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = NinjaCyan,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun LogsTab(logs: List<String>, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME LOG CONSOLE",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            IconButton(onClick = onClear) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Logs", tint = NinjaCyan)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkSurface, RoundedCornerShape(14.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(logs) { logEntry ->
                Text(
                    text = logEntry,
                    color = if (logEntry.contains("declined") || logEntry.contains("error", ignoreCase = true))
                        NinjaCrimson
                    else if (logEntry.contains("Auto-sliced") || logEntry.contains("approved") || logEntry.contains("connected"))
                        NinjaGreen
                    else
                        TextPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
