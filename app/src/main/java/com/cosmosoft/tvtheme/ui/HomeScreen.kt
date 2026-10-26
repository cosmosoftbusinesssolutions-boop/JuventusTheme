package com.cosmosoft.tvtheme.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.plex.PlexItem
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.plex.MediaCard
import com.cosmosoft.tvtheme.ui.plex.PlexHome
import com.cosmosoft.tvtheme.ui.plex.landscapeImage
import com.cosmosoft.tvtheme.ui.plex.posterImage
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RowPadding = PaddingValues(start = ContentStart, end = SafeHorizontal, top = 8.dp, bottom = 8.dp)

private sealed interface HomeRow {
    val id: String
    val title: String

    class Apps(override val id: String, override val title: String, val apps: List<LaunchableApp>) : HomeRow

    class Media(override val id: String, override val title: String, val items: List<PlexItem>, val landscape: Boolean) :
        HomeRow
}

@Composable
fun HomeScreen(
    apps: List<LaunchableApp>?,
    settings: LauncherSettings,
    plex: PlexSession,
    plexHome: PlexHome?,
    /** Pressing Home still opens the TV's built-in launcher, so offer the setup guide. */
    needsSetup: Boolean,
    /** Bumps when the focused card disappeared (an app was hidden) and focus should return to the rows. */
    refocusKey: Int,
    onAppOptions: (LaunchableApp) -> Unit,
    onOpenMedia: (PlexItem) -> Unit,
    onSetup: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val visible = apps.orEmpty().filter { it.key !in settings.hidden }
    val byKey = visible.associateBy { it.key }
    val favorites = settings.favorites.mapNotNull { byKey[it] }
    val serverId = plex.server?.machineId

    val rows = buildList {
        if (settings.showFavorites && favorites.isNotEmpty()) {
            add(HomeRow.Apps("favorites", stringResource(R.string.row_favorites), favorites))
        }
        if (plexHome != null && settings.showContinueWatching && plexHome.continueWatching.isNotEmpty()) {
            add(HomeRow.Media("continue", stringResource(R.string.row_continue_watching), plexHome.continueWatching, landscape = true))
        }
        if (settings.showTvApps) {
            visible.filter { it.isTvApp }.takeIf { it.isNotEmpty() }
                ?.let { add(HomeRow.Apps("tv", stringResource(R.string.row_tv_apps), it)) }
        }
        if (plexHome != null && settings.showWatchlist && plexHome.watchlist.isNotEmpty()) {
            add(HomeRow.Media("watchlist", stringResource(R.string.row_watchlist), plexHome.watchlist, landscape = false))
        }
        plexHome?.libraries.orEmpty().forEach { (library, items) ->
            val id = "$serverId/${library.key}"
            if (id !in settings.hiddenLibraries && items.isNotEmpty()) {
                add(HomeRow.Media(id, stringResource(R.string.row_recently_added, library.title), items, landscape = false))
            }
        }
        if (settings.showOtherApps) {
            visible.filterNot { it.isTvApp }.takeIf { it.isNotEmpty() }
                ?.let { add(HomeRow.Apps("other", stringResource(R.string.row_other_apps), it)) }
        }
    }
    val firstCardFocus = remember { FocusRequester() }

    LazyColumn(
        contentPadding = PaddingValues(vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item { Clock() }
        if (needsSetup) {
            item {
                ListRow(
                    title = stringResource(R.string.home_setup_title),
                    subtitle = stringResource(R.string.home_setup_card_hint),
                    onClick = onSetup,
                    modifier = Modifier.padding(start = ContentStart, end = SafeHorizontal),
                )
            }
        }
        if (settings.showFavorites && favorites.isEmpty() && apps != null) {
            item {
                Column(Modifier.padding(start = ContentStart)) {
                    SectionTitle(stringResource(R.string.row_favorites))
                    Text(
                        text = stringResource(R.string.favorites_hint),
                        style = MaterialTheme.typography.bodyLarge.copy(shadow = LabelShadow),
                        color = theme.text.copy(alpha = 0.75f),
                    )
                }
            }
        }
        items(rows, key = { it.id }) { row ->
            val focus = firstCardFocus.takeIf { row == rows.first() }
            when (row) {
                is HomeRow.Apps -> AppRow(row, onAppOptions, focus)
                is HomeRow.Media -> MediaRow(row, plex, onOpenMedia, focus)
            }
        }
        if (apps?.isEmpty() == true) {
            item {
                Text(
                    text = stringResource(R.string.no_apps),
                    color = theme.text,
                    modifier = Modifier.padding(start = ContentStart),
                )
            }
        }
    }

    LaunchedEffect(rows.isNotEmpty(), refocusKey) {
        if (rows.isNotEmpty()) firstCardFocus.requestFocusWhenReady()
    }
}

@Composable
private fun Clock() {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val dateFormat = remember {
        val locale = Locale.getDefault()
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)
    }
    Column(Modifier.padding(start = ContentStart)) {
        Text(
            text = timeFormat.format(now),
            style = MaterialTheme.typography.displayMedium.copy(shadow = LabelShadow),
            fontWeight = FontWeight.Light,
            color = theme.text,
        )
        Text(
            text = dateFormat.format(now),
            style = MaterialTheme.typography.titleMedium.copy(shadow = LabelShadow),
            color = theme.text.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(12.dp))
        // Short accent bar under the date, in the theme's highlight color.
        Box(
            Modifier
                .width(56.dp)
                .height(4.dp)
                .background(theme.accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun AppRow(row: HomeRow.Apps, onAppOptions: (LaunchableApp) -> Unit, firstCardFocus: FocusRequester?) {
    Column {
        SectionTitle(row.title, Modifier.padding(start = ContentStart))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(row.apps, key = { it.key }) { app ->
                val focus = if (firstCardFocus != null && app == row.apps.first()) {
                    Modifier.focusRequester(firstCardFocus)
                } else {
                    Modifier
                }
                AppCard(app, onOptions = { onAppOptions(app) }, modifier = focus)
            }
        }
    }
}

@Composable
private fun MediaRow(row: HomeRow.Media, plex: PlexSession, onOpen: (PlexItem) -> Unit, firstCardFocus: FocusRequester?) {
    Column {
        SectionTitle(row.title, Modifier.padding(start = ContentStart))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = RowPadding,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(row.items, key = { it.ratingKey }) { item ->
                val focus = if (firstCardFocus != null && item == row.items.first()) {
                    Modifier.focusRequester(firstCardFocus)
                } else {
                    Modifier
                }
                MediaCard(
                    item = item,
                    imageUrl = if (row.landscape) plex.landscapeImage(item) else plex.posterImage(item),
                    landscape = row.landscape,
                    onClick = { onOpen(item) },
                    modifier = focus,
                )
            }
        }
    }
}
