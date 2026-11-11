package fr.pingooweb.ding.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grille 4/8 dp, rayons et cibles Bento. */
object Dimens {
    val Space1 = 4.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space5 = 24.dp
    val Space6 = 32.dp
    val Space8 = 48.dp

    val ScreenMargin = 16.dp
    val ScreenMarginWide = 24.dp
    val WideBreakpoint = 600.dp

    /** Rayon d'une tuile Bento. */
    val TileRadius = 28.dp
    /** Rayon d'une pastille / puce. */
    val PillRadius = 20.dp
    /** Espace entre tuiles. */
    val Gap = 12.dp
    val Margin = 16.dp
    val TilePadding = 20.dp

    /** Anciens noms (alias). */
    val CardRadius = TileRadius
    val SheetRadius = TileRadius

    /** Cible tactile minimale. */
    val MinTouchTarget = 48.dp
    val TargetSize = MinTouchTarget
    val ButtonM = 48.dp
    val ButtonL = 64.dp
    val ButtonXL = 80.dp
    /** Bouton principal (64–72 dp). */
    val PrimaryControl = 72.dp

    fun screenMargin(widthDp: Dp): Dp = if (widthDp >= WideBreakpoint) ScreenMarginWide else ScreenMargin
}
