package fr.pingooweb.ding.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.pingooweb.ding.R
import fr.pingooweb.ding.ui.theme.Dimens
import fr.pingooweb.ding.ui.theme.extended

/**
 * Dialogue de confirmation Bento (fond tile, rayon 28, boutons pilule).
 * [destructive] : le bouton de confirmation passe en couleur d'erreur.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel),
    destructive: Boolean = false,
) {
    val ext = MaterialTheme.extended
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.TileRadius),
        containerColor = ext.tile,
        titleContentColor = ext.ink,
        textContentColor = ext.inkSecondary,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            DialogPill(
                confirmLabel, onConfirm,
                container = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                content = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            )
        },
        dismissButton = {
            DialogPill(dismissLabel, onDismiss, container = MaterialTheme.colorScheme.background, content = ext.ink)
        },
    )
}

@Composable
private fun DialogPill(text: String, onClick: () -> Unit, container: Color, content: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TargetSize)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
