package com.aistudio.fruitninjabot.fnxbot

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.fruitninjabot.fnxbot.R
import com.aistudio.fruitninjabot.fnxbot.service.AutoTouchService
import com.aistudio.fruitninjabot.fnxbot.service.BotConfig
import com.aistudio.fruitninjabot.fnxbot.service.BotRunState
import com.aistudio.fruitninjabot.fnxbot.service.BotStateController
import com.aistudio.fruitninjabot.fnxbot.service.FloatingControlService
import com.aistudio.fruitninjabot.fnxbot.service.SlashPattern
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// High-contrast arcade night theme
private val NinjaDarkColors = darkColorScheme(
    primary = Color(0xFFFFCC00),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF332B00),
    onPrimaryContainer = Color(0xFFFFE57F),
    secondary = Color(0xFF00E676),
    onSecondary = Color(0xFF003817),
    background = Color(0xFF101216),
    surface = Color(0xFF181B22),
    surfaceVariant = Color(0xFF222733),
    onSurface = Color(0xFFF0F2F8),
    onSurfaceVariant = Color(0xFFC4C8D4),
    error = Color(0xFFFF5252),
    onError = Color(0xFF370000)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = NinjaDarkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FruitNinjaAutoSplasherApp()
                }
            }
        }
    }
}

enum class NavigationTab(val title: String) {
    DASHBOARD("Dashboard"),
    ARENA("Dojo Arena"),
    CONFIG("Tuning"),
    LOGS("Live Logs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FruitNinjaAutoSplasherApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    val isAccessibilityConnected by AutoTouchService.isServiceConnected.collectAsState()
    val botRunState by BotStateController.botRunState.collectAsState()
    val isOverlayActive by BotStateController.floatingOverlayActive.collectAsState()
    val slashCount by BotStateController.slashCount.collectAsState()
    val slashesPerSecond by BotStateController.slashesPerSecond.collectAsState()
    val config by BotStateController.config.collectAsState()
    val logs by BotStateController.recentLogs.collectAsState()

    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            delay(1500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⚔️ FRUIT NINJA AUTO-SPLASHER",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val isSlashing = botRunState == BotRunState.SLASHING
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSlashing) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFFFF5252).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252)
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSlashing) "SLASHING" else "IDLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        icon = {
                            when (tab) {
                                NavigationTab.DASHBOARD -> Text("⚡", fontSize = 18.sp)
                                NavigationTab.ARENA -> Text("🍉", fontSize = 18.sp)
                                NavigationTab.CONFIG -> Icon(Icons.Default.Settings, contentDescription = "Settings")
                                NavigationTab.LOGS -> Text("📜", fontSize = 18.sp)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(
                    context = context,
                    isAccessibilityConnected = isAccessibilityConnected,
                    hasOverlayPermission = hasOverlayPermission,
                    botRunState = botRunState,
                    isOverlayActive = isOverlayActive,
                    slashCount = slashCount,
                    slashesPerSecond = slashesPerSecond,
                    config = config,
                    onToggleSlasher = {
                        val touch = AutoTouchService.instance
                        if (botRunState == BotRunState.SLASHING) {
                            touch?.stopAutoSlash()
                        } else {
                            if (touch != null) {
                                touch.startAutoSlash()
                            } else {
                                openAccessibilitySettings(context)
                            }
                        }
                    },
                    onToggleOverlay = {
                        if (isOverlayActive) {
                            val stopIntent = Intent(context, FloatingControlService::class.java).apply {
                                action = FloatingControlService.ACTION_STOP_OVERLAY
                            }
                            context.startService(stopIntent)
                        } else {
                            if (hasOverlayPermission) {
                                val intent = Intent(context, FloatingControlService::class.java)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                openOverlaySettings(context)
                            }
                        }
                    },
                    onSelectSpeedPreset = { delayMs ->
                        BotStateController.updateConfig(config.copy(delayBetweenSwipesMs = delayMs))
                        BotStateController.addLog("Speed preset set to ${delayMs}ms delay")
                    },
                    onSelectPattern = { pattern ->
                        BotStateController.updateConfig(config.copy(pattern = pattern))
                        BotStateController.addLog("Strategy changed: ${pattern.label}")
                    }
                )

                NavigationTab.ARENA -> DojoArenaScreen(
                    botRunState = botRunState,
                    config = config,
                    onSlashTest = {
                        AutoTouchService.instance?.performSingleSwipe(200f, 600f, 800f, 400f, 35L)
                    }
                )

                NavigationTab.CONFIG -> ConfigScreen(
                    config = config,
                    onConfigChange = { updated ->
                        BotStateController.updateConfig(updated)
                    },
                    onResetStats = {
                        BotStateController.resetStats()
                        BotStateController.addLog("Statistics cleared.")
                    }
                )

                NavigationTab.LOGS -> LogsScreen(
                    logs = logs,
                    onClearLogs = {
                        BotStateController.resetStats()
                    }
                )
            }
        }
    }
}

