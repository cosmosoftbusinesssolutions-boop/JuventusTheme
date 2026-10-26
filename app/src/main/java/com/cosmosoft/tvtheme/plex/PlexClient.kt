package com.cosmosoft.tvtheme.plex

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class PlexHttpException(val code: Int) : IOException("Plex request failed with HTTP $code")

/** Small blocking HTTP helper that adds the headers every Plex request needs. Call it off the main thread. */
class PlexClient(val clientId: String) {

    fun headers(token: String?): Map<String, String> = buildMap {
        put("Accept", "application/json")
        put("X-Plex-Product", PRODUCT)
        put("X-Plex-Version", VERSION)
        put("X-Plex-Client-Identifier", clientId)
        put("X-Plex-Platform", "Android")
        put("X-Plex-Device", Build.MODEL)
        put("X-Plex-Device-Name", "$PRODUCT (${Build.MODEL})")
        if (token != null) put("X-Plex-Token", token)
    }

    fun request(method: String, url: String, token: String?, timeoutMs: Int = 15_000): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            headers(token).forEach(connection::setRequestProperty)
            if (method == "POST" || method == "PUT") {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.close()
            }
            val code = connection.responseCode
            if (code !in 200..299) throw PlexHttpException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun getObject(url: String, token: String?, timeoutMs: Int = 15_000): JSONObject =
        JSONObject(request("GET", url, token, timeoutMs).ifBlank { "{}" })

    fun getArray(url: String, token: String?): JSONArray = JSONArray(request("GET", url, token).ifBlank { "[]" })

    companion object {
        const val PRODUCT = "TV Theme"
        const val VERSION = "1.0"
    }
}

internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

internal fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null

internal fun JSONObject.int(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null

internal fun JSONObject.long(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

/** The `MediaContainer.<name>` list that almost every Plex endpoint returns. */
internal fun JSONObject.container(name: String): List<JSONObject> =
    optJSONObject("MediaContainer")?.optJSONArray(name).objects()
