package com.mipresupuestocr.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mipresupuestocr.domain.model.ThemeMode

val LocalPositiveColor = staticCompositionLocalOf { PositiveDark }

/**
 * Windows 11 surface language: windows and bars sit on the darker mantle layer, cards on the lighter
 * base layer, and controls are close to square (4 dp) while cards use an 8 dp corner.
 */
val FluentShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Slightly tighter, heavier headings than the Material defaults, closer to Fluent's type ramp. */
private val FluentTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp,
        ),
    )
}

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = ErrorLight,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLow,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightBackground,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = ErrorDark,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerHigh,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkBackground,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

/**
 * [dynamicColor] stays off by default: Android's wallpaper palette would replace the Catppuccin
 * colours this app is built around.
 */
@Composable
fun MiPresupuestoTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    CompositionLocalProvider(
        LocalPositiveColor provides if (darkTheme) PositiveDark else PositiveLight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FluentTypography,
            shapes = FluentShapes,
            content = content,
        )
    }
}

/** Semantic colour for gains and other "good" figures. */
val positiveColor: Color
    @Composable get() = LocalPositiveColor.current
