package fr.pingooweb.ding.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import fr.pingooweb.ding.domain.TimeFormat
import fr.pingooweb.ding.ui.theme.TimerDigitsSmall

/** Affiche une durée en chiffres tabulaires ("m:ss" / "h:mm:ss"). */
@Composable
fun TimeText(
    ms: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = TimerDigitsSmall,
    color: Color = LocalContentColor.current,
    roundUp: Boolean = true,
    prefix: String = "",
    textAlign: TextAlign? = null,
) {
    Text(
        text = prefix + TimeFormat.format(ms, roundUp),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}
