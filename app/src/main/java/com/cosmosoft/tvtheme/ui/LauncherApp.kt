package com.cosmosoft.tvtheme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.cosmosoft.tvtheme.data.AppRepository
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.data.LoadedWallpaper
import com.cosmosoft.tvtheme.data.Profile
import com.cosmosoft.tvtheme.data.ProfileStore
import com.cosmosoft.tvtheme.data.WallpaperChoice
import com.cosmosoft.tvtheme.data.Wallpapers
import com.cosmosoft.tvtheme.data.allows
import com.cosmosoft.tvtheme.billing.Premium
import com.cosmosoft.tvtheme.home.HomeSetup
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.ui.plex.PlexHome
import com.cosmosoft.tvtheme.ui.profiles.PinDialog
import com.cosmosoft.tvtheme.ui.profiles.ProfilePicker
import androidx.compose.ui.res.stringResource
import com.cosmosoft.tvtheme.plex.PlexItem
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.plex.MediaDetailDialog
import com.cosmosoft.tvtheme.ui.plex.rememberPlexHome
import com.cosmosoft.tvtheme.ui.theme.LauncherThemeProvider
import com.cosmosoft.tvtheme.ui.theme.BianconeriPresetIndex
import com.cosmosoft.tvtheme.ui.theme.BianconeriTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root of the launcher: wallpaper, side menu, the current screen, and the overlays.
 *
 * @param appsVersion bump to reload the app list (after installs, uninstalls, or returning home).
 * @param homeSignal bumps each time Home is pressed while the launcher is already open.
 */
