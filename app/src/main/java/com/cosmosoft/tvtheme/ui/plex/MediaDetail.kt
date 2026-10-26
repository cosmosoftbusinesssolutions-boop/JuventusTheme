package com.cosmosoft.tvtheme.ui.plex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cosmosoft.tvtheme.PlayerActivity
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.plex.PlexItem
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.SafeHorizontal
import com.cosmosoft.tvtheme.ui.SafeVertical
import com.cosmosoft.tvtheme.ui.SectionTitle
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen page for a movie, show or episode. Opening an episode from a show stacks another page on top. */
@Composable
fun MediaDetailDialog(item: PlexItem, session: PlexSession, refreshKey: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        MediaDetail(item, session, refreshKey)
    }
}

private class DetailData(
    /** The richest version we found: from your server if it's there, otherwise from Plex's catalog. */
    val item: PlexItem,
    val onServer: Boolean,
    /** Null when the title can't go on the watchlist (episodes, or older items with no catalog id). */
    val onWatchlist: Boolean?,
    val seasons: List<PlexItem>,
)

private fun loadDetail(session: PlexSession, item: PlexItem): DetailData {
    val server = session.server
    val token = session.token
    val serverItem = runCatching {
        when {
            server == null -> null
            !item.fromWatchlist -> session.api.metadata(server, item.ratingKey)
            // A watchlist title is in Plex's catalog; look for the same title in your libraries.
            item.guid != null -> session.api.findByGuid(server, item.guid)?.let { session.api.metadata(server, it.ratingKey) }
            else -> null
        }
    }.getOrNull()
    val shown = serverItem
        ?: item.discoverKey?.let { key -> token?.let { runCatching { session.api.catalogMetadata(it, key) }.getOrNull() } }
        ?: item
    val seasons = if (server != null && serverItem?.type == "show") {
        runCatching { session.api.children(server, serverItem.ratingKey) }.getOrNull().orEmpty()
    } else {
        emptyList()
    }
    val canWatchlist = shown.type == "movie" || shown.type == "show"
    val onWatchlist = shown.discoverKey?.takeIf { canWatchlist }?.let { key ->
        token?.let { runCatching { session.api.isOnWatchlist(it, key) }.getOrNull() }
    }
    return DetailData(shown, serverItem != null, onWatchlist, seasons)
}

