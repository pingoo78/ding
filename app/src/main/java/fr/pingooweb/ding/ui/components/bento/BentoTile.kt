package fr.pingooweb.ding.ui.components.bento

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.ProgramColors
import fr.pingooweb.ding.ui.theme.extended

/**
 * Tuile Bento : fond [color] (tile par défaut, ou `program(i).container`), rayon 28 dp, sans ombre ni bordure.
 * Le texte dessus est l'encre du thème. [onClick] optionnel (la tuile devient alors un bouton accessible).
 */
@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.extended.tile,
    contentColor: Color = MaterialTheme.extended.ink,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(Dimens.TilePadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.TileRadius)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics {
                role = Role.Button
                if (onClickLabel != null) semanticsOnClick(label = onClickLabel, action = null)
            },
            shape = shape, color = color, contentColor = contentColor,
            content = inner,
        )
    } else {
        Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, content = inner)
    }
}

/** Variante pastel d'un programme. */
@Composable
fun BentoTile(
    program: ProgramColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: PaddingValues = PaddingValues(Dimens.TilePadding),
    content: @Composable ColumnScope.() -> Unit,
) = BentoTile(modifier, program.container, MaterialTheme.extended.ink, onClick, onClickLabel, padding, content)

@ThemePreviews
@Composable
private fun BentoTilePreview() = PreviewSurface {
    androidx.compose.foundation.layout.Column(
        Modifier.padding(Dimens.Margin),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Gap),
    ) {
        BentoTile { Text("Tuile blanche", style = MaterialTheme.typography.titleMedium) }
        BentoTile(MaterialTheme.extended.program(0), onClick = {}) {
            Text("Tuile lavande cliquable", style = MaterialTheme.typography.titleMedium)
        }
        BentoTile(MaterialTheme.extended.program(2)) { Text("Pêche", style = MaterialTheme.typography.titleMedium) }
    }
}
