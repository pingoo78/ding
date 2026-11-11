package fr.pingooweb.ding.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.pingooweb.ding.R
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended

/**
 * Pavé numérique Bento type horloge : les chiffres remplissent h:mm:ss depuis la droite (voir `TimeFormat`).
 * Touches pilule de 64 dp. Appui long sur « effacer » = tout effacer.
 * [keyColor] : fond des touches (tileRaised par défaut ; passer un fond en creux sur une tuile blanche).
 */
@Composable
fun DigitKeypad(
    onDigit: (Int) -> Unit,
    onDoubleZero: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color = MaterialTheme.extended.tileRaised,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    KeyButton(Modifier.weight(1f), keyColor, onClick = { onDigit(d) }) { KeyLabel(d.toString()) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(Modifier.weight(1f), keyColor, onClick = onDoubleZero) { KeyLabel("00") }
            KeyButton(Modifier.weight(1f), keyColor, onClick = { onDigit(0) }) { KeyLabel("0") }
            KeyButton(
                Modifier.weight(1f),
                keyColor,
                onClick = onBackspace,
                onLongClick = onClear,
                description = stringResource(R.string.keypad_backspace),
                longClickLabel = stringResource(R.string.keypad_clear),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, tint = MaterialTheme.extended.ink)
            }
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"),
        color = MaterialTheme.extended.ink,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(
    modifier: Modifier,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    description: String? = null,
    longClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(Dimens.ButtonL)
            .clip(CircleShape)
            .background(color)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}
