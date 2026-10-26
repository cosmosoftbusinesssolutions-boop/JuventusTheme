package com.cosmosoft.tvtheme.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.BuildConfig
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.BuiltinWallpapers
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.data.WallpaperChoice
import com.cosmosoft.tvtheme.data.Wallpapers
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.billing.Premium
import com.cosmosoft.tvtheme.data.ProfileStore
import com.cosmosoft.tvtheme.ui.profiles.Avatar
import com.cosmosoft.tvtheme.ui.profiles.ProfileEditorDialog
import com.cosmosoft.tvtheme.ui.profiles.label
import com.cosmosoft.tvtheme.ui.plex.PlexHome
import com.cosmosoft.tvtheme.ui.plex.PlexServerPickerDialog
import com.cosmosoft.tvtheme.ui.plex.PlexSignInDialog
import com.cosmosoft.tvtheme.ui.theme.LauncherThemes
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PhotoPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

private enum class PlexDialog { SignIn, ChooseServer }

@Composable
fun CustomizeScreen(
    apps: List<LaunchableApp>?,
    settings: LauncherSettings,
    plex: PlexSession,
    plexHome: PlexHome?,
    profiles: ProfileStore,
    premium: Premium,
    onOpenSetup: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickingPicture by remember { mutableStateOf(false) }
    var plexDialog by remember { mutableStateOf<PlexDialog?>(null) }
    var editingProfile by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // Focus goes back to the profile row that was edited (or Add profile, if it was deleted).
    val profileFocus = remember { mutableMapOf<String, FocusRequester>() }
    val addProfileFocus = remember { FocusRequester() }
    var refocusProfile by remember { mutableStateOf<String?>(null) }
    val unlocked = premium.isUnlocked
    val showLocked = { Toast.makeText(context, R.string.premium_locked_hint, Toast.LENGTH_SHORT).show() }
    val firstFocus = remember { FocusRequester() }

    val requestPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickingPicture = true
        } else {
            Toast.makeText(context, R.string.pictures_denied, Toast.LENGTH_LONG).show()
        }
    }
    val saveAsWallpaper = { uri: Uri ->
        scope.launch {
            val saved = withContext(Dispatchers.IO) { Wallpapers.saveCustom(context, uri, settings.profileId) }
            if (saved) {
                settings.wallpaper = WallpaperChoice.Custom(System.currentTimeMillis())
            } else {
                Toast.makeText(context, R.string.pictures_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
    // Google Play build: the system photo picker grants access to just the one picture chosen.
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) saveAsWallpaper(uri)
    }
    val choosePicture = {
        if (BuildConfig.SYSTEM_PHOTO_PICKER) {
            try {
                systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.pictures_no_picker, Toast.LENGTH_LONG).show()
            }
        } else if (ContextCompat.checkSelfPermission(context, PhotoPermission) == PackageManager.PERMISSION_GRANTED) {
            pickingPicture = true
        } else {
            requestPhotos.launch(PhotoPermission)
        }
    }

    // Items are inset one by one (not via contentPadding) so the wallpaper row can scroll edge to edge.
    val inset = Modifier.padding(start = ContentStart, end = SafeHorizontal)
    LazyColumn(
        contentPadding = PaddingValues(vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(inset) {
                SectionTitle(stringResource(R.string.customize_wallpaper))
                Text(stringResource(R.string.customize_wallpaper_hint), color = theme.text.copy(alpha = 0.7f))
            }
        }
        item {
            WallpaperPicker(settings, locked = !unlocked, onLocked = showLocked, onChoosePicture = choosePicture, firstFocus = firstFocus)
        }

        item { SectionTitle(stringResource(R.string.customize_rows), inset.padding(top = 16.dp)) }
        item {
            ToggleRow(stringResource(R.string.row_favorites), settings.showFavorites, inset) { settings.showFavorites = it }
        }
        item {
            ToggleRow(stringResource(R.string.row_tv_apps), settings.showTvApps, inset) { settings.showTvApps = it }
        }
        item {
            ToggleRow(stringResource(R.string.row_other_apps), settings.showOtherApps, inset) { settings.showOtherApps = it }
        }

        item { SectionTitle(stringResource(R.string.plex_section), inset.padding(top = 16.dp)) }
        if (!unlocked) {
            item { LockedRow(stringResource(R.string.plex_connect), inset) }
        } else if (!plex.isSignedIn) {
            item {
                ListRow(
                    title = stringResource(R.string.plex_connect),
                    subtitle = stringResource(R.string.plex_connect_hint),
                    onClick = { plexDialog = PlexDialog.SignIn },
                    modifier = inset,
                )
            }
        } else {
            item {
                ListRow(
                    title = stringResource(R.string.plex_server),
                    subtitle = plex.server?.name ?: stringResource(R.string.plex_server_none),
                    onClick = { plexDialog = PlexDialog.ChooseServer },
                    modifier = inset,
                )
            }
            item {
                ToggleRow(stringResource(R.string.row_continue_watching), settings.showContinueWatching, inset) {
                    settings.showContinueWatching = it
                }
            }
            item {
                ToggleRow(stringResource(R.string.row_watchlist), settings.showWatchlist, inset) { settings.showWatchlist = it }
            }
            items(plexHome?.libraries.orEmpty(), key = { "library:${it.first.key}" }) { (library, _) ->
                val id = "${plex.server?.machineId}/${library.key}"
                val shown = id !in settings.hiddenLibraries
                ToggleRow(stringResource(R.string.row_recently_added, library.title), shown, inset) {
                    settings.setLibraryShown(id, it)
                }
            }
            item {
                ListRow(
                    title = stringResource(R.string.plex_sign_out),
                    subtitle = plex.accountName ?: stringResource(R.string.plex_signed_in),
                    onClick = { confirmSignOut = true },
                    modifier = inset,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.profiles_section), inset.padding(top = 16.dp)) }
        if (profiles.current.isMain) {
            // Only the main profile manages profiles.
            items(profiles.profiles, key = { "profile:${it.id}" }) { profile ->
                ListRow(
                    title = profile.name,
                    subtitle = if (profile.restricted) {
                        stringResource(R.string.profile_summary_restricted, profile.ratingLimit.label())
                    } else {
                        stringResource(R.string.profile_summary_open)
                    },
                    onClick = { editingProfile = profile.id },
                    modifier = inset.focusRequester(profileFocus.getOrPut(profile.id) { FocusRequester() }),
                ) {
                    Avatar(profile, 36.dp)
                }
            }
            item {
                // Existing profiles keep working after the trial; only adding more is Premium.
                if (unlocked) {
                    val newName = stringResource(R.string.profiles_new_name, profiles.profiles.size + 1)
                    ListRow(
                        title = stringResource(R.string.profiles_add),
                        onClick = { editingProfile = profiles.add(newName).id },
                        modifier = inset.focusRequester(addProfileFocus),
                    )
                } else {
                    LockedRow(stringResource(R.string.profiles_add), inset.focusRequester(addProfileFocus))
                }
            }
            if (profiles.profiles.size > 1) {
                item {
                    ToggleRow(stringResource(R.string.profiles_ask_on_start), profiles.askOnStart, inset) {
                        profiles.askOnStart = it
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.customize_hidden), inset.padding(top = 16.dp)) }
        val hiddenApps = apps.orEmpty().filter { it.key in settings.hidden }
        if (hiddenApps.isEmpty()) {
            item { Text(stringResource(R.string.customize_hidden_none), color = theme.text.copy(alpha = 0.7f), modifier = inset) }
        }
        items(hiddenApps, key = { it.key }) { app ->
            ListRow(
                title = app.label,
                subtitle = stringResource(R.string.customize_unhide),
                onClick = { settings.setHidden(app.key, false) },
                modifier = inset,
            )
        }

        item { PremiumSection(premium, inset.padding(top = 16.dp)) }

        item { SectionTitle(stringResource(R.string.customize_default_home), inset.padding(top = 16.dp)) }
        item {
            ListRow(
                title = stringResource(R.string.home_setup_title),
                subtitle = stringResource(R.string.customize_default_home_hint),
                onClick = onOpenSetup,
                modifier = inset,
            )
        }
    }

    when (plexDialog) {
        PlexDialog.SignIn -> PlexSignInDialog(plex, onDismiss = { plexDialog = null })
        PlexDialog.ChooseServer -> PlexServerPickerDialog(plex, onDismiss = { plexDialog = null })
        null -> Unit
    }

    editingProfile?.let { id ->
        ProfileEditorDialog(profiles, id, apps, onDismiss = {
            editingProfile = null
            refocusProfile = id
        })
    }
    LaunchedEffect(refocusProfile) {
        val id = refocusProfile ?: return@LaunchedEffect
        val target = profileFocus[id]?.takeIf { profiles.profiles.any { it.id == id } } ?: addProfileFocus
        target.requestFocusWhenReady()
        refocusProfile = null
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_sign_out_title),
            message = stringResource(R.string.confirm_sign_out_message),
            confirmLabel = stringResource(R.string.action_sign_out),
            onConfirm = {
                confirmSignOut = false
                plex.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (pickingPicture) {
        PicturePickerDialog(
            onDismiss = { pickingPicture = false },
            onPicked = { uri ->
                pickingPicture = false
                saveAsWallpaper(uri)
            },
        )
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun WallpaperPicker(
    settings: LauncherSettings,
    locked: Boolean,
    onLocked: () -> Unit,
    onChoosePicture: () -> Unit,
    firstFocus: FocusRequester,
) {
    val context = LocalContext.current
    val current = settings.wallpaper

    LazyRow(
        contentPadding = PaddingValues(start = ContentStart, end = SafeHorizontal, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "custom") {
            val thumbnail by produceState<ImageBitmap?>(null, current) {
                value = withContext(Dispatchers.IO) {
                    Wallpapers.decodeFileSampled(Wallpapers.customFile(context, settings.profileId), 320, 180)?.asImageBitmap()
                }
            }
            WallpaperOption(
                name = stringResource(R.string.wallpaper_your_picture),
                selected = current is WallpaperChoice.Custom,
                onClick = if (locked) onLocked else onChoosePicture,
                modifier = Modifier.focusRequester(firstFocus),
            ) {
                val image = thumbnail
                if (image != null) {
                    Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(40.dp))
                }
            }
        }
        items(BuiltinWallpapers, key = { "builtin:${it.id}" }) { wallpaper ->
            val thumbnail by produceState<ImageBitmap?>(null, wallpaper.id) {
                value = withContext(Dispatchers.Default) { wallpaper.render(320, 180).asImageBitmap() }
            }
            WallpaperOption(
                name = wallpaper.name,
                selected = current == WallpaperChoice.Builtin(wallpaper.id),
                onClick = { if (locked) onLocked() else settings.wallpaper = WallpaperChoice.Builtin(wallpaper.id) },
            ) {
                thumbnail?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
        }
        itemsIndexed(LauncherThemes, key = { index, _ -> "preset:$index" }) { index, preset ->
            WallpaperOption(
                name = preset.name,
                selected = current == WallpaperChoice.Preset(index),
                onClick = { settings.wallpaper = WallpaperChoice.Preset(index) },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(preset.background)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(20.dp)
                        .background(preset.accent, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun WallpaperOption(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable BoxScope.() -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val focusedBorder = Border(border = BorderStroke(3.dp, theme.accent), shape = CardShape)
    Column(Modifier.width(CardWidth)) {
        Card(
            onClick = onClick,
            modifier = modifier.size(CardWidth, CardHeight),
            shape = CardDefaults.shape(shape = CardShape),
            colors = CardDefaults.colors(containerColor = theme.card, focusedContainerColor = theme.card),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(
                border = if (selected) Border(BorderStroke(2.dp, theme.text), shape = CardShape) else Border.None,
                focusedBorder = focusedBorder,
                pressedBorder = focusedBorder,
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                preview()
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = theme.background.first(),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(theme.accent, CircleShape)
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, color = theme.text.copy(alpha = 0.85f))
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    ListRow(title = title, onClick = { onChange(!checked) }, modifier = modifier) {
        Text(stringResource(if (checked) R.string.state_on else R.string.state_off))
    }
}

@Composable
private fun PicturePickerDialog(onPicked: (Uri) -> Unit, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val pictures by produceState<List<Uri>?>(null) {
        value = withContext(Dispatchers.IO) { Wallpapers.deviceImages(context) }
    }
    val firstFocus = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.85f)
                    .background(theme.card, RoundedCornerShape(16.dp))
                    .padding(32.dp),
            ) {
                SectionTitle(stringResource(R.string.pictures_title))
                Spacer(Modifier.height(16.dp))
                val list = pictures
                when {
                    list == null -> Text(stringResource(R.string.pictures_loading), color = theme.text)
                    list.isEmpty() -> Text(stringResource(R.string.pictures_empty), color = theme.text)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(CardWidth),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(list, key = { it.toString() }) { uri ->
                            val thumbnail by produceState<ImageBitmap?>(null, uri) {
                                value = withContext(Dispatchers.IO) {
                                    Wallpapers.decodeSampled(context, uri, 320, 180)?.asImageBitmap()
                                }
                            }
                            Card(
                                onClick = { onPicked(uri) },
                                modifier = Modifier
                                    .size(CardWidth, CardHeight)
                                    .then(if (uri == list.first()) Modifier.focusRequester(firstFocus) else Modifier),
                                shape = CardDefaults.shape(shape = CardShape),
                                colors = CardDefaults.colors(containerColor = theme.background.first()),
                                scale = CardDefaults.scale(focusedScale = 1.08f),
                                border = CardDefaults.border(
                                    focusedBorder = Border(border = BorderStroke(3.dp, theme.accent), shape = CardShape),
                                ),
                            ) {
                                thumbnail?.let {
                                    Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(pictures?.isNotEmpty() == true) {
        if (pictures?.isNotEmpty() == true) runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun LockedRow(title: String, modifier: Modifier) {
    val context = LocalContext.current
    ListRow(
        title = title,
        subtitle = stringResource(R.string.premium_locked_hint),
        onClick = { Toast.makeText(context, R.string.premium_locked_hint, Toast.LENGTH_SHORT).show() },
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.premium_locked))
    }
}

@Composable
private fun PremiumSection(premium: Premium, modifier: Modifier) {
    val theme = LocalLauncherTheme.current
    val activity = LocalContext.current.findActivity()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.premium_section))
        val status = when {
            premium.owned -> stringResource(R.string.premium_owned)
            premium.trialDaysLeft > 0 -> stringResource(R.string.premium_trial, premium.trialDaysLeft)
            else -> stringResource(R.string.premium_expired)
        }
        Text(status, color = if (premium.isUnlocked) theme.text else theme.accent)
        if (!premium.owned) {
            Text(stringResource(R.string.premium_includes), color = theme.text.copy(alpha = 0.7f))
            val products = listOfNotNull(
                premium.lifetime?.let { it to R.string.premium_lifetime },
                premium.monthly?.let { it to R.string.premium_monthly },
            )
            if (products.isEmpty() && !premium.isUnlocked) {
                Text(stringResource(R.string.premium_unavailable), color = theme.text.copy(alpha = 0.7f))
            }
            products.forEach { (product, label) ->
                ListRow(
                    title = stringResource(label, Premium.price(product).orEmpty()),
                    onClick = { activity?.let { premium.buy(it, product) } },
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
