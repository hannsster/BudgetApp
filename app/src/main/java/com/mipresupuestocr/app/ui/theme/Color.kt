package com.mipresupuestocr.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Catppuccin palettes, used with the flat surface language of a Windows 11 app:
 * the window sits on the darker "mantle" layer and cards are the lighter "base" layer, separated by
 * a hairline border instead of a shadow. Accent colours are the flavour's own blue, mauve and green,
 * so the app keeps Catppuccin's character rather than Windows' exact greys.
 *
 * Mocha is the default (dark) flavour, Latte the light one. Swapping in Frappé or Macchiato only
 * means replacing the Mocha constants below.
 * https://github.com/catppuccin/catppuccin
 */

// Catppuccin Mocha (§ dark)
private val MochaCrust = Color(0xFF11111B)
private val MochaMantle = Color(0xFF181825)
private val MochaBase = Color(0xFF1E1E2E)
private val MochaSurface0 = Color(0xFF313244)
private val MochaSurface1 = Color(0xFF45475A)
private val MochaOverlay1 = Color(0xFF7F849C)
private val MochaSubtext0 = Color(0xFFA6ADC8)
private val MochaText = Color(0xFFCDD6F4)
private val MochaBlue = Color(0xFF89B4FA)
private val MochaLavender = Color(0xFFB4BEFE)
private val MochaMauve = Color(0xFFCBA6F7)
private val MochaTeal = Color(0xFF94E2D5)
private val MochaGreen = Color(0xFFA6E3A1)
private val MochaRed = Color(0xFFF38BA8)
private val MochaPeach = Color(0xFFFAB387)

// Catppuccin Latte (light)
private val LatteCrust = Color(0xFFDCE0E8)
private val LatteMantle = Color(0xFFE6E9EF)
private val LatteBase = Color(0xFFEFF1F5)
private val LatteSurface0 = Color(0xFFCCD0DA)
private val LatteSurface1 = Color(0xFFBCC0CC)
private val LatteOverlay1 = Color(0xFF8C8FA1)
private val LatteSubtext0 = Color(0xFF6C6F85)
private val LatteText = Color(0xFF4C4F69)
private val LatteBlue = Color(0xFF1E66F5)
private val LatteLavender = Color(0xFF7287FD)
private val LatteMauve = Color(0xFF8839EF)
private val LatteTeal = Color(0xFF179299)
private val LatteGreen = Color(0xFF40A02B)
private val LatteRed = Color(0xFFD20F39)

// Role names the theme maps into Material 3.
val DarkPrimary = MochaBlue
val DarkOnPrimary = MochaCrust
val DarkPrimaryContainer = Color(0xFF28406B)
val DarkOnPrimaryContainer = MochaLavender

val DarkSecondary = MochaMauve
val DarkOnSecondary = MochaCrust
val DarkSecondaryContainer = Color(0xFF3B2E52)
val DarkOnSecondaryContainer = MochaLavender

val DarkTertiary = MochaTeal
val DarkOnTertiary = MochaCrust
val DarkTertiaryContainer = Color(0xFF21403D)
val DarkOnTertiaryContainer = MochaTeal

val DarkBackground = MochaMantle
val DarkSurface = MochaMantle
val DarkSurfaceContainerLow = MochaBase
val DarkSurfaceContainerHigh = MochaSurface0
val DarkSurfaceContainerHighest = MochaSurface1
val DarkSurfaceVariant = MochaSurface0
val DarkOnSurface = MochaText
val DarkOnSurfaceVariant = MochaSubtext0
val DarkOutline = MochaOverlay1
val DarkOutlineVariant = MochaSurface1

val LightPrimary = LatteBlue
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD3E1FF)
val LightOnPrimaryContainer = Color(0xFF0B2E6F)

val LightSecondary = LatteMauve
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEADDFF)
val LightOnSecondaryContainer = Color(0xFF3B1E6E)

val LightTertiary = LatteTeal
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCEFEC)
val LightOnTertiaryContainer = Color(0xFF0B3B38)

val LightBackground = LatteMantle
val LightSurface = LatteMantle
val LightSurfaceContainerLow = LatteBase
val LightSurfaceContainerHigh = Color(0xFFF7F8FA)
val LightSurfaceContainerHighest = LatteSurface0
val LightSurfaceVariant = LatteSurface0
val LightOnSurface = LatteText
val LightOnSurfaceVariant = LatteSubtext0
val LightOutline = LatteOverlay1
val LightOutlineVariant = LatteSurface0

val ErrorLight = LatteRed
val ErrorDark = MochaRed
val PositiveLight = LatteGreen
val PositiveDark = MochaGreen
val WarningDark = MochaPeach