@Composable
fun LauncherApp(profiles: ProfileStore, premium: Premium, plex: PlexSession, appsVersion: Int, homeSignal: Int) {
    val context = LocalContext.current
    val premiumOn = premium.isUnlocked
    // Extra profiles are a Premium feature; without it everyone uses the main profile.
    val profile = if (premiumOn) profiles.current else profiles.main
    val settings = remember(profile.id) { LauncherSettings(context, profile.id) }

    val apps by produceState<List<LaunchableApp>?>(initialValue = null, appsVersion) {
        value = withContext(Dispatchers.IO) { AppRepository.load(context) }
    }
    // Coming back to the launcher (appsVersion bumps on resume) also refreshes Plex rows.
    val plexHome = rememberPlexHome(plex, refreshKey = appsVersion)?.takeIf { premiumOn }?.allowedFor(profile)
    var openMedia by remember { mutableStateOf<PlexItem?>(null) }
    val wallpaperChoice = settings.wallpaper.takeIf { premiumOn || it is WallpaperChoice.Preset } ?: WallpaperChoice.Preset(BianconeriPresetIndex)
    val wallpaper by produceState<LoadedWallpaper?>(initialValue = null, wallpaperChoice) {
        value = withContext(Dispatchers.IO) { Wallpapers.load(context, wallpaperChoice) }
    }

    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var quickSettingsOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var setupOpen by remember { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(premiumOn && profiles.askOnStart && profiles.profiles.size > 1) }
    val needsSetup = remember(appsVersion) { HomeSetup.needsSetup(context) }
    val quickSettingsFocus = remember { FocusRequester() }

    // Restricted profiles need the main profile's PIN once per session to reach settings.
    var settingsUnlocked by remember(profile.id) { mutableStateOf(!profile.restricted) }
    var pendingAfterPin by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun guarded(action: () -> Unit) {
        if (settingsUnlocked || !profiles.main.hasPin) action() else pendingAfterPin = action
    }

    // Pressing Home on the remote always brings you back to the Home screen.
    LaunchedEffect(homeSignal) {
        destination = Destination.Home
        quickSettingsOpen = false
        openMedia = null
    }
    LaunchedEffect(profile.id) { destination = Destination.Home }
    // A launcher is the bottom of the back stack: Back returns to Home but never closes it.
    BackHandler { destination = Destination.Home }

    LauncherThemeProvider(wallpaper?.theme ?: BianconeriTheme) {
        Box(Modifier.fillMaxSize()) {
            WallpaperBackground(wallpaper)

            Box(Modifier.fillMaxSize()) {
                val showOptions = { app: LaunchableApp -> optionsFor = app }
                when (destination) {
                    Destination.Home -> HomeScreen(
                        apps = apps,
                        settings = settings,
                        plex = plex,
                        plexHome = plexHome,
                        needsSetup = needsSetup,
                        onAppOptions = showOptions,
                        onOpenMedia = { openMedia = it },
                        onSetup = { setupOpen = true },
                    )
                    Destination.Apps -> AppsScreen(apps, settings, showOptions)
                    Destination.Customize -> CustomizeScreen(
                        apps = apps,
                        settings = settings,
                        plex = plex,
                        plexHome = plexHome,
                        profiles = profiles,
                        premium = premium,
                        onOpenSetup = { setupOpen = true },
                    )
                }
            }

            NavRail(
                selected = destination,
                profile = profile.takeIf { premiumOn && profiles.profiles.size > 1 },
                onNavigate = { target ->
                    if (target == Destination.Customize) guarded { destination = target } else destination = target
                },
                onQuickSettings = { guarded { quickSettingsOpen = true } },
                onSwitchProfile = { picking = true },
                quickSettingsFocus = quickSettingsFocus,
            )

            if (quickSettingsOpen) {
                QuickSettingsPanel(
                    onClose = {
                        quickSettingsOpen = false
                        runCatching { quickSettingsFocus.requestFocus() }
                    },
                    // The panel is already PIN-guarded, so these don't ask again.
                    onCustomize = {
                        quickSettingsOpen = false
                        destination = Destination.Customize
                    },
                    onHomeSetup = {
                        quickSettingsOpen = false
                        setupOpen = true
                    },
                )
            }
            optionsFor?.let { app ->
                AppOptionsDialog(app, settings, restricted = !settingsUnlocked, onDismiss = { optionsFor = null })
            }
            openMedia?.let { item ->
                MediaDetailDialog(item, plex, refreshKey = appsVersion, onDismiss = { openMedia = null })
            }
            if (setupOpen) {
                HomeSetupDialog(refreshKey = appsVersion, onDismiss = { setupOpen = false })
            }
            if (picking) {
                ProfilePicker(profiles, onPicked = { picking = false }, onDismiss = { picking = false })
            }
            pendingAfterPin?.let { action ->
                val wrong = stringResource(R.string.pin_wrong)
                PinDialog(
                    title = stringResource(R.string.pin_enter_main),
                    onPin = { pin ->
                        if (profiles.checkPin(profiles.main, pin)) {
                            settingsUnlocked = true
                            pendingAfterPin = null
                            action()
                            null
                        } else {
                            wrong
                        }
                    },
                    onDismiss = { pendingAfterPin = null },
                )
            }
        }
    }
}

/** Drops Plex titles above a restricted profile's rating limit. */
private fun PlexHome.allowedFor(profile: Profile): PlexHome {
    if (!profile.restricted) return this
    val allowed = { item: PlexItem -> profile.ratingLimit.allows(item.contentRating) }
    return PlexHome(
        continueWatching = continueWatching.filter(allowed),
        watchlist = watchlist.filter(allowed),
        libraries = libraries.map { (library, items) -> library to items.filter(allowed) },
    )
}

@Composable
private fun WallpaperBackground(wallpaper: LoadedWallpaper?) {
    Crossfade(targetState = wallpaper, label = "wallpaper") { current ->
        val image = current?.image
        if (image != null) {
            Box(Modifier.fillMaxSize()) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Darken the left and bottom so text and cards stay readable on bright pictures.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.25f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                )
            }
        } else {
            val colors = (current?.theme ?: BianconeriTheme).background
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(colors)),
            )
        }
    }
}
