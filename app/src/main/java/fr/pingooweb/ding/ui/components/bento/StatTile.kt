package fr.pingooweb.ding.ui.components.bento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.TimerMedium
import fr.pingooweb.ding.ui.theme.extended

/**
 * Tuile statistique : [label] (13/600, secondaire) + [value] (par défaut 28/800 chiffres tabulaires)
 * + [detail] optionnel (13/600 secondaire, ex. « dans 6:00 »).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueStyle: TextStyle = TimerMedium,
    color: Color = MaterialTheme.extended.tile,
    valueMaxLines: Int = 1,
) {
    BentoTile(
        modifier = modifier,
        color = color,
        padding = PaddingValues(horizontal = Dimens.TilePadding, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.extended.inkSecondary)
        Text(value, style = valueStyle, maxLines = valueMaxLines, overflow = TextOverflow.Ellipsis)
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.extended.inkSecondary,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun StatTilePreview() = PreviewSurface {
    Row(Modifier.padding(Dimens.Margin).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        StatTile("Écoulé", "12:24", Modifier.weight(1f))
        StatTile(
            "Ensuite", "Arroser le rôti", Modifier.weight(1f), detail = "dans 6:00",
            valueStyle = MaterialTheme.typography.titleSmall, valueMaxLines = 2,
        )
    }
}
