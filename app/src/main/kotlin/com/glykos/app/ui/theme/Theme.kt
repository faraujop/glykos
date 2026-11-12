package com.glykos.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.glykos.app.R

/** Colours taken from the Glykos logo. */
object Brand {
    val Night = Color(0xFF120C2E)
    val Indigo = Color(0xFF2A1B5E)
    val Amber = Color(0xFFFFC15E)
    val Coral = Color(0xFFFF5E7E)
    val Glow = Color(0xFFFF7A59)
    val Cream = Color(0xFFFFF4E6)

    /** The logo's ink: amber → coral. Used sparingly: reading, logo, main action. */
    val Ink = Brush.linearGradient(listOf(Amber, Coral))
    val NightSky = Brush.linearGradient(listOf(Indigo, Night))
}

private val Light = lightColorScheme(
    primary = Brand.Indigo,
    onPrimary = Color.White,
    secondary = Brand.Coral,
    onSecondary = Brand.Indigo,
    tertiary = Brand.Amber,
    background = Color(0xFFF4F2FA),
    onBackground = Color(0xFF221A3D),
    surface = Color.White,
    onSurface = Color(0xFF221A3D),
    surfaceVariant = Color(0xFFECE8F6),
    onSurfaceVariant = Color(0xFF6B6488),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFECE8F6),
    outline = Color(0xFFCFC9E3),
    error = Color(0xFFC62828),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC3B5FF),
    onPrimary = Brand.Night,
    secondary = Color(0xFFFF8AA0),
    onSecondary = Brand.Night,
    tertiary = Brand.Amber,
    background = Color(0xFF0E0A24),
    onBackground = Color(0xFFEDEAF7),
    surface = Color(0xFF1C1543),
    onSurface = Color(0xFFEDEAF7),
    surfaceVariant = Color(0xFF2A2158),
    onSurfaceVariant = Color(0xFFB3ACCF),
    surfaceContainer = Color(0xFF1C1543),
    surfaceContainerHigh = Color(0xFF1C1543),
    surfaceContainerHighest = Color(0xFF2A2158),
    outline = Color(0xFF443A78),
    error = Color(0xFFFF8A80),
)

// Variable-font axes: stable in practice since Compose 1.3; API still marked experimental.
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Archivo, normal width for reading text. */
private val Text = FontFamily(archivo(400, 100f), archivo(500, 100f), archivo(600, 100f), archivo(700, 100f))

/** Archivo expanded: wordmark, headings and big numbers. */
val Wide = FontFamily(archivo(600, 115f), archivo(700, 115f), archivo(800, 115f))

private val Type = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Wide, fontWeight = FontWeight(800)),
        displayMedium = base.displayMedium.copy(fontFamily = Wide, fontWeight = FontWeight(800)),
        headlineMedium = base.headlineMedium.copy(fontFamily = Wide, fontWeight = FontWeight(700)),
        headlineSmall = base.headlineSmall.copy(fontFamily = Wide, fontWeight = FontWeight(700)),
        titleLarge = base.titleLarge.copy(fontFamily = Wide, fontWeight = FontWeight(700)),
        titleMedium = base.titleMedium.copy(fontFamily = Wide, fontWeight = FontWeight(700), fontSize = 16.sp),
        titleSmall = base.titleSmall.copy(fontFamily = Text, fontWeight = FontWeight(600)),
        bodyLarge = base.bodyLarge.copy(fontFamily = Text),
        bodyMedium = base.bodyMedium.copy(fontFamily = Text),
        bodySmall = base.bodySmall.copy(fontFamily = Text, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Text, fontWeight = FontWeight(700), fontSize = 15.sp),
        labelMedium = base.labelMedium.copy(fontFamily = Text, fontWeight = FontWeight(600)),
        labelSmall = base.labelSmall.copy(fontFamily = Text),
    )
}

/** The big glucose number, painted with the logo's ink. */
val ReadingStyle = TextStyle(fontFamily = Wide, fontWeight = FontWeight(800), fontSize = 76.sp, lineHeight = 76.sp, brush = Brand.Ink)

@Composable
fun GlykosTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
