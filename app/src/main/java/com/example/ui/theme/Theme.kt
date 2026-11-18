package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = OnDeepTealContainer,
    secondary = ScanMint,
    onSecondary = Color.White,
    secondaryContainer = ScanMintContainer,
    onSecondaryContainer = OnScanMintContainer,
    tertiary = WarmAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarmAmberContainer,
    onTertiaryContainer = OnWarmAmberContainer,
    background = PaperBackground,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = PaperOutline,
    error = ErrorRed,
    errorContainer = ErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLightPrimary,
    onPrimary = OnTealLightPrimary,
    primaryContainer = TealDarkContainer,
    onPrimaryContainer = DeepTealContainer,
    secondary = ScanMintBright,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF0B4F49),
    onSecondaryContainer = ScanMintContainer,
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = WarmAmberContainer,
    background = SlateBackground,
    onBackground = DarkTextPrimary,
    surface = SlateSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = SlateOutline
)

@Composable
fun PaperScanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PaperScanTypography,
        content = content
    )
}
