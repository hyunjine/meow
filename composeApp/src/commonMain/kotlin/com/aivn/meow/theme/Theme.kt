package com.aivn.meow.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MeowColors {
    val Background = Color(0xFFF1F1F6)
    val Surface = Color(0xFFFFFFFF)
    val GlassSurface = Color(0xFFFFFFFF)
    val GlassSurfaceStrong = Color(0xFFFFFFFF)
    val GlassBorder = Color(0xFFE4E4EC)

    val TextPrimary = Color(0xFF0D0F26)
    val TextSecondary = Color(0xFF383D66)
    val TextTertiary = Color(0xFF616A94)

    val Brand = Color(0xFF3D5EFF)
    val BrandSubtle = Color(0x223D5EFF)
    val Success = Color(0xFF20B76B)
    val Warning = Color(0xFFFA9E29)
    val Error = Color(0xFFF04D59)
    val Violet = Color(0xFF8B52EB)
    val Teal = Color(0xFF2EAEBF)
    val Grey = Color(0xFF737A99)
}

private val LightScheme: ColorScheme = lightColorScheme(
    primary = MeowColors.Brand,
    background = MeowColors.Background,
    surface = MeowColors.Surface,
    onPrimary = Color.White,
    onBackground = MeowColors.TextPrimary,
    onSurface = MeowColors.TextPrimary,
    error = MeowColors.Error,
)

private val AppTypography: Typography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MeowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = AppTypography,
        content = content,
    )
}
