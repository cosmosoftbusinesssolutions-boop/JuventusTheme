package com.cosmosoft.tvtheme.plex

import java.util.UUID

/**
 * Plex endpoints used by the launcher. Every function blocks, so call them from Dispatchers.IO.
 *
 * - plex.tv: sign-in PINs, the account, and the list of servers.
 * - Your Plex Media Server: libraries, Continue Watching, metadata, watched state, playback.
 * - discover.provider.plex.tv: the watchlist, which lives in Plex's online catalog.
 */
class PlexApi(private val client: PlexClient) {

    // --- plex.tv account -------------------------------------------------------------------------

    fun createPin(): PlexPin {
        val json = org.json.JSONObject(client.request("POST", "$PLEX_TV/api/v2/pins?strong=false", token = null))
        return PlexPin(json.getLong("id"), json.getString("code"), json.optInt("expiresIn", 900))
    }

    /** Returns the account token once the code has been entered at plex.tv/link, or null until then. */
    fun checkPin(id: Long): String? =
        client.getObject("$PLEX_TV/api/v2/pins/$id", token = null).str("authToken")?.takeIf { it.isNotEmpty() }

    fun accountName(token: String): String? {
        val json = client.getObject("$PLEX_TV/api/v2/user", token)
        return json.str("friendlyName")?.takeIf { it.isNotEmpty() } ?: json.str("username")
    }

    /** Servers on the account that we can actually reach, each with its best working address. */
    fun servers(token: String): List<PlexServer> =
        client.getArray("$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1", token).objects()
            .filter { it.str("provides").orEmpty().split(',').contains("server") }
            .mapNotNull { resource ->
                val serverToken = resource.str("accessToken") ?: token
                // Prefer a direct local address, then a direct remote one, then Plex's relay.
                val uri = resource.optJSONArray("connections").objects()
                    .sortedBy {
                        when {
                            it.optBoolean("relay") -> 2
                            it.optBoolean("local") -> 0
                            else -> 1
                        }
                    }
                    .mapNotNull { it.str("uri") }
                    .firstOrNull { reachable(it, serverToken) }
                    ?: return@mapNotNull null
                PlexServer(
                    name = resource.str("name").orEmpty(),
                    machineId = resource.str("clientIdentifier").orEmpty(),
                    uri = uri,
                    token = serverToken,
                )
            }

    private fun reachable(uri: String, token: String) =
        runCatching { client.request("GET", "$uri/identity", token, timeoutMs = 4_000) }.isSuccess

    // --- Plex Media Server ----------------------------------------------------------------------

    fun libraries(server: PlexServer): List<PlexLibrary> =
        client.getObject("${server.uri}/library/sections", server.token).container("Directory")
            .map { PlexLibrary(it.str("key").orEmpty(), it.str("title").orEmpty(), it.str("type").orEmpty()) }
            .filter { it.type == "movie" || it.type == "show" }

    fun recentlyAdded(server: PlexServer, library: PlexLibrary, size: Int = 20): List<PlexItem> =
        items(server, "/library/sections/${library.key}/all?sort=addedAt:desc&X-Plex-Container-Start=0&X-Plex-Container-Size=$size")

    fun continueWatching(server: PlexServer): List<PlexItem> = try {
        items(server, "/hubs/continueWatching/items?X-Plex-Container-Start=0&X-Plex-Container-Size=20")
    } catch (e: PlexHttpException) {
        // Servers older than the Continue Watching hub only have On Deck.
        items(server, "/library/onDeck")
    }

    fun metadata(server: PlexServer, ratingKey: String): PlexItem? =
        items(server, "/library/metadata/$ratingKey").firstOrNull()

    /** Seasons of a show, or episodes of a season. */
    fun children(server: PlexServer, ratingKey: String): List<PlexItem> =
        items(server, "/library/metadata/$ratingKey/children")

    /** Finds a catalog (watchlist) title in your own libraries. */
    fun findByGuid(server: PlexServer, guid: String): PlexItem? =
        items(server, "/library/all?guid=${encode(guid)}").firstOrNull()

