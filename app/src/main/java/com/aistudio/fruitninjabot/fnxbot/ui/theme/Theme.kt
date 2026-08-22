package com.aistudio.fruitninjabot.fnxbot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.aistudio.fruitninjabot.fnxbot.R

private val DarkColorScheme = darkColorScheme(
    primary = NinjaCrimson,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A101C),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = NinjaCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00383D),
    onSecondaryContainer = Color(0xFFB5FAFF),
    tertiary = NinjaAmber,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

@Composable
fun FruitNinjaBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
