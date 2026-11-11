package fr.pingooweb.ding.ui.components.bento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.ProgramColors
import fr.pingooweb.ding.ui.theme.extended

enum class PillVariant {
    /** Encre (noir en clair, inversé en sombre). */
    Primary,
    /** Couleur forte du programme (fournir `program`). */
    Strong,
    /** Action « étape atteinte » (orange). */
    Attention,
    /** Blanc / tileRaised. */
    Tonal,
}

/** Tailles : hauteur, taille de l'icône, police. */
enum class PillSize(val height: Dp, val icon: Dp) {
    M(Dimens.ButtonM, 22.dp),
    L(Dimens.ButtonL, 28.dp),
    XL(Dimens.ButtonXL, 32.dp),
}

/** Bouton pilule Bento (icône + texte). [program] requis pour [PillVariant.Strong]. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.Primary,
    size: PillSize = PillSize.M,
    icon: ImageVector? = null,
    program: ProgramColors? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val ext = MaterialTheme.extended
    val (container, content) = when (variant) {
        PillVariant.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        PillVariant.Strong ->
            if (program != null) program.strong to program.onStrong
            else MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        PillVariant.Attention -> ext.attentionAction to ext.onAttentionAction
        PillVariant.Tonal -> ext.tileRaised to ext.ink
    }
    val textStyle = when (size) {
        PillSize.M -> MaterialTheme.typography.titleSmall
        PillSize.L -> MaterialTheme.typography.titleMedium
        PillSize.XL -> MaterialTheme.typography.headlineSmall.copy(fontSize = 30.sp)
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = size.height)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier,
            ),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = ext.track, disabledContentColor = ext.inkSecondary,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = if (size == PillSize.XL) 28.dp else 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size.icon))
            Spacer(Modifier.width(if (size == PillSize.XL) 12.dp else 8.dp))
        }
        Text(text, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@ThemePreviews
@Composable
private fun PillButtonPreview() = PreviewSurface {
    Column(Modifier.padding(Dimens.Margin), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        PillButton("Démarrer", {}, variant = PillVariant.Primary, size = PillSize.L, icon = Icons.Rounded.PlayArrow)
        PillButton(
            "Pause", {}, variant = PillVariant.Strong, size = PillSize.L, icon = Icons.Rounded.Pause,
            program = MaterialTheme.extended.program(0),
        )
        PillButton("Valider", {}, variant = PillVariant.Attention, size = PillSize.XL, icon = Icons.Rounded.Check)
        PillButton("Annuler", {}, variant = PillVariant.Tonal)
    }
}
