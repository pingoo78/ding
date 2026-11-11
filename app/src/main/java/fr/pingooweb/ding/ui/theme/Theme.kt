package fr.pingooweb.ding.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Slate100 = Color(0xFFE3E7EE)

private val LightScheme = lightColorScheme(
    primary = InkLight,
    onPrimary = OnInkLight,
    primaryContainer = Slate100,
    onPrimaryContainer = InkLight,
    inversePrimary = InkDark,
    secondary = InkSecondaryLight,
    onSecondary = OnInkLight,
    secondaryContainer = Slate100,
    onSecondaryContainer = InkLight,
    tertiary = AttentionActionLight,
    onTertiary = OnAttentionActionLight,
    tertiaryContainer = AttentionLight,
    onTertiaryContainer = InkLight,
    background = BackgroundLight,
    onBackground = InkLight,
    surface = TileLight,
    onSurface = InkLight,
    surfaceVariant = Slate100,
    onSurfaceVariant = InkSecondaryLight,
    surfaceTint = Color.Transparent,
    inverseSurface = InkLight,
    inverseOnSurface = InkDark,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = OutlineLight,
    outlineVariant = TrackLight,
    scrim = Color.Black,
    surfaceBright = TileLight,
    surfaceDim = Color(0xFFDDE1E9),
    surfaceContainerLowest = TileLight,
    surfaceContainerLow = TileLight,
    surfaceContainer = TileLight,
    surfaceContainerHigh = TileLight,
    surfaceContainerHighest = Slate100,
)

private val DarkScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = OnInkDark,
    primaryContainer = TrackDark,
    onPrimaryContainer = InkDark,
    inversePrimary = InkLight,
    secondary = InkSecondaryDark,
    onSecondary = OnInkDark,
    secondaryContainer = TileRaisedDark,
    onSecondaryContainer = InkDark,
    tertiary = AttentionActionDark,
    onTertiary = OnAttentionActionDark,
    tertiaryContainer = AttentionDark,
    onTertiaryContainer = InkDark,
    background = BackgroundDark,
    onBackground = InkDark,
    surface = TileDark,
    onSurface = InkDark,
    surfaceVariant = TileRaisedDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceTint = Color.Transparent,
    inverseSurface = InkDark,
    inverseOnSurface = InkLight,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = Color(0xFF4A1717),
    onErrorContainer = Color(0xFFFECACA),
    outline = OutlineDark,
    outlineVariant = TrackDark,
    scrim = Color.Black,
    surfaceBright = TrackDark,
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = TileDark,
    surfaceContainer = TileDark,
    surfaceContainerHigh = TileRaisedDark,
    surfaceContainerHighest = TrackDark,
)

private val DingShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(Dimens.PillRadius),
    large = RoundedCornerShape(Dimens.TileRadius),
    extraLarge = RoundedCornerShape(Dimens.TileRadius),
)

/** Thème Ding! (design Bento). Le nom `DingTheme` est conservé (identifiant interne). */
@Composable
fun DingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val extended = if (darkTheme) ExtendedColors.dark() else ExtendedColors.light()
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = DingTypography,
            shapes = DingShapes,
            content = content,
        )
    }
}
