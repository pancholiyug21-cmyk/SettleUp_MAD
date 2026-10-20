package com.example.splitwsie.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// SETTLE relies on a clean, light premium theme. Dynamic colour and dark mode are deliberately
// not enabled so the brand identity is identical across devices.

private val SettleColorScheme = lightColorScheme(
    primary = SettlePrimary,
    onPrimary = SettleOnPrimary,
    primaryContainer = SettlePrimaryContainer,
    onPrimaryContainer = SettleOnPrimary,
    background = SettleBackground,
    onBackground = SettleTextPrimary,
    surface = SettleSurface,
    onSurface = SettleTextPrimary,
    surfaceVariant = SettleSurfaceVariant,
    onSurfaceVariant = SettleTextSecondary,
    error = SettleNegative,
    errorContainer = SettleNegativeContainer,
    onError = SettleSurface,
    onErrorContainer = SettleNegative,
    outline = SettleDivider,
    outlineVariant = SettleDivider
)

@Composable
fun SplitwsieTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            // Window.statusBarColor / navigationBarColor are deprecated and no-ops from API 35.
            // enableEdgeToEdge() in MainActivity draws the app behind the bars; all that is
            // needed here is dark icons so they stay legible on the light background.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = SettleColorScheme,
        typography = Typography,
        content = content
    )
}