    fun setWatched(server: PlexServer, ratingKey: String, watched: Boolean) {
        val action = if (watched) "scrobble" else "unscrobble"
        client.request("GET", "${server.uri}/:/$action?identifier=com.plexapp.plugins.library&key=$ratingKey", server.token)
    }

    /** Tells the server where playback is, so Continue Watching and resume points stay in sync. */
    fun reportTimeline(server: PlexServer, ratingKey: String, state: String, positionMs: Long, durationMs: Long) {
        val key = encode("/library/metadata/$ratingKey")
        client.request(
            "GET",
            "${server.uri}/:/timeline?ratingKey=$ratingKey&key=$key&state=$state&time=$positionMs&duration=$durationMs",
            server.token,
        )
    }

    private fun items(server: PlexServer, path: String): List<PlexItem> =
        client.getObject("${server.uri}$path", server.token).container("Metadata").map { PlexItem.parse(it) }

    // --- Watchlist (Plex online catalog) ------------------------------------------------------------

    fun watchlist(token: String): List<PlexItem> =
        client.getObject("$DISCOVER/library/sections/watchlist/all?X-Plex-Container-Start=0&X-Plex-Container-Size=40", token)
            .container("Metadata").map { PlexItem.parse(it, fromWatchlist = true) }

    fun catalogMetadata(token: String, discoverKey: String): PlexItem? =
        client.getObject("$DISCOVER/library/metadata/$discoverKey", token)
            .container("Metadata").firstOrNull()?.let { PlexItem.parse(it, fromWatchlist = true) }

    fun isOnWatchlist(token: String, discoverKey: String): Boolean {
        val container = client.getObject("$DISCOVER/library/metadata/$discoverKey/userState", token)
            .optJSONObject("MediaContainer") ?: return false
        // Plex returns UserState as either an object or a one-item list.
        val state = container.optJSONArray("UserState")?.optJSONObject(0) ?: container.optJSONObject("UserState")
        return state?.str("watchlistedAt") != null
    }

    fun setOnWatchlist(token: String, discoverKey: String, add: Boolean) {
        val action = if (add) "addToWatchlist" else "removeFromWatchlist"
        client.request("PUT", "$DISCOVER/actions/$action?ratingKey=$discoverKey", token)
    }

    // --- URLs ------------------------------------------------------------------------------------

    /** Artwork scaled by the server. Catalog items already carry absolute image URLs. */
    fun imageUrl(server: PlexServer?, path: String?, width: Int, height: Int): String? = when {
        path == null -> null
        path.startsWith("http") -> path
        server == null -> null
        else -> "${server.uri}/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1" +
            "&url=${encode(path)}&X-Plex-Token=${server.token}"
    }

    /** The original file, for players that can decode it as-is. */
    fun directPlayUrl(server: PlexServer, partKey: String) = "${server.uri}$partKey?X-Plex-Token=${server.token}"

    /** An HLS stream converted by the server, used when the original file won't play. */
    fun transcodeUrl(server: PlexServer, ratingKey: String): String {
        val params = listOf(
            "path" to encode("/library/metadata/$ratingKey"),
            "mediaIndex" to "0",
            "partIndex" to "0",
            "protocol" to "hls",
            "fastSeek" to "1",
            "directPlay" to "0",
            "directStream" to "1",
            "videoQuality" to "100",
            "maxVideoBitrate" to "20000",
            "location" to "lan",
            "session" to UUID.randomUUID().toString(),
            "X-Plex-Token" to server.token,
            "X-Plex-Client-Identifier" to client.clientId,
            "X-Plex-Product" to encode(PlexClient.PRODUCT),
            "X-Plex-Platform" to "Android",
        )
        return "${server.uri}/video/:/transcode/universal/start.m3u8?" + params.joinToString("&") { (k, v) -> "$k=$v" }
    }

    private companion object {
        const val PLEX_TV = "https://plex.tv"
        const val DISCOVER = "https://discover.provider.plex.tv"
    }
}
