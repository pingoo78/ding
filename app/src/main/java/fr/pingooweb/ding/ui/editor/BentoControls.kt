@file:OptIn(ExperimentalLayoutApi::class)

package fr.pingooweb.ding.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended

/** Fond « en creux » des contrôles posés sur une tuile (la tuile et tileRaised sont blancs en clair). */
@Composable
internal fun controlContainer(): Color = MaterialTheme.colorScheme.background

/**
 * Contrôle segmenté en pilule : segment actif = encre (texte inversé), sans le SegmentedButton M3.
 * L'état est porté par le remplissage ET par la sémantique (RadioButton sélectionné).
 */
@Composable
fun <T> PillSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = controlContainer(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(container)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.extended.ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Pilule de choix (sélectionnable) : sélection = encre + coche (jamais la couleur seule). */
@Composable
fun ChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    container: Color = controlContainer(),
) {
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.extended.ink
    Row(
        modifier
            .heightIn(min = Dimens.TargetSize)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val icon = if (selected) Icons.Rounded.Check else leadingIcon
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ligne de [ChoicePill] qui passe à la ligne si besoin. */
@Composable
fun ChoiceFlow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Bouton pilule discret (fond en creux), couleur de contenu personnalisable (ex. destructif). */
@Composable
fun SoftPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.extended.ink,
    container: Color = controlContainer(),
    contentDescription: String? = null,
) {
    Row(
        modifier
            .heightIn(min = Dimens.TargetSize)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Couleurs d'un TextField Bento : sans bordure ni indicateur, fond [container]. */
@Composable
fun bentoTextFieldColors(container: Color): TextFieldColors {
    val ext = MaterialTheme.extended
    return TextFieldDefaults.colors(
        focusedTextColor = ext.ink, unfocusedTextColor = ext.ink,
        focusedContainerColor = container, unfocusedContainerColor = container,
        disabledContainerColor = container, errorContainerColor = container,
        cursorColor = ext.ink,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        focusedLabelColor = ext.inkSecondary, unfocusedLabelColor = ext.inkSecondary,
        focusedPlaceholderColor = ext.inkSecondary, unfocusedPlaceholderColor = ext.inkSecondary,
    )
}

internal val FieldShape = RoundedCornerShape(20.dp)

@ThemePreviews
@Composable
private fun BentoControlsPreview() = PreviewSurface {
    androidx.compose.foundation.layout.Column(
        Modifier.padding(Dimens.Margin),
        verticalArrangement = Arrangement.spacedBy(Dimens.Gap),
    ) {
        PillSegmented(listOf("a" to "Heure de sonnerie", "b" to "Durée d'étape"), "a", {}, container = MaterialTheme.extended.tile)
        PillSegmented(listOf<Pair<Boolean?, String>>(null to "Hériter", true to "Oui", false to "Non"), true, {})
        ChoiceFlow {
            ChoicePill("Hériter", true, {})
            ChoicePill("Alarme système", false, {})
            ChoicePill("Silencieux", false, {})
        }
        SoftPill("Ouvrir", {}, Modifier.fillMaxWidth())
    }
}
