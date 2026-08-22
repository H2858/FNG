package com.aistudio.fruitninjabot.fnxbot

import android.Manifest
import android.app.Activity
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
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.aistudio.fruitninjabot.fnxbot.R
import com.aistudio.fruitninjabot.fnxbot.service.*
import com.aistudio.fruitninjabot.fnxbot.ui.theme.FruitNinjaBotTheme
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

data class ArenaItem(
    val id: Long = Random.nextLong(),
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val isBomb: Boolean,
    var isSliced: Boolean = false
)

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            FruitNinjaBotTheme {
                MainScreen(
                    onStartCapture = { launcher ->
                        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                        launcher.launch(captureIntent)
                    },
                    onStopCapture = {
                        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
                            action = ScreenCaptureService.ACTION_STOP
                        }
                        startService(stopIntent)
                    },
                    onToggleOverlay = { enable ->
                        val overlayIntent = Intent(this, FloatingControlService::class.java).apply {
                            action = if (enable) FloatingControlService.ACTION_SHOW else FloatingControlService.ACTION_HIDE
                        }
                        if (enable) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(overlayIntent)
                            } else {
                                startService(overlayIntent)
                            }
                        } else {
                            startService(overlayIntent)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartCapture: (androidx.activity.result.ActivityResultLauncher<Intent>) -> Unit,
    onStopCapture: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val runState by BotStateController.botRunState.collectAsState()
    val isOverlayActive by BotStateController.floatingOverlayActive.collectAsState()
    val isCaptureActive by BotStateController.screenCaptureActive.collectAsState()
    val isAccessibilityActive by AutoTouchService.isServiceConnected.collectAsState()

    val sliceCount by BotStateController.sliceCount.collectAsState()
    val detectedCount by BotStateController.detectedCount.collectAsState()
    val adSkipsCount by BotStateController.adSkipsCount.collectAsState()
    val menuClicksCount by BotStateController.menuClicksCount.collectAsState()
    val latencyMs by BotStateController.visionLatencyMs.collectAsState()
    val config by BotStateController.config.collectAsState()
    val logs by BotStateController.recentLogs.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }

    // Media projection launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val startIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
            BotStateController.setRunState(BotRunState.RUNNING)
            Toast.makeText(context, "Capture & Vision Bot started!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for notifications
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🍉 Fruit Ninja Bot",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (runState) {
                                BotRunState.RUNNING -> Color(0xFF2E7D32)
                                BotRunState.PAUSED -> Color(0xFFE65100)
                                BotRunState.STOPPED -> Color(0xFFC62828)
                            }
                        ) {
                            Text(
                                text = runState.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { BotStateController.resetStats() },
                        modifier = Modifier.testTag("reset_stats_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Statistics")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.SportsEsports, contentDescription = "Arena") },
                    label = { Text("Dojo Arena") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Config") },
                    label = { Text("Config") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Logs") },
                    label = { Text("Logs") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardTab(
                    context = context,
                    hasOverlayPermission = hasOverlayPermission,
                    isAccessibilityActive = isAccessibilityActive,
                    isOverlayActive = isOverlayActive,
                    isCaptureActive = isCaptureActive,
                    runState = runState,
                    sliceCount = sliceCount,
                    detectedCount = detectedCount,
                    adSkipsCount = adSkipsCount,
                    menuClicksCount = menuClicksCount,
                    latencyMs = latencyMs,
                    onStartCapture = { onStartCapture(projectionLauncher) },
                    onStopCapture = onStopCapture,
                    onToggleOverlay = onToggleOverlay,
                    onRefreshPermissions = {
                        hasOverlayPermission = Settings.canDrawOverlays(context)
                    }
                )
                1 -> DojoArenaTab(config = config)
                2 -> ConfigTab(config = config, onConfigChanged = { BotStateController.updateConfig(it) })
                3 -> LogsTab(logs = logs)
            }
        }
    }
}

