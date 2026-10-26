package com.cosmosoft.tvtheme.plex

import org.json.JSONObject

/** A Plex Media Server and the connection that answered fastest when we tested it. */
data class PlexServer(val name: String, val machineId: String, val uri: String, val token: String)

data class PlexLibrary(val key: String, val title: String, val type: String)

class PlexPin(val id: Long, val code: String, val expiresInSeconds: Int)

/**
 * One movie, show, season or episode. Items from the watchlist come from Plex's online catalog,
 * not your server: [fromWatchlist] is true and their [ratingKey] is the catalog id.
 */
data class PlexItem(
    val ratingKey: String,
    val type: String,
    val title: String,
    val year: Int?,
    val summary: String,
    val thumb: String?,
    val art: String?,
    val grandparentTitle: String?,
    val grandparentThumb: String?,
    val grandparentArt: String?,
    val parentIndex: Int?,
    val index: Int?,
    val durationMs: Long?,
    val viewOffsetMs: Long?,
    val viewCount: Int,
    val leafCount: Int?,
    val viewedLeafCount: Int?,
    val guid: String?,
    val contentRating: String?,
    val audienceRating: Double?,
    val genres: List<String>,
    val cast: List<String>,
    val partKey: String?,
    val fromWatchlist: Boolean,
) {
    val isPlayable get() = type == "movie" || type == "episode"

    val isWatched: Boolean
        get() = if (leafCount != null && type != "movie" && type != "episode") {
            leafCount > 0 && viewedLeafCount == leafCount
        } else {
            viewCount > 0
        }

    /** How far into the item playback stopped, from 0 to 1, or null if it hasn't been started. */
    val progress: Float?
        get() {
            val offset = viewOffsetMs ?: return null
            val duration = durationMs?.takeIf { it > 0 } ?: return null
            return (offset.toFloat() / duration).coerceIn(0f, 1f)
        }

    /** Id in Plex's online catalog, used for the watchlist. Only modern `plex://` guids have one. */
    val discoverKey: String?
        get() = guid?.takeIf { it.startsWith("plex://") }?.substringAfterLast('/')

    /** Episodes are shown under their show's name. */
    val heading: String get() = if (type == "episode") grandparentTitle ?: title else title

    val subheading: String?
        get() = when (type) {
            "episode" -> listOfNotNull(
                if (parentIndex != null && index != null) "S$parentIndex · E$index" else null,
                title,
            ).joinToString(" · ")
            else -> year?.toString()
        }

    companion object {
        fun parse(json: JSONObject, fromWatchlist: Boolean = false) = PlexItem(
            ratingKey = json.str("ratingKey").orEmpty(),
            type = json.str("type").orEmpty(),
            title = json.str("title").orEmpty(),
            year = json.int("year"),
            summary = json.str("summary").orEmpty(),
            thumb = json.str("thumb"),
            art = json.str("art"),
            grandparentTitle = json.str("grandparentTitle"),
            grandparentThumb = json.str("grandparentThumb"),
            grandparentArt = json.str("grandparentArt"),
            parentIndex = json.int("parentIndex"),
            index = json.int("index"),
            durationMs = json.long("duration"),
            viewOffsetMs = json.long("viewOffset"),
            viewCount = json.int("viewCount") ?: 0,
            leafCount = json.int("leafCount"),
            viewedLeafCount = json.int("viewedLeafCount"),
            guid = json.str("guid"),
            contentRating = json.str("contentRating"),
            audienceRating = if (json.has("audienceRating")) json.optDouble("audienceRating") else null,
            genres = json.optJSONArray("Genre").objects().mapNotNull { it.str("tag") },
            cast = json.optJSONArray("Role").objects().mapNotNull { it.str("tag") },
            partKey = json.optJSONArray("Media").objects().firstOrNull()
                ?.optJSONArray("Part").objects().firstOrNull()?.str("key"),
            fromWatchlist = fromWatchlist,
        )
    }
}
