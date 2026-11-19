package com.movin.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.movin.app.data.model.ThemeMode

private val AppleWhiteColorScheme = lightColorScheme(
    primary = AppleWhiteAccentBlue,
    onPrimary = Color.White,
    secondary = AppleWhiteAccentGreen,
    onSecondary = Color.White,
    error = AppleWhiteAccentRed,
    onError = Color.White,
    background = AppleWhiteBackground,
    onBackground = AppleWhiteTextPrimary,
    surface = AppleWhiteSurface,
    onSurface = AppleWhiteTextPrimary,
    surfaceVariant = AppleWhiteSurface,
    onSurfaceVariant = AppleWhiteTextSecondary,
    outline = AppleWhiteBorder
)

private val OledDarkColorScheme = darkColorScheme(
    primary = OledDarkAccentBlue,
    onPrimary = Color.White,
    secondary = OledDarkAccentGreen,
    onSecondary = Color.Black,
    error = OledDarkAccentRed,
    onError = Color.White,
    background = OledBlackBackground,
    onBackground = OledDarkTextPrimary,
    surface = OledDarkSurface,
    onSurface = OledDarkTextPrimary,
    surfaceVariant = OledDarkSurface,
    onSurfaceVariant = OledDarkTextSecondary,
    outline = OledDarkBorder
)

@Composable
fun MovinTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.APPLE_WHITE -> false
        ThemeMode.OLED_DARK -> true
    }

    val colorScheme = if (darkTheme) OledDarkColorScheme else AppleWhiteColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