@Composable
fun DashboardTab(
    context: Context,
    hasOverlayPermission: Boolean,
    isAccessibilityActive: Boolean,
    isOverlayActive: Boolean,
    isCaptureActive: Boolean,
    runState: BotRunState,
    sliceCount: Int,
    detectedCount: Int,
    adSkipsCount: Int,
    menuClicksCount: Int,
    latencyMs: Long,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // System Readiness / Permission Status Banner
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "System Permissions & Services",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                // Overlay Permission Row
                PermissionItemRow(
                    title = "Draw Over Other Apps (Floating HUD)",
                    isGranted = hasOverlayPermission,
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                // Accessibility Service Row
                PermissionItemRow(
                    title = "Accessibility Service (Auto-Touch)",
                    isGranted = isAccessibilityActive,
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                Button(
                    onClick = onRefreshPermissions,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verify Permissions")
                }
            }
        }

        // Live Telemetry Grid
        Text(
            text = "Live Automation Telemetry",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Slices",
                value = "$sliceCount",
                icon = Icons.Default.ContentCut,
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Fruits Seen",
                value = "$detectedCount",
                icon = Icons.Default.Visibility,
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Vision Latency",
                value = "${latencyMs}ms",
                icon = Icons.Default.Speed,
                color = Color(0xFF29B6F6),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Ad Skips / Menus",
                value = "$adSkipsCount / $menuClicksCount",
                icon = Icons.Default.AdsClick,
                color = Color(0xFFAB47BC),
                modifier = Modifier.weight(1f)
            )
        }

        // Engine Master Controls
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Bot Execution Controls",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isCaptureActive) {
                                onStopCapture()
                            } else {
                                onStartCapture()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_capture_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCaptureActive) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                    ) {
                        Icon(
                            imageVector = if (isCaptureActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isCaptureActive) "Stop Vision" else "Start Vision")
                    }

                    OutlinedButton(
                        onClick = {
                            if (!hasOverlayPermission) {
                                Toast.makeText(context, "Please enable Overlay Permission first", Toast.LENGTH_SHORT).show()
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                onToggleOverlay(!isOverlayActive)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_overlay_button")
                    ) {
                        Icon(
                            imageVector = if (isOverlayActive) Icons.Default.LayersClear else Icons.Default.Layers,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isOverlayActive) "Hide HUD" else "Show HUD")
                    }
                }

                // Pause / Resume if capture active
                if (isCaptureActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (runState == BotRunState.RUNNING) {
                                    BotStateController.setRunState(BotRunState.PAUSED)
                                } else {
                                    BotStateController.setRunState(BotRunState.RUNNING)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (runState == BotRunState.RUNNING) Color(0xFFF57C00) else Color(0xFF1976D2)
                            )
                        ) {
                            Icon(
                                imageVector = if (runState == BotRunState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (runState == BotRunState.RUNNING) "Pause Slicing" else "Resume Slicing")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = if (isGranted) "✓ Enabled / Granted" else "✗ Action Required",
                fontSize = 11.sp,
                color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFE53935),
                fontWeight = FontWeight.Bold
            )
        }
        if (!isGranted) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Enable", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DojoArenaTab(config: BotConfig) {
    var items by remember { mutableStateOf(listOf<ArenaItem>()) }
    var slicePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var arenaSlices by remember { mutableIntStateOf(0) }

    // Arena game loop
    LaunchedEffect(Unit) {
        while (true) {
            delay(33L) // ~30 FPS loop

            // Randomly spawn fruits / bombs
            val current = items.toMutableList()
            if (current.size < 6 && Random.nextFloat() < 0.20f) {
                val isBomb = Random.nextFloat() < 0.22f
                val colors = listOf(
                    Color(0xFFE53935), // Red
                    Color(0xFFFFB300), // Yellow
                    Color(0xFF43A047), // Green
                    Color(0xFFFB8C00), // Orange
                    Color(0xFFD81B60)  // Magenta
                )
                current.add(
                    ArenaItem(
                        x = Random.nextFloat() * 600f + 100f,
                        y = 1100f,
                        vx = (Random.nextFloat() - 0.5f) * 12f,
                        vy = -28f - Random.nextFloat() * 12f,
                        color = if (isBomb) Color(0xFF212121) else colors.random(),
                        isBomb = isBomb
                    )
                )
            }

            // Physics step
            val nextList = mutableListOf<ArenaItem>()
            for (item in current) {
                item.x += item.vx
                item.y += item.vy
                item.vy += 0.95f // Gravity
                if (item.y < 1200f && item.x in -100f..900f) {
                    nextList.add(item)
                }
            }
            items = nextList
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dojo Arena Simulator", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Arena Slices: $arenaSlices", fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2D1E12), Color(0xFF1B1109))
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            slicePath = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            val newPoint = change.position
                            slicePath = slicePath + newPoint

                            // Check collision with arena items
                            val updated = items.map { item ->
                                if (!item.isSliced) {
                                    val dist = hypot(item.x - newPoint.x, item.y - newPoint.y)
                                    if (dist < 65f) {
                                        arenaSlices++
                                        BotStateController.incrementSliceCount(1)
                                        item.copy(isSliced = true)
                                    } else {
                                        item
                                    }
                                } else {
                                    item
                                }
                            }
                            items = updated
                        },
                        onDragEnd = {
                            slicePath = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw Fruit & Bomb items
                for (item in items) {
                    if (!item.isSliced) {
                        if (item.isBomb) {
                            // Bomb metallic sphere
                            drawCircle(
                                color = Color(0xFF212121),
                                radius = 32.dp.toPx(),
                                center = Offset(item.x, item.y)
                            )
                            drawCircle(
                                color = Color(0xFFFF5722),
                                radius = 8.dp.toPx(),
                                center = Offset(item.x - 8f, item.y - 8f)
                            )
                        } else {
                            // Juicy fruit
                            drawCircle(
                                color = item.color,
                                radius = 28.dp.toPx(),
                                center = Offset(item.x, item.y)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = 8.dp.toPx(),
                                center = Offset(item.x - 8f, item.y - 8f)
                            )
                        }
                    } else {
                        // Sliced halves
                        drawCircle(
                            color = item.color.copy(alpha = 0.6f),
                            radius = 16.dp.toPx(),
                            center = Offset(item.x - 18f, item.y)
                        )
                        drawCircle(
                            color = item.color.copy(alpha = 0.6f),
                            radius = 16.dp.toPx(),
                            center = Offset(item.x + 18f, item.y)
                        )
                    }
                }

                // Draw Blade Swipe Trail
                if (slicePath.size >= 2) {
                    val p = Path().apply {
                        moveTo(slicePath[0].x, slicePath[0].y)
                        for (i in 1 until slicePath.size) {
                            lineTo(slicePath[i].x, slicePath[i].y)
                        }
                    }
                    drawPath(
                        path = p,
                        color = Color(0xFF00E5FF),
                        style = Stroke(width = 8.dp.toPx())
                    )
                    drawPath(
                        path = p,
                        color = Color.White,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Text(
                text = "Swipe your finger across the wooden dojo to test slice physics & detection!",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun ConfigTab(config: BotConfig, onConfigChanged: (BotConfig) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Slicing Strategy & Modes", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        // Slicing Mode selector
        SliceMode.values().forEach { mode ->
            Card(
                onClick = { onConfigChanged(config.copy(sliceMode = mode)) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (config.sliceMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = config.sliceMode == mode,
                        onClick = { onConfigChanged(config.copy(sliceMode = mode)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(mode.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Automation Features", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        SwitchPreferenceRow(
            title = "Strict Bomb Avoidance",
            subtitle = "Calculates perpendicular clearance vectors to avoid striking bombs",
            checked = config.bombAvoidance,
            onCheckedChange = { onConfigChanged(config.copy(bombAvoidance = it)) }
        )

        SwitchPreferenceRow(
            title = "Auto-Skip Ad Overlays",
            subtitle = "Detects corner close/dismiss 'X' icons and automatically taps them",
            checked = config.autoSkipAds,
            onCheckedChange = { onConfigChanged(config.copy(autoSkipAds = it)) }
        )

        SwitchPreferenceRow(
            title = "Auto-Start Lobby Game",
            subtitle = "Detects the lobby watermelon start icon and slashes it to begin game",
            checked = config.autoStartGame,
            onCheckedChange = { onConfigChanged(config.copy(autoStartGame = it)) }
        )

        HorizontalDivider()

        Text("Performance & Speed", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        // Slice Duration Slider
        Column {
            Text("Slash Gesture Duration: ${config.sliceDurationMs}ms", fontSize = 13.sp)
            Slider(
                value = config.sliceDurationMs.toFloat(),
                onValueChange = { onConfigChanged(config.copy(sliceDurationMs = it.toLong())) },
                valueRange = 30f..150f,
                steps = 12
            )
        }

        // Target FPS Slider
        Column {
            Text("Vision Capture Target Rate: ${config.targetFps} FPS", fontSize = 13.sp)
            Slider(
                value = config.targetFps.toFloat(),
                onValueChange = { onConfigChanged(config.copy(targetFps = it.toInt())) },
                valueRange = 15f..60f,
                steps = 9
            )
        }
    }
}

@Composable
fun SwitchPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LogsTab(logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Real-Time Engine Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("${logs.size} entries", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = when {
                            log.contains("started") || log.contains("connected") || log.contains("Sliced") -> Color(0xFF81C784)
                            log.contains("cancelled") || log.contains("interrupted") || log.contains("stopped") -> Color(0xFFE57373)
                            else -> Color(0xFFE0E0E0)
                        }
                    )
                }
            }
        }
    }
}
