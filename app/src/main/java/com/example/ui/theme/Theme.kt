package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val DarkColorScheme = darkColorScheme(
    primary = SoftLavender,
    onPrimary = PrimaryContrastText,
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFE8DEF8),
    secondary = ActiveMint,
    onSecondary = Color(0xFF1E3600),
    secondaryContainer = CardInnerBg,
    onSecondaryContainer = TextPrimaryWhite,
    background = DeepDarkBg,
    onBackground = TextPrimaryWhite,
    surface = SlateSurface,
    onSurface = TextPrimaryWhite,
    surfaceVariant = CardInnerBg,
    onSurfaceVariant = SoftMutedGray,
    outline = LightBorder,
    outlineVariant = DarkBorder
)


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme


  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
