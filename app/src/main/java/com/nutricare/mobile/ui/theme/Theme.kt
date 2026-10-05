package com.nutricare.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Light & clean MyFitnessPal-style palette with NutriCare emerald identity
val Emerald = Color(0xFF10B981)
val EmeraldDark = Color(0xFF059669)
val Teal = Color(0xFF14B8A6)
val Ink = Color(0xFF0F172A)
val Slate = Color(0xFF64748B)
val Mist = Color(0xFFF1F5F9)
val CardBg = Color(0xFFF8FAFC)
val Rose = Color(0xFFF43F5E)
val Amber = Color(0xFFF59E0B)
val Sky = Color(0xFF0EA5E9)

private val LightScheme = lightColorScheme(
    primary = EmeraldDark,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CardBg,
    onSurfaceVariant = Slate,
    outline = Color(0xFFE2E8F0),
    error = Rose,
)

@Composable
fun NutriTheme(content: @Composable () -> Unit) {
    // App is always light — a health companion should feel bright and clean
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography(
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.4).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = (-0.2).sp),
            bodyMedium = TextStyle(fontSize = 14.sp),
            bodySmall = TextStyle(fontSize = 12.sp, color = Slate),
            labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.4.sp),
        ),
        content = content,
    )
}
