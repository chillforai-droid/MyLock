package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MyLockDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = CyberBlack,
    primaryContainer = IceCyanContainer,
    onPrimaryContainer = Color(0xFFB3F5FF),
    secondary = CyanGlow,
    onSecondary = CyberBlack,
    secondaryContainer = BlueContainerDark,
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = CobaltBlue,
    onTertiary = Color.White,
    background = CyberBlack,
    onBackground = TextPrimaryDark,
    surface = ObsidianNavy,
    onSurface = TextPrimaryDark,
    surfaceVariant = DeepSlateBlue,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianNavy,
    surfaceContainer = DeepSlateBlue,
    surfaceContainerHigh = ElevatedNavy,
    outline = OutlineDark,
    error = AlertCrimsonDark,
    onError = CyberBlack
)

private val MyLockLightColorScheme = lightColorScheme(
    primary = LightBluePrimary,
    onPrimary = Color.White,
    primaryContainer = LightBlueContainer,
    onPrimaryContainer = Color(0xFF001B47),
    secondary = LightCyanSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = Color(0xFF002026),
    tertiary = SapphireBlue,
    onTertiary = Color.White,
    background = LightIceBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = LightSurface,
    surfaceContainer = Color(0xFFEDF3FA),
    surfaceContainerHigh = LightSurfaceVariant,
    outline = OutlineLight,
    error = AlertCrimson,
    onError = Color.White
)

val MyLockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyLockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MyLockDarkColorScheme else MyLockLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyLockTypography,
        shapes = MyLockShapes,
        content = content
    )
}
