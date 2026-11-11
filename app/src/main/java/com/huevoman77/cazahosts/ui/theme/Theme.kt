package com.huevoman77.cazahosts.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.huevoman77.cazahosts.data.Severity

val DeepSpace = Color(0xFF070B18)
val SurfaceDark = Color(0xFF0E1526)
val SurfaceElevated = Color(0xFF141E33)
val Mint = Color(0xFF22E3A5)
val Azure = Color(0xFF5B8CFF)
val Coral = Color(0xFFFF6B7A)
val Amber = Color(0xFFFFC46B)
val Violet = Color(0xFFB48CFF)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00281A),
    primaryContainer = Color(0xFF0C3E2D),
    onPrimaryContainer = Color(0xFF9CFFD9),
    secondary = Azure,
    onSecondary = Color(0xFF00174A),
    secondaryContainer = Color(0xFF17305F),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Violet,
    onTertiary = Color(0xFF2C1160),
    error = Coral,
    onError = Color(0xFF450010),
    errorContainer = Color(0xFF5B1A24),
    onErrorContainer = Color(0xFFFFD9DC),
    background = DeepSpace,
    onBackground = Color(0xFFE6ECF7),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6ECF7),
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = Color(0xFFA6B1C6),
    outline = Color(0xFF364463),
    outlineVariant = Color(0xFF1E2942)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00795A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F2DC),
    onPrimaryContainer = Color(0xFF00281A),
    secondary = Color(0xFF2F5FCC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E2FF),
    onSecondaryContainer = Color(0xFF00174A),
    tertiary = Color(0xFF6C43C9),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF121722),
    surface = Color.White,
    onSurface = Color(0xFF121722),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF4A5468),
    outline = Color(0xFFC3CCDD),
    outlineVariant = Color(0xFFE2E8F2)
)

private val CazaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun CazaHostsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CazaTypography,
        content = content
    )
}

fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICA -> Coral
    Severity.ALTA -> Color(0xFFFF9F43)
    Severity.MEDIA -> Amber
    Severity.BAJA -> Azure
    Severity.INFO -> Color(0xFF7F8CA6)
}
