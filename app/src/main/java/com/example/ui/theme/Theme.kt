package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = AgentPrimaryDark,
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = AgentSlate400,
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF312E81),
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = AgentDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = AgentDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = AgentSlate800,
    onSurfaceVariant = AgentSlate400,
    surfaceContainerLowest = Color(0xFF080C14),
    surfaceContainerLow = AgentDarkContainerLow,
    surfaceContainer = AgentDarkContainer,
    surfaceContainerHigh = AgentDarkContainerHigh,
    surfaceContainerHighest = AgentDarkContainerHighest,
    outline = Color(0xFF2E3E5D),
    outlineVariant = Color(0xFF1E293B)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AgentPrimaryBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = AgentAccentIndigo,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEF2FF),
    onTertiaryContainer = Color(0xFF3730A3),
    background = AgentLightBackground,
    onBackground = Color(0xFF0F172A),
    surface = AgentLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = AgentLightContainerLow,
    surfaceContainer = AgentLightContainer,
    surfaceContainerHigh = AgentLightContainerHigh,
    surfaceContainerHighest = AgentLightContainerHighest,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
