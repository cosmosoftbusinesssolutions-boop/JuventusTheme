package com.cosmosoft.tvtheme.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Highest content rating a restricted profile may see on Plex. */
enum class RatingLimit { LittleKids, OlderKids, Teens, Everyone }

/** US movie and TV ratings mapped to the [RatingLimit] that first allows them. */
private val ratingLevels = mapOf(
    "G" to RatingLimit.LittleKids, "TV-Y" to RatingLimit.LittleKids, "TV-G" to RatingLimit.LittleKids,
    "PG" to RatingLimit.OlderKids, "TV-Y7" to RatingLimit.OlderKids, "TV-Y7-FV" to RatingLimit.OlderKids,
    "TV-PG" to RatingLimit.OlderKids,
    "PG-13" to RatingLimit.Teens, "TV-14" to RatingLimit.Teens,
    "R" to RatingLimit.Everyone, "NC-17" to RatingLimit.Everyone, "TV-MA" to RatingLimit.Everyone,
)

/** Unrated or unrecognized titles count as adult, so they only show when everything is allowed. */
fun RatingLimit.allows(contentRating: String?): Boolean {
    if (this == RatingLimit.Everyone) return true
    val level = contentRating?.uppercase()?.substringAfter('/')?.let(ratingLevels::get) ?: return false
    return level <= this
}

data class Profile(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val pinHash: String?,
    /** Restricted profiles can't change settings without the main profile's PIN and only see allowed content. */
    val restricted: Boolean,
    val ratingLimit: RatingLimit,
) {
    val isMain get() = id == MAIN_ID
    val hasPin get() = pinHash != null

    companion object {
        /** The first profile. It can't be deleted, and its settings use the original "launcher" file. */
        const val MAIN_ID = "main"
    }
}

/** Everyone who uses this TV. Each profile keeps its own [LauncherSettings]. */
class ProfileStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    var profiles by mutableStateOf(load())
        private set

    var currentId by mutableStateOf(
        prefs.getString(KEY_CURRENT, null)?.takeIf { id -> profiles.any { it.id == id } } ?: Profile.MAIN_ID,
    )
        private set

    /** Show "Who's watching?" when the launcher starts. Only matters with more than one profile. */
    var askOnStart: Boolean
        get() = askOnStartState
        set(value) {
            askOnStartState = value
            prefs.edit().putBoolean(KEY_ASK_ON_START, value).apply()
        }
    private var askOnStartState by mutableStateOf(prefs.getBoolean(KEY_ASK_ON_START, true))

    val current: Profile get() = profiles.first { it.id == currentId }
    val main: Profile get() = profiles.first { it.isMain }

    fun select(id: String) {
        if (profiles.none { it.id == id }) return
        currentId = id
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun add(name: String): Profile {
        val profile = Profile(
            id = "p" + System.currentTimeMillis().toString(36),
            name = name,
            colorIndex = profiles.size,
            pinHash = null,
            restricted = false,
            ratingLimit = RatingLimit.Everyone,
        )
        save(profiles + profile)
        return profile
    }

    fun update(profile: Profile) = save(profiles.map { if (it.id == profile.id) profile else it })

    fun delete(id: String) {
        if (id == Profile.MAIN_ID) return
        if (currentId == id) select(Profile.MAIN_ID)
        save(profiles.filterNot { it.id == id })
        context.deleteSharedPreferences(LauncherSettings.prefsName(id))
        Wallpapers.customFile(context, id).delete()
    }

    /** Sets or (with null) removes a profile's 4-digit PIN. */
    fun setPin(id: String, pin: String?) {
        val profile = profiles.firstOrNull { it.id == id } ?: return
        update(profile.copy(pinHash = pin?.let { hash(id, it) }))
    }

    fun checkPin(profile: Profile, pin: String) = profile.pinHash == null || profile.pinHash == hash(profile.id, pin)

    private fun save(list: List<Profile>) {
        profiles = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("color", it.colorIndex)
                    .put("pin", it.pinHash ?: JSONObject.NULL)
                    .put("restricted", it.restricted)
                    .put("rating", it.ratingLimit.name),
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun load(): List<Profile> {
        val stored = runCatching {
            val array = JSONArray(prefs.getString(KEY_PROFILES, null) ?: "[]")
            (0 until array.length()).map { i ->
                val json = array.getJSONObject(i)
                Profile(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    colorIndex = json.optInt("color"),
                    pinHash = json.optString("pin").takeIf { !json.isNull("pin") && it.isNotEmpty() },
                    restricted = json.optBoolean("restricted"),
                    ratingLimit = runCatching { RatingLimit.valueOf(json.optString("rating")) }
                        .getOrDefault(RatingLimit.Everyone),
                )
            }
        }.getOrDefault(emptyList())
        if (stored.any { it.isMain }) return stored
        return listOf(Profile(Profile.MAIN_ID, "Main", 0, null, false, RatingLimit.Everyone)) + stored
    }

    private fun hash(id: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$id:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_CURRENT = "current"
        const val KEY_ASK_ON_START = "ask_on_start"
    }
}
