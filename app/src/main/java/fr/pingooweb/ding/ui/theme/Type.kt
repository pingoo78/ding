package fr.pingooweb.ding.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.R

/** Plus Jakarta Sans (OFL), TTF statiques. */
val JakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.plus_jakarta_extrabold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = JakartaFamily, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val DingTypography = Typography(
    displayLarge = style(57, 64, FontWeight.ExtraBold),
    displayMedium = style(44, 48, FontWeight.ExtraBold).copy(letterSpacing = (-0.03).em),
    displaySmall = style(36, 42, FontWeight.ExtraBold),
    headlineLarge = style(28, 34, FontWeight.ExtraBold).copy(letterSpacing = (-0.02).em), // titre d'écran
    headlineMedium = style(26, 32, FontWeight.ExtraBold),
    headlineSmall = style(22, 28, FontWeight.ExtraBold),                                   // titre de tuile
    titleLarge = style(22, 28, FontWeight.ExtraBold),
    titleMedium = style(18, 24, FontWeight.ExtraBold),                                     // titre de tuile
    titleSmall = style(16, 22, FontWeight.ExtraBold),
    bodyLarge = style(16, 24, FontWeight.Medium),
    bodyMedium = style(14, 20, FontWeight.Medium),
    bodySmall = style(13, 18, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(13, 18, FontWeight.SemiBold),                                      // libellé
    labelSmall = style(12, 16, FontWeight.SemiBold),
)

/** Chiffres tabulaires (largeur fixe) : à utiliser pour TOUS les temps affichés. */
private const val TNUM = "tnum"

/** Décompte héros (écran Exécution) ; réduire `fontSize` via BoxWithConstraints si besoin. */
val TimerHero = TextStyle(
    fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 96.sp, lineHeight = 1.0.em, letterSpacing = (-0.04).em, fontFeatureSettings = TNUM,
)

/** Grand décompte (tuiles, bandeau « En cours »). */
val TimerLarge = TextStyle(
    fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 44.sp, lineHeight = 1.05.em, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM,
)

/** Valeur de StatTile (28 sp). */
val TimerMedium = TextStyle(
    fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM,
)

/** Temps dans le corps de liste (lignes d'étapes, cartes). */
val TimerSmall = TextStyle(
    fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM,
)

// Anciens noms (alias)
val TimerDigits = TimerHero
val TimerDigitsMedium = TextStyle(
    fontFamily = JakartaFamily, fontWeight = FontWeight.ExtraBold,
    fontSize = 22.sp, lineHeight = 28.sp, fontFeatureSettings = TNUM,
)
val TimerDigitsSmall = TimerSmall
