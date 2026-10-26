package com.cosmosoft.tvtheme.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

/**
 * Long-press menu for an app: favorites, ordering, hiding, and system app info.
 * In a [restricted] profile (until the main PIN is entered) only Open is offered.
 */
@Composable
fun AppOptionsDialog(
    app: LaunchableApp,
    settings: LauncherSettings,
    restricted: Boolean,
    onHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val firstOption = remember { FocusRequester() }
    val isFavorite = settings.isFavorite(app.key)
    val favoriteIndex = settings.favorites.indexOf(app.key)

    // Every option closes the menu after it runs.
    fun option(action: () -> Unit): () -> Unit = {
        action()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(440.dp)
                .background(theme.card, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = app.image, contentDescription = null, modifier = Modifier.size(width = 64.dp, height = 36.dp))
                Spacer(Modifier.width(16.dp))
                Text(app.label, style = MaterialTheme.typography.titleLarge, color = theme.text)
            }
            Spacer(Modifier.height(6.dp))
            ListRow(
                title = stringResource(R.string.options_open),
                onClick = option { context.launch(app.launchIntent()) },
                modifier = Modifier.focusRequester(firstOption),
            )
            if (!restricted) {
                ListRow(
                    title = stringResource(if (isFavorite) R.string.options_remove_favorite else R.string.options_add_favorite),
                    onClick = option { settings.toggleFavorite(app.key) },
                )
                if (isFavorite && favoriteIndex > 0) {
                    ListRow(
                        title = stringResource(R.string.options_move_left),
                        onClick = option { settings.moveFavorite(app.key, -1) },
                    )
                }
                if (isFavorite && favoriteIndex < settings.favorites.lastIndex) {
                    ListRow(
                        title = stringResource(R.string.options_move_right),
                        onClick = option { settings.moveFavorite(app.key, 1) },
                    )
                }
                ListRow(
                    title = stringResource(R.string.options_hide),
                    onClick = option {
                        settings.setHidden(app.key, true)
                        onHidden()
                    },
                )
                ListRow(
                    title = stringResource(R.string.options_app_info),
                    onClick = option {
                        context.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", app.component.packageName, null),
                            ),
                        )
                    },
                )
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstOption.requestFocus() } }
}
