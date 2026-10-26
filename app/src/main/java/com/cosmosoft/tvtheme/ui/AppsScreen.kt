package com.cosmosoft.tvtheme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

/** Every launchable app (except hidden ones) in a grid, A to Z. */
@Composable
fun AppsScreen(
    apps: List<LaunchableApp>?,
    settings: LauncherSettings,
    /** Bumps when the focused card disappeared (an app was hidden) and focus should return to the grid. */
    refocusKey: Int,
    onAppOptions: (LaunchableApp) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val visible = apps.orEmpty().filter { it.key !in settings.hidden }
    val firstCardFocus = remember { FocusRequester() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(CardWidth),
        contentPadding = PaddingValues(start = ContentStart, end = SafeHorizontal, top = SafeVertical, bottom = SafeVertical),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp)) {
                SectionTitle(stringResource(R.string.apps_title))
                Text(
                    text = stringResource(R.string.apps_hint, visible.size),
                    color = theme.text.copy(alpha = 0.7f),
                )
            }
        }
        items(visible, key = { it.key }) { app ->
            val focus = if (app == visible.first()) Modifier.focusRequester(firstCardFocus) else Modifier
            AppCard(app, onOptions = { onAppOptions(app) }, modifier = focus)
        }
    }

    LaunchedEffect(visible.isNotEmpty(), refocusKey) {
        if (visible.isNotEmpty()) firstCardFocus.requestFocusWhenReady()
    }
}
