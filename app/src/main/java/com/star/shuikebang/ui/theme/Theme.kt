package com.star.shuikebang.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = CardWhite,
    secondary = BrandDeep,
    background = PageBg,
    onBackground = TextMain,
    surface = CardWhite,
    onSurface = TextMain,
    error = RecordRed,
    onError = CardWhite,
    outline = DividerLine,
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = CardWhite,
    secondary = Brand,
    background = PageBgDark,
    onBackground = TextMainDark,
    surface = CardDark,
    onSurface = TextMainDark,
    error = RecordRed,
    onError = CardWhite,
    outline = androidx.compose.ui.graphics.Color(0xFF2A3140),
)

@Composable
fun ShuikeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
