package com.cosmosoft.tvtheme.ui.plex

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.cosmosoft.tvtheme.plex.PlexItem
import com.cosmosoft.tvtheme.plex.PlexLibrary
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.CardHeight
import com.cosmosoft.tvtheme.ui.CardShape
import com.cosmosoft.tvtheme.ui.CardWidth
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal val PosterWidth = 140.dp
internal val PosterHeight = 210.dp

/** Everything Plex contributes to the Home screen. */
class PlexHome(
    val continueWatching: List<PlexItem>,
    val watchlist: List<PlexItem>,
    val libraries: List<Pair<PlexLibrary, List<PlexItem>>>,
)

/**
 * Loads the Plex rows, or null when signed out. A failing section is left empty rather than
 * failing the whole screen. The previous result stays on screen while a refresh loads.
 */
@Composable
fun rememberPlexHome(session: PlexSession, refreshKey: Int): PlexHome? {
    val home by produceState<PlexHome?>(null, session.token, session.server, session.contentVersion, refreshKey) {
        val token = session.token
        if (token == null) {
            value = null
            return@produceState
        }
        val server = session.server
        value = withContext(Dispatchers.IO) {
            coroutineScope {
                val continueWatching = async {
                    server?.let { runCatching { session.api.continueWatching(it) }.getOrNull() }.orEmpty()
                }
                val watchlist = async { runCatching { session.api.watchlist(token) }.getOrNull().orEmpty() }
                val libraries = async {
                    server?.let { s ->
                        runCatching { session.api.libraries(s) }.getOrNull().orEmpty().map { library ->
                            library to runCatching { session.api.recentlyAdded(s, library) }.getOrNull().orEmpty()
                        }
                    }.orEmpty()
                }
                PlexHome(continueWatching.await(), watchlist.await(), libraries.await())
            }
        }
    }
    return home
}

/** Landscape art for a wide card: an episode's still, or a movie's or show's background. */
internal fun PlexSession.landscapeImage(item: PlexItem, width: Int = 400, height: Int = 225) =
    imageUrl(if (item.type == "episode") item.thumb ?: item.art else item.art ?: item.thumb, width, height)

/** Poster art for a tall card. Episodes use their show's poster. */
internal fun PlexSession.posterImage(item: PlexItem, width: Int = 280, height: Int = 420) =
    imageUrl(if (item.type == "episode") item.grandparentThumb ?: item.thumb else item.thumb, width, height)

@Composable
fun MediaCard(
    item: PlexItem,
    imageUrl: String?,
    landscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
) {
    val theme = LocalLauncherTheme.current
    val width: Dp = if (landscape) CardWidth else PosterWidth
    val height: Dp = if (landscape) CardHeight else PosterHeight
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.size(width, height),
            shape = CardDefaults.shape(shape = CardShape),
            colors = CardDefaults.colors(containerColor = theme.card, focusedContainerColor = theme.card),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                focusedBorder = Border(border = BorderStroke(3.dp, theme.accent), shape = CardShape),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Title underneath shows until (or if) the artwork loads.
                Text(
                    text = item.heading,
                    color = theme.text.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp),
                )
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.heading,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item.progress?.let { progress ->
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(theme.accent),
                        )
                    }
                }
                if (item.isWatched) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = theme.background.first(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(theme.accent, CircleShape)
                            .padding(3.dp)
                            .size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (showHeading) item.heading else item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.text.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val secondary = if (showHeading) item.subheading else item.subheading?.substringBeforeLast(" · ")
        if (secondary != null) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = theme.text.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
