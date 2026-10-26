package com.cosmosoft.tvtheme.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Glow
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

internal val CardShape = RoundedCornerShape(12.dp)
internal val CardWidth = 196.dp
internal val CardHeight = 110.dp

/** Width of the collapsed side menu. Screens draw under it and inset their content by [ContentStart]. */
internal val RailWidth = 80.dp
internal val ContentStart = RailWidth + 24.dp

/** TV-safe area: keeps content clear of overscan on older sets. */
internal val SafeHorizontal = 48.dp
internal val SafeVertical = 27.dp

@Composable
internal fun AppCard(app: LaunchableApp, onOptions: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    Column(modifier = Modifier.width(CardWidth)) {
        Card(
            onClick = { context.launch(app.launchIntent()) },
            onLongClick = onOptions,
            modifier = modifier.size(CardWidth, CardHeight),
            shape = CardDefaults.shape(shape = CardShape),
            colors = CardDefaults.colors(containerColor = theme.card, focusedContainerColor = theme.card),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = CardDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, theme.accent), shape = CardShape),
            ),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = theme.accent.copy(alpha = 0.55f), elevation = 14.dp)),
        ) {
            if (app.isTvApp) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(bitmap = app.image, contentDescription = app.label, modifier = Modifier.size(56.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium.copy(shadow = LabelShadow),
            color = theme.text.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(shadow = LabelShadow),
        fontWeight = FontWeight.SemiBold,
        color = LocalLauncherTheme.current.text,
        modifier = modifier,
    )
}

/** A full-width focusable row: a title, an optional subtitle, and optional trailing content. */
@Composable
internal fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val theme = LocalLauncherTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.card.copy(alpha = 0.7f),
            contentColor = theme.text,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Soft dark shadow so labels stay readable on bright stripes and photo wallpapers. */
internal val LabelShadow = Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 8f)

/**
 * Asks before something that can't be undone. Focus starts on Cancel, so a stray press of OK
 * on the remote doesn't confirm it.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(theme.card, RoundedCornerShape(16.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = theme.text)
            if (message != null) Text(message, color = theme.text.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            ListRow(stringResource(R.string.action_cancel), onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
            ListRow(confirmLabel, onClick = onConfirm)
        }
    }
    LaunchedEffect(Unit) { cancelFocus.requestFocusWhenReady() }
}

/**
 * Focuses a target that may not be laid out yet (a new page in a dialog, an animating sheet):
 * retries for a few frames instead of failing silently.
 */
internal suspend fun FocusRequester.requestFocusWhenReady() {
    repeat(10) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(30)
    }
}

/** Starts [intent], returning false if nothing on the device can handle it. */
internal fun Context.launch(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Opens a system settings screen, falling back to the main Settings app when a TV doesn't have it. */
internal fun Context.openSettings(action: String) {
    if (!launch(Intent(action))) launch(Intent(Settings.ACTION_SETTINGS))
}
