package fr.pingooweb.ding.ui.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.pingooweb.ding.R
import fr.pingooweb.ding.domain.model.SoundChoice

/** Tri-état Hériter / Oui / Non (null = hérite), contrôle segmenté pilule. */
@Composable
fun TriStateRow(value: Boolean?, onChange: (Boolean?) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf<Pair<Boolean?, String>>(
        null to stringResource(R.string.tri_inherit),
        true to stringResource(R.string.tri_yes),
        false to stringResource(R.string.tri_no),
    )
    PillSegmented(options, value, onChange, modifier)
}

/** Choix du son en pilules. [value] null = hérite (si [allowInherit]). */
@Composable
fun SoundChooser(
    value: SoundChoice?,
    allowInherit: Boolean,
    onSelect: (SoundChoice?) -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceFlow(modifier) {
        if (allowInherit) {
            ChoicePill(stringResource(R.string.tri_inherit), value == null, { onSelect(null) })
        }
        ChoicePill(stringResource(R.string.sound_system), value == SoundChoice.SystemAlarm, { onSelect(SoundChoice.SystemAlarm) })
        ChoicePill(
            text = (value as? SoundChoice.Custom)?.title?.ifBlank { null } ?: stringResource(R.string.sound_choose),
            selected = value is SoundChoice.Custom,
            onClick = onPick,
            leadingIcon = Icons.Rounded.MusicNote,
        )
        ChoicePill(stringResource(R.string.sound_silent), value == SoundChoice.Silent, { onSelect(SoundChoice.Silent) })
    }
}
