package com.readygym.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Fixed sunny light palette: do not silently switch to dark or dynamic colors. */
internal object ReadyGymColors {
    val Background = Color(0xFFFFF9F1)
    val Surface = Color(0xFFFFFFFF)
    val SelectedSurface = Color(0xFFFFF0DF)
    val Text = Color(0xFF24362D)
    val MutedText = Color(0xFF56645B)
    val Outline = Color(0xFF758176)
    val Action = Color(0xFFBE4708)
    val OnAction = Color(0xFFFFFFFF)
    val Accent = Color(0xFF28734C)
    val AccentSurface = Color(0xFFE8F4E9)
    val Error = Color(0xFFB3261E)
    val ErrorSurface = Color(0xFFFFEDEA)
}

@Composable
fun ReadyGymTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = ReadyGymColors.Action,
            onPrimary = ReadyGymColors.OnAction,
            primaryContainer = ReadyGymColors.SelectedSurface,
            onPrimaryContainer = ReadyGymColors.Text,
            secondary = ReadyGymColors.Accent,
            onSecondary = ReadyGymColors.Surface,
            secondaryContainer = ReadyGymColors.AccentSurface,
            onSecondaryContainer = ReadyGymColors.Accent,
            tertiary = ReadyGymColors.Accent,
            onTertiary = ReadyGymColors.Surface,
            tertiaryContainer = ReadyGymColors.AccentSurface,
            onTertiaryContainer = ReadyGymColors.Accent,
            background = ReadyGymColors.Background,
            onBackground = ReadyGymColors.Text,
            surface = ReadyGymColors.Surface,
            onSurface = ReadyGymColors.Text,
            surfaceVariant = ReadyGymColors.SelectedSurface,
            onSurfaceVariant = ReadyGymColors.MutedText,
            surfaceTint = ReadyGymColors.Action,
            surfaceBright = ReadyGymColors.Surface,
            surfaceDim = ReadyGymColors.Background,
            surfaceContainerLowest = ReadyGymColors.Surface,
            surfaceContainerLow = ReadyGymColors.Background,
            surfaceContainer = ReadyGymColors.Surface,
            surfaceContainerHigh = ReadyGymColors.SelectedSurface,
            surfaceContainerHighest = ReadyGymColors.SelectedSurface,
            outline = ReadyGymColors.Outline,
            outlineVariant = ReadyGymColors.Outline,
            inverseSurface = ReadyGymColors.Text,
            inverseOnSurface = ReadyGymColors.Surface,
            inversePrimary = ReadyGymColors.SelectedSurface,
            error = ReadyGymColors.Error,
            onError = ReadyGymColors.Surface,
            errorContainer = ReadyGymColors.ErrorSurface,
            onErrorContainer = ReadyGymColors.Error,
            scrim = ReadyGymColors.Text
        ),
        content = content
    )
}
