package com.k1adili.atichat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.k1adili.atichat.R

/** Spacing scale used everywhere instead of ad-hoc dp values. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

// Seed: a deep chat green (the Windows app uses a green theme). Primary is reserved for the main
// actions, active states and outgoing bubbles.
private val Seed = Color(0xFF1F7A4D)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0E0),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF4F6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D9),
    onSecondaryContainer = Color(0xFF0C1F15),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBEEAF8),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF8FBF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8FBF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFBFC9C1),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7DD6A3),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0B5233),
    onPrimaryContainer = Color(0xFFD3F0E0),
    secondary = Color(0xFFB5CCBD),
    onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF384B3F),
    onSecondaryContainer = Color(0xFFD1E8D9),
    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF043542),
    tertiaryContainer = Color(0xFF214C59),
    onTertiaryContainer = Color(0xFFBEEAF8),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFE0E4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
)

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.vazirmatn, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.vazirmatn, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.vazirmatn, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Vazirmatn,
    fontWeight = weight,
    fontSize = size.sp,
    // Persian needs more vertical room than Latin-tuned defaults.
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private val AtiTypography = Typography(
    headlineSmall = style(24, FontWeight.Bold, 38),
    titleLarge = style(20, FontWeight.Bold, 32),
    titleMedium = style(16, FontWeight.SemiBold, 26),
    titleSmall = style(14, FontWeight.SemiBold, 24),
    bodyLarge = style(16, FontWeight.Normal, 28),
    bodyMedium = style(14, FontWeight.Normal, 24),
    bodySmall = style(12, FontWeight.Normal, 20),
    labelLarge = style(14, FontWeight.Medium, 22),
    labelMedium = style(12, FontWeight.Medium, 20),
    labelSmall = style(11, FontWeight.Medium, 18),
)

/** Message text follows its own content direction (Persian right-aligned, English left-aligned). */
val MessageTextStyle = TextStyle(textDirection = TextDirection.Content)

private val AtiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun AtiChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AtiTypography,
        shapes = AtiShapes,
    ) {
        // Full RTL on every screen, regardless of the device locale.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