@Composable
private fun MediaDetail(item: PlexItem, session: PlexSession, refreshKey: Int) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var child by remember { mutableStateOf<PlexItem?>(null) }
    var selectedSeason by remember { mutableStateOf<PlexItem?>(null) }
    val firstAction = remember { FocusRequester() }

    val data by produceState<DetailData?>(null, item.ratingKey, refreshKey, reload) {
        value = withContext(Dispatchers.IO) { loadDetail(session, item) }
    }
    val shown = data?.item ?: item
    val season = selectedSeason ?: data?.seasons?.firstOrNull()
    val episodes by produceState(emptyList<PlexItem>(), season?.ratingKey, refreshKey, reload) {
        val server = session.server
        value = if (season != null && server != null) {
            withContext(Dispatchers.IO) { runCatching { session.api.children(server, season.ratingKey) }.getOrNull().orEmpty() }
        } else {
            emptyList()
        }
    }

    /** Runs a change on Plex, then reloads this page and the Home rows. */
    fun change(block: suspend () -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
            reload++
            session.contentChanged()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background.first()),
    ) {
        AsyncImage(
            model = session.imageUrl(shown.art ?: shown.grandparentArt, 1920, 1080),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.92f), Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        LazyColumn(
            contentPadding = PaddingValues(top = 64.dp, bottom = SafeVertical),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Column(
                    Modifier
                        .padding(start = SafeHorizontal * 2)
                        .fillMaxWidth(0.6f),
                ) {
                    if (shown.type == "episode" && shown.grandparentTitle != null) {
                        Text(shown.grandparentTitle, style = MaterialTheme.typography.titleLarge, color = theme.text.copy(alpha = 0.8f))
                    }
                    Text(
                        text = shown.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    MetaLine(shown)
                    if (shown.genres.isNotEmpty()) {
                        Text(shown.genres.take(4).joinToString(", "), color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = shown.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(24.dp))

                    val loaded = data
                    if (loaded == null) {
                        Text(stringResource(R.string.detail_loading), color = Color.White.copy(alpha = 0.7f))
                    } else {
                        Actions(
                            data = loaded,
                            firstAction = firstAction,
                            onPlay = { startMs -> context.startActivity(PlayerActivity.intent(context, loaded.item, startMs)) },
                            onToggleWatched = {
                                val server = session.server ?: return@Actions
                                change { session.api.setWatched(server, loaded.item.ratingKey, !loaded.item.isWatched) }
                            },
                            onToggleWatchlist = {
                                val token = session.token ?: return@Actions
                                val key = loaded.item.discoverKey ?: return@Actions
                                change { session.api.setOnWatchlist(token, key, add = loaded.onWatchlist != true) }
                            },
                        )
                        if (!loaded.onServer) {
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.detail_not_in_library), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    if (shown.cast.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.detail_starring, shown.cast.take(5).joinToString(", ")),
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val seasons = data?.seasons.orEmpty()
            if (seasons.size > 1) {
                item {
                    DetailRow(stringResource(R.string.detail_seasons)) {
                        items(seasons, key = { it.ratingKey }) { s ->
                            MediaCard(
                                item = s,
                                imageUrl = session.posterImage(s),
                                landscape = false,
                                onClick = { selectedSeason = s },
                                showHeading = false,
                            )
                        }
                    }
                }
            }
            if (episodes.isNotEmpty()) {
                item {
                    val title = season?.title?.takeIf { seasons.size > 1 } ?: stringResource(R.string.detail_episodes)
                    DetailRow(title) {
                        items(episodes, key = { it.ratingKey }) { episode ->
                            MediaCard(
                                item = episode,
                                imageUrl = session.landscapeImage(episode),
                                landscape = true,
                                onClick = { child = episode },
                                showHeading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    child?.let { MediaDetailDialog(it, session, refreshKey, onDismiss = { child = null }) }

    LaunchedEffect(data != null) {
        if (data != null) runCatching { firstAction.requestFocus() }
    }
}

@Composable
private fun MetaLine(item: PlexItem) {
    val parts = listOfNotNull(
        if (item.type == "episode" && item.parentIndex != null && item.index != null) {
            "Season ${item.parentIndex} · Episode ${item.index}"
        } else {
            item.year?.toString()
        },
        item.durationMs?.let { formatDuration(it) },
        item.contentRating,
        item.audienceRating?.let { "★ %.1f".format(it) },
    )
    if (parts.isNotEmpty()) {
        Text(parts.joinToString("   ·   "), style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun Actions(
    data: DetailData,
    firstAction: FocusRequester,
    onPlay: (startMs: Long) -> Unit,
    onToggleWatched: () -> Unit,
    onToggleWatchlist: () -> Unit,
) {
    val item = data.item
    // Whichever button comes first gets focus when the page opens.
    var focusAssigned = false
    fun focusFirst(): Modifier = if (!focusAssigned) {
        focusAssigned = true
        Modifier.focusRequester(firstAction)
    } else {
        Modifier
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (data.onServer && item.isPlayable) {
            val offset = item.viewOffsetMs?.takeIf { it > 0 }
            if (offset != null) {
                DetailButton(stringResource(R.string.detail_resume, formatTimestamp(offset)), Icons.Filled.PlayArrow, focusFirst()) {
                    onPlay(offset)
                }
                DetailButton(stringResource(R.string.detail_play_from_start), null, focusFirst()) { onPlay(0) }
            } else {
                DetailButton(stringResource(R.string.detail_play), Icons.Filled.PlayArrow, focusFirst()) { onPlay(0) }
            }
        }
        if (data.onServer) {
            val label = if (item.isWatched) R.string.detail_mark_unwatched else R.string.detail_mark_watched
            DetailButton(stringResource(label), null, focusFirst(), onToggleWatched)
        }
        if (data.onWatchlist != null) {
            val label = if (data.onWatchlist) R.string.detail_remove_watchlist else R.string.detail_add_watchlist
            DetailButton(stringResource(label), null, focusFirst(), onToggleWatchlist)
        }
    }
}

@Composable
private fun DetailButton(text: String, icon: ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun DetailRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(start = SafeHorizontal * 2))
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(start = SafeHorizontal * 2, end = SafeHorizontal, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) {
        stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

/** 1:02:03 or 12:34. */
private fun formatTimestamp(ms: Long): String {
    val total = ms / 1_000
    val h = total / 3_600
    val m = total % 3_600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
