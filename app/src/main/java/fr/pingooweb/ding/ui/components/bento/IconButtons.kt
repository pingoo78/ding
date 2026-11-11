package fr.pingooweb.ding.ui.components.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended

/**
 * Pastille ronde (40 dp par défaut) portant une icône. Décorative par défaut ([contentDescription] null).
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    container: Color = MaterialTheme.extended.tileRaised,
    tint: Color = MaterialTheme.extended.ink,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Bouton icône rond 48 dp ; [contentDescription] obligatoire. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.extended.tileRaised,
    tint: Color = MaterialTheme.extended.ink,
    enabled: Boolean = true,
    size: Dp = Dimens.TargetSize,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun IconButtonsPreview() = PreviewSurface {
    Row(Modifier.padding(Dimens.Margin), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        IconBadge(Icons.Rounded.NotificationsActive, container = MaterialTheme.extended.program(2).container, tint = MaterialTheme.extended.attentionAction)
        RoundIconButton(Icons.Rounded.Settings, "Réglages", {})
        RoundIconButton(
            Icons.Rounded.PlayArrow, "Lecture", {},
            container = MaterialTheme.extended.program(0).strong,
            tint = MaterialTheme.extended.program(0).onStrong,
        )
    }
}
