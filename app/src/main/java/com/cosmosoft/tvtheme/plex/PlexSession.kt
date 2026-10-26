package com.cosmosoft.tvtheme.plex

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

/** The signed-in Plex account and chosen server, saved to SharedPreferences and exposed as Compose state. */
class PlexSession(context: Context) {
    private val prefs = context.getSharedPreferences("plex", Context.MODE_PRIVATE)

    val api = PlexApi(PlexClient(clientId()))

    var token by mutableStateOf(prefs.getString(KEY_TOKEN, null))
        private set
    var accountName by mutableStateOf(prefs.getString(KEY_ACCOUNT, null))
        private set
    var server by mutableStateOf(loadServer())
        private set

    /** Bumped after we change something on Plex (watched state, watchlist) so rows reload. */
    var contentVersion by mutableIntStateOf(0)
        private set

    val isSignedIn get() = token != null

    fun signIn(token: String, accountName: String?) {
        this.token = token
        this.accountName = accountName
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_ACCOUNT, accountName).apply()
    }

    fun selectServer(server: PlexServer) {
        this.server = server
        prefs.edit()
            .putString(KEY_SERVER_NAME, server.name)
            .putString(KEY_SERVER_ID, server.machineId)
            .putString(KEY_SERVER_URI, server.uri)
            .putString(KEY_SERVER_TOKEN, server.token)
            .apply()
    }

    fun signOut() {
        token = null
        accountName = null
        server = null
        prefs.edit().clear().putString(KEY_CLIENT_ID, clientId()).apply()
    }

    fun contentChanged() {
        contentVersion++
    }

    fun imageUrl(path: String?, width: Int, height: Int) = api.imageUrl(server, path, width, height)

    private fun loadServer(): PlexServer? {
        val uri = prefs.getString(KEY_SERVER_URI, null) ?: return null
        return PlexServer(
            name = prefs.getString(KEY_SERVER_NAME, null).orEmpty(),
            machineId = prefs.getString(KEY_SERVER_ID, null).orEmpty(),
            uri = uri,
            token = prefs.getString(KEY_SERVER_TOKEN, null) ?: return null,
        )
    }

    /** Plex identifies each device by a stable id; it's created once and kept across sign-outs. */
    private fun clientId(): String = prefs.getString(KEY_CLIENT_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_CLIENT_ID, it).apply() }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN = "token"
        const val KEY_ACCOUNT = "account"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_URI = "server_uri"
        const val KEY_SERVER_TOKEN = "server_token"
    }
}
