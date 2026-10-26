package com.cosmosoft.tvtheme.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Everything one profile customizes, saved to SharedPreferences and exposed as Compose state
 * so the UI updates as soon as a value changes.
 */
class LauncherSettings(context: Context, val profileId: String = Profile.MAIN_ID) {
    private val prefs = context.getSharedPreferences(prefsName(profileId), Context.MODE_PRIVATE)

    /** App keys ([LaunchableApp.key]) in the order they appear in the Favorites row. */
    var favorites by mutableStateOf(
        prefs.getString(KEY_FAVORITES, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty(),
    )
        private set

    var hidden by mutableStateOf(prefs.getStringSet(KEY_HIDDEN, null)?.toSet().orEmpty())
        private set

    var showFavorites by BooleanPref(KEY_SHOW_FAVORITES, true)
    var showTvApps by BooleanPref(KEY_SHOW_TV_APPS, true)
    var showOtherApps by BooleanPref(KEY_SHOW_OTHER_APPS, true)
    var showContinueWatching by BooleanPref(KEY_SHOW_CONTINUE_WATCHING, true)
    var showWatchlist by BooleanPref(KEY_SHOW_WATCHLIST, true)

    /** Plex libraries whose "Recently added" row is turned off, as "<server id>/<library key>". */
    var hiddenLibraries by mutableStateOf(prefs.getStringSet(KEY_HIDDEN_LIBRARIES, null)?.toSet().orEmpty())
        private set

    fun setLibraryShown(id: String, shown: Boolean) {
        hiddenLibraries = if (shown) hiddenLibraries - id else hiddenLibraries + id
        prefs.edit().putStringSet(KEY_HIDDEN_LIBRARIES, hiddenLibraries).apply()
    }

    private var wallpaperState = mutableStateOf(
        WallpaperChoice.parse(prefs.getString(KEY_WALLPAPER, null))
            // Before wallpapers existed, only a color theme index was stored.
            ?: prefs.getInt(KEY_LEGACY_THEME, -1).takeIf { it >= 0 }?.let { WallpaperChoice.Preset(it) }
            ?: DefaultWallpaper,
    )
    var wallpaper: WallpaperChoice
        get() = wallpaperState.value
        set(value) {
            wallpaperState.value = value
            prefs.edit().putString(KEY_WALLPAPER, value.serialize()).apply()
        }

    fun isFavorite(key: String) = key in favorites

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString("\n")).apply()
    }

    /** Moves a favorite [delta] places (negative is left). Does nothing at either end. */
    fun moveFavorite(key: String, delta: Int) {
        val from = favorites.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in favorites.indices) return
        favorites = favorites.toMutableList().apply { add(to, removeAt(from)) }
        prefs.edit().putString(KEY_FAVORITES, favorites.joinToString("\n")).apply()
    }

    fun setHidden(key: String, hide: Boolean) {
        hidden = if (hide) hidden + key else hidden - key
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply()
    }

    private inner class BooleanPref(private val key: String, default: Boolean) :
        ReadWriteProperty<Any?, Boolean> {
        private val state = mutableStateOf(prefs.getBoolean(key, default))

        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    companion object {
        /** The main profile keeps the original file, so settings from before profiles existed carry over. */
        fun prefsName(profileId: String) = if (profileId == Profile.MAIN_ID) "launcher" else "launcher_$profileId"

        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SHOW_FAVORITES = "show_favorites"
        private const val KEY_SHOW_TV_APPS = "show_tv_apps"
        private const val KEY_SHOW_OTHER_APPS = "show_other_apps"
        private const val KEY_SHOW_CONTINUE_WATCHING = "show_continue_watching"
        private const val KEY_SHOW_WATCHLIST = "show_watchlist"
        private const val KEY_HIDDEN_LIBRARIES = "hidden_libraries"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_LEGACY_THEME = "theme"
    }
}
