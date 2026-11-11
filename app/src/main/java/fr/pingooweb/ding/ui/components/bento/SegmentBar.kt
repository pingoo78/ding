package fr.pingooweb.ding.ui.components.bento

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.pingooweb.ding.ui.components.PreviewSurface
import fr.pingooweb.ding.ui.components.ThemePreviews
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended

enum class SegmentState { Done, Current, Upcoming }

/**
 * Un segment de la frise : [weight] = durée (proportionnelle), [fraction] = avancement 0..1 (utile si Current),
 * [marker] = l'étape attend une validation (cercle évidé au-dessus de la fin du segment).
 */
@Immutable
data class BarSegment(
    val weight: Float,
    val state: SegmentState = SegmentState.Upcoming,
    val fraction: Float = 0f,
    val marker: Boolean = false,
)

/**
 * Frise horizontale segmentée proportionnelle ; segments arrondis séparés de 3 dp.
 * [fillColor] = couleur forte du programme, [trackColor] = piste.
 */
@Composable
fun SegmentBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.extended.track,
    markerColor: Color = MaterialTheme.extended.ink,
    barHeight: Dp = 12.dp,
    contentDescription: String? = null,
) {
    val hasMarker = segments.any { it.marker }
    val markerSpace = if (hasMarker) 14.dp else 0.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .height(barHeight + markerSpace)
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier,
            ),
    ) {
        if (segments.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val h = barHeight.toPx()
        val top = markerSpace.toPx()
        val total = segments.sumOf { it.weight.coerceAtLeast(0.0001f).toDouble() }.toFloat()
        val usable = (size.width - gap * (segments.size - 1)).coerceAtLeast(1f)
        val radius = CornerRadius(h / 2f)
        val markerR = 5.dp.toPx()
        var x = 0f
        for (s in segments) {
            val w = usable * s.weight.coerceAtLeast(0.0001f) / total
            val rr = RoundRect(x, top, x + w, top + h, radius)
            drawRoundRect(trackColor, Offset(x, top), Size(w, h), radius)
            val frac = when (s.state) {
                SegmentState.Done -> 1f
                SegmentState.Current -> s.fraction.coerceIn(0f, 1f)
                SegmentState.Upcoming -> 0f
            }
            if (frac > 0f) {
                clipPath(Path().apply { addRoundRect(rr) }) {
                    drawRect(fillColor, Offset(x, top), Size(w * frac, h))
                }
            }
            if (s.marker) {
                val cx = (x + w - markerR - 1.dp.toPx()).coerceAtLeast(markerR + 1f)
                drawCircle(markerColor, markerR - 1.dp.toPx(), Offset(cx, markerR + 1.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            x += w + gap
        }
    }
}

@ThemePreviews
@Composable
private fun SegmentBarPreview() = PreviewSurface {
    val p = MaterialTheme.extended.program(0)
    Column(Modifier.padding(Dimens.Margin), verticalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
        SegmentBar(
            listOf(
                BarSegment(5f, SegmentState.Done),
                BarSegment(7f, SegmentState.Current, 0.4f, marker = true),
                BarSegment(6f, marker = true),
                BarSegment(5f),
                BarSegment(7f),
            ),
            fillColor = p.strong,
        )
        SegmentBar(listOf(BarSegment(1f), BarSegment(3f), BarSegment(2f)), fillColor = p.strong)
    }
}
