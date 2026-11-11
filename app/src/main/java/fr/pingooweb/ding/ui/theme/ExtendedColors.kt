package fr.pingooweb.ding.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import fr.pingooweb.ding.domain.ProgramPalette

/** Paire d'un programme : [container] pastel (texte = ink), [strong] couleur forte (texte = [onStrong]). */
@Immutable
data class ProgramColors(val container: Color, val strong: Color, val onStrong: Color)

/** Couleurs hors schéma M3 (design Bento). Accès : `MaterialTheme.extended.tile`. */
@Immutable
data class ExtendedColors(
    val ink: Color,
    val inkSecondary: Color,
    val tile: Color,
    val tileRaised: Color,
    val track: Color,
    /** Fond « étape atteinte » (pêche / brun sombre). Texte dessus : [ink]. */
    val attention: Color,
    val onAttention: Color,
    /** Action « étape atteinte » (bouton, pastille). */
    val attentionAction: Color,
    val onAttentionAction: Color,
    val success: Color,
    /** Corail de marque : logo / icône / splash. */
    val brand: Color,
    /** Corail accessible pour du texte. */
    val brandText: Color,
    val programs: List<ProgramColors>,
) {
    /** Paire (conteneur, forte, onStrong) du programme (index borné à 0..7). */
    fun program(index: Int): ProgramColors = programs[index.coerceIn(0, programs.lastIndex)]

    /** Ancien accès : couleur forte seule. */
    fun programAccent(index: Int): Color = program(index).strong

    companion object {
        fun light() = ExtendedColors(
            ink = InkLight, inkSecondary = InkSecondaryLight,
            tile = TileLight, tileRaised = TileRaisedLight, track = TrackLight,
            attention = AttentionLight, onAttention = InkLight,
            attentionAction = AttentionActionLight, onAttentionAction = OnAttentionActionLight,
            success = SuccessLight, brand = BrandCoral, brandText = BrandTextLight,
            programs = palette(dark = false, onStrong = Color.White),
        )

        fun dark() = ExtendedColors(
            ink = InkDark, inkSecondary = InkSecondaryDark,
            tile = TileDark, tileRaised = TileRaisedDark, track = TrackDark,
            attention = AttentionDark, onAttention = InkDark,
            attentionAction = AttentionActionDark, onAttentionAction = OnAttentionActionDark,
            success = SuccessDark, brand = BrandCoral, brandText = BrandTextDark,
            programs = palette(dark = true, onStrong = Color(0xFF14161F)),
        )

        private fun palette(dark: Boolean, onStrong: Color) = List(ProgramPalette.SIZE) { i ->
            ProgramColors(
                container = Color(ProgramPalette.containerArgb(i, dark)),
                strong = Color(ProgramPalette.argb(i, dark)),
                onStrong = onStrong,
            )
        }
    }
}

val LocalExtendedColors = staticCompositionLocalOf { ExtendedColors.light() }

/** Accès : `MaterialTheme.extended.attention`. */
val MaterialTheme.extended: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
