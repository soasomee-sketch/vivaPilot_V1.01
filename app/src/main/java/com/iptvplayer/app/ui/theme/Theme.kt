package com.iptvplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Color_White = androidx.compose.ui.graphics.Color.White

private val IptvColorScheme = darkColorScheme(
    background = Bg,
    surface = Surface,
    surfaceVariant = Surface2,
    primary = Accent,
    secondary = Accent2,
    error = Danger,
    onBackground = TextMain,
    onSurface = TextMain,
    onPrimary = Color_White,
    outline = Border,
)

// Typografia zbliżona do systemowego stosu z CSS (-apple-system, Segoe UI, Roboto...)
val IptvTypography = Typography(
    headlineSmall = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),   // header h1
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, color = TextDim),
    labelSmall = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun IptvPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IptvColorScheme,
        typography = IptvTypography,
        content = content,
    )
}