@Composable
fun DashboardScreen(
    context: Context,
    isAccessibilityConnected: Boolean,
    hasOverlayPermission: Boolean,
    botRunState: BotRunState,
    isOverlayActive: Boolean,
    slashCount: Int,
    slashesPerSecond: Float,
    config: BotConfig,
    onToggleSlasher: () -> Unit,
    onToggleOverlay: () -> Unit,
    onSelectSpeedPreset: (Long) -> Unit,
    onSelectPattern: (SlashPattern) -> Unit
) {
    val isSlashing = botRunState == BotRunState.SLASHING

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isAccessibilityConnected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Accessibility Service Required",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFE082),
                                fontSize = 14.sp
                            )
                            Text(
                                "Required to dispatch auto-slice touch gestures over game.",
                                fontSize = 12.sp,
                                color = Color(0xFFFFF3E0)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Enable", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (!hasOverlayPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Overlay Permission Needed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF80DEEA),
                                fontSize = 14.sp
                            )
                            Text(
                                "Enables floating HUD above Fruit Ninja.",
                                fontSize = 12.sp,
                                color = Color(0xFFE0F7FA)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { openOverlaySettings(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Hero Telemetry Card
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.elevatedCardElevation(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSlashing) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSlashing) "Status: SLASHING" else "Status: IDLE",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSlashing) Color(0xFF00E676) else Color(0xFFFF5252)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$slashCount",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "TOTAL SLASHES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(44.dp)
                                .background(Color(0xFF384050))
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f/s", slashesPerSecond),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF00E5FF)
                            )
                            Text(
                                text = "SLASH SPEED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Big Action Button: START / STOP
                    Button(
                        onClick = onToggleSlasher,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("toggle_slasher_btn"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSlashing) Color(0xFFFF3333) else Color(0xFF00E676)
                        )
                    ) {
                        Text(
                            text = if (isSlashing) "🛑 STOP AUTO-SPLASHER" else "⚡ START AUTO-SPLASHER",
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSlashing) Color.White else Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Floating HUD Overlay Toggle
                    OutlinedButton(
                        onClick = onToggleOverlay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isOverlayActive) Color(0xFFFFCC00) else Color(0xFF556075)
                        )
                    ) {
                        Text(
                            text = if (isOverlayActive) "🪟 Close Floating HUD Overlay" else "🪟 Launch Floating HUD Overlay",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverlayActive) Color(0xFFFFCC00) else Color(0xFFC4C8D4)
                        )
                    }
                }
            }
        }

        // Multi-Pattern Strategy Selector
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 GESTURE STRATEGY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Safe Y: 12%-55%",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    SlashPattern.entries.forEach { pattern ->
                        val isSelected = config.pattern == pattern
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF332B00) else Color(0xFF252A38),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFFFCC00) else Color(0xFF353D50)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectPattern(pattern) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFFCC00) else Color(0xFF667085))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pattern.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color(0xFFFFCC00) else Color.White
                                    )
                                    Text(
                                        text = pattern.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFFA0A8B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Speed Presets Section
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🚀 SPEED PRESETS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            Triple("Hyper (20ms)", 20L, "⚡⚡⚡"),
                            Triple("Turbo (40ms)", 40L, "⚡⚡"),
                            Triple("Safe (75ms)", 75L, "⚡")
                        )

                        presets.forEach { (label, delayMs, emoji) ->
                            val isSelected = config.delayBetweenSwipesMs == delayMs
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFF2A3040),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectSpeedPreset(delayMs) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(emoji, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DojoArenaScreen(
    botRunState: BotRunState,
    config: BotConfig,
    onSlashTest: () -> Unit
) {
    val isSlashing = botRunState == BotRunState.SLASHING
    val transition = rememberInfiniteTransition(label = "blade")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blade_pulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DOJO ARENA SIMULATOR",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Live preview of ${config.pattern.label} (Y: 12% to 55% Safe Zone)",
            fontSize = 12.sp,
            color = Color(0xFFA0A8B8),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Interactive Arena Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F1118))
                .border(2.dp, if (isSlashing) Color(0xFF00E676) else Color(0xFF333A4D), RoundedCornerShape(20.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw Safe Upper Zone (12% to 55% height)
                val minY = h * 0.12f
                val maxY = h * 0.55f
                val minX = w * 0.05f
                val maxX = w * 0.95f

                // Safe Zone Box
                drawRect(
                    color = Color(0xFF00E676).copy(alpha = 0.08f),
                    topLeft = Offset(minX, minY),
                    size = androidx.compose.ui.geometry.Size(maxX - minX, maxY - minY)
                )

                drawRect(
                    color = Color(0xFFFFCC00).copy(alpha = 0.4f),
                    topLeft = Offset(minX, minY),
                    size = androidx.compose.ui.geometry.Size(maxX - minX, maxY - minY),
                    style = Stroke(width = 2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
                )

                // Danger zone label at bottom
                drawRect(
                    color = Color(0xFFFF5252).copy(alpha = 0.05f),
                    topLeft = Offset(minX, maxY),
                    size = androidx.compose.ui.geometry.Size(maxX - minX, h - maxY)
                )

                // Render dynamic pattern path preview
                when (config.pattern) {
                    SlashPattern.INFINITY_WAVE -> {
                        val path = Path()
                        val cx = w * 0.5f
                        val cy = (minY + maxY) * 0.5f
                        val scaleX = (maxX - minX) * 0.42f * pulse
                        val scaleY = (maxY - minY) * 0.45f * pulse

                        for (i in 0..16) {
                            val t = (i.toFloat() / 16f) * 2.0 * PI
                            val denom = (1.0 + sin(t) * sin(t)).toFloat()
                            val px = cx + (scaleX * cos(t).toFloat()) / denom
                            val py = cy + (scaleY * (sin(t) * cos(t)).toFloat()) / denom
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }

                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFFFFCC00), Color(0xFF00E5FF))),
                            style = Stroke(width = if (isSlashing) 6f else 3f)
                        )
                    }

                    SlashPattern.FULL_SWEEP -> {
                        for (i in 0..3) {
                            val y = minY + (maxY - minY) * (i / 3f)
                            drawLine(
                                brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFF00E5FF), Color.White, Color.Transparent)),
                                start = Offset(minX, y),
                                end = Offset(maxX, y),
                                strokeWidth = if (isSlashing) 5f else 3f
                            )
                        }
                    }

                    SlashPattern.Z_GRID -> {
                        val path = Path().apply {
                            moveTo(minX, minY)
                            lineTo(maxX, minY + 30f)
                            lineTo(minX, maxY - 30f)
                            lineTo(maxX, maxY)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFFCC00), Color(0xFF00E676))),
                            style = Stroke(width = if (isSlashing) 6f else 3f)
                        )
                    }

                    SlashPattern.DOUBLE_CROSS -> {
                        val midX = w * 0.5f
                        val midY = (minY + maxY) * 0.5f
                        val spanX = (maxX - minX) * 0.4f * pulse
                        val spanY = (maxY - minY) * 0.4f * pulse

                        drawLine(
                            brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFFFFCC00), Color.White, Color.Transparent)),
                            start = Offset(midX - spanX, midY - spanY),
                            end = Offset(midX + spanX, midY + spanY),
                            strokeWidth = if (isSlashing) 6f else 3f
                        )
                        drawLine(
                            brush = Brush.linearGradient(listOf(Color.Transparent, Color(0xFF00E676), Color.White, Color.Transparent)),
                            start = Offset(midX + spanX, midY - spanY),
                            end = Offset(midX - spanX, midY + spanY),
                            strokeWidth = if (isSlashing) 6f else 3f
                        )
                    }

                    SlashPattern.WHIRLWIND -> {
                        val cx = w * 0.5f
                        val cy = (minY + maxY) * 0.5f
                        val rx = (maxX - minX) * 0.35f * pulse
                        val ry = (maxY - minY) * 0.42f * pulse
                        val path = Path()

                        for (step in 0..12) {
                            val ang = (step.toFloat() / 12f) * 2.0 * PI
                            val px = cx + cos(ang).toFloat() * rx
                            val py = cy + sin(ang).toFloat() * ry
                            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }

                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF00E676), Color(0xFFFFCC00))),
                            style = Stroke(width = if (isSlashing) 6f else 3f)
                        )
                    }

                    SlashPattern.SPIRAL_WHIRLWIND -> {
                        val cx = w * 0.5f
                        val cy = (minY + maxY) * 0.5f
                        val maxRx = (maxX - minX) * 0.44f * pulse
                        val maxRy = (maxY - minY) * 0.46f * pulse
                        val path = Path()
                        val totalSteps = 24

                        for (step in 0..totalSteps) {
                            val progress = step.toFloat() / totalSteps.toFloat()
                            val ang = progress * 2.2 * 2.0 * PI
                            val r = 0.15f + 0.85f * progress
                            val px = cx + cos(ang).toFloat() * maxRx * r
                            val py = cy + sin(ang).toFloat() * maxRy * r
                            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }

                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFFFCC00), Color(0xFF00E676), Color(0xFF00E5FF))),
                            style = Stroke(width = if (isSlashing) 6f else 3f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSlashing) "⚔️ ${config.pattern.label.uppercase()} ACTIVE ⚔️" else "ARENA IDLE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSlashing) Color(0xFF00E676) else Color(0xFF6B7280),
                    modifier = Modifier.scale(if (isSlashing) pulse else 1f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isSlashing) "Continuous safe-zone swipes running at hyper frequency" else "Launch HUD or tap Start to begin auto-swipes",
                    fontSize = 11.5.sp,
                    color = Color(0xFFC4C8D4),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onSlashTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF))
        ) {
            Text("🗡️ Test Single High-Speed Swipe", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ConfigScreen(
    config: BotConfig,
    onConfigChange: (BotConfig) -> Unit,
    onResetStats: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "⚙️ ENGINE TUNING & CONFIGURATION",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Swipe Duration Slider (20ms - 80ms)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gesture Duration", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text("${config.swipeDurationMs} ms", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Time taken for blade stroke to travel from start to end (20ms - 80ms)", fontSize = 11.sp, color = Color(0xFFA0A8B8))
                    Slider(
                        value = config.swipeDurationMs.toFloat(),
                        onValueChange = { onConfigChange(config.copy(swipeDurationMs = it.toLong())) },
                        valueRange = 20f..80f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // Delay Between Swipes Slider (10ms - 100ms)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delay Between Consecutive Swipes", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        Text("${config.delayBetweenSwipesMs} ms", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF00E5FF))
                    }
                    Text("Pause interval before generating next swipe burst (10ms - 100ms)", fontSize = 11.sp, color = Color(0xFFA0A8B8))
                    Slider(
                        value = config.delayBetweenSwipesMs.toFloat(),
                        onValueChange = { onConfigChange(config.copy(delayBetweenSwipesMs = it.toLong())) },
                        valueRange = 10f..100f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF)
                        )
                    )
                }
            }
        }

        // Auto-Restart Banana Slice Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🍌 Auto-Restart Banana Slice", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFFCC00))
                            Text("Periodically slices 'Play Again' banana zone (X:75%, Y:80%)", fontSize = 11.sp, color = Color(0xFFA0A8B8))
                        }
                        Button(
                            onClick = { onConfigChange(config.copy(autoRestartBananaEnabled = !config.autoRestartBananaEnabled)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (config.autoRestartBananaEnabled) Color(0xFF00E676) else Color(0xFF424A5E)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (config.autoRestartBananaEnabled) "ENABLED" else "DISABLED",
                                color = if (config.autoRestartBananaEnabled) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Hardware Emergency Kill-Switch Info Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1414)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Kill Switch",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("🚨 HARDWARE EMERGENCY BRAKE", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFFF8A80))
                        Text(
                            "Pressing the physical [VOLUME DOWN] button instantly terminates all active gestures immediately.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
            }
        }

        // Reset Stats Action
        item {
            OutlinedButton(
                onClick = onResetStats,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset All Slashing Statistics", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LogsScreen(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
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
                text = "📜 REAL-TIME ENGINE LOGS",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onClearLogs) {
                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFFA0A8B8))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1118)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222735)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { logEntry ->
                    Text(
                        text = logEntry,
                        fontSize = 11.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = when {
                            logEntry.contains("STARTED") -> Color(0xFF00E676)
                            logEntry.contains("STOPPED") -> Color(0xFFFF5252)
                            logEntry.contains("Accessibility") -> Color(0xFFFFCC00)
                            else -> Color(0xFFCFD5E2)
                        }
                    )
                }
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}

private fun openOverlaySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
