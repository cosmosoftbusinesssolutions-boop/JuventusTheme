package com.cosmosoft.tvtheme.ui.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import com.cosmosoft.tvtheme.ui.requestFocusWhenReady
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.LaunchableApp
import com.cosmosoft.tvtheme.data.LauncherSettings
import com.cosmosoft.tvtheme.data.Profile
import com.cosmosoft.tvtheme.data.ProfileStore
import com.cosmosoft.tvtheme.data.RatingLimit
import com.cosmosoft.tvtheme.ui.ListRow
import com.cosmosoft.tvtheme.ui.SafeVertical
import com.cosmosoft.tvtheme.ui.SectionTitle
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

/** Gold, white, silver and charcoal first, to match the black-and-white look. */
val AvatarColors = listOf(
    Color(0xFFD4AF37), Color(0xFFF4F4F4), Color(0xFF9E9E9E), Color(0xFF2E2E2E), Color(0xFF8C6D1F), Color(0xFF5A5A5A),
)

@Composable
fun Avatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    val color = AvatarColors[profile.colorIndex % AvatarColors.size]
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.name.take(1).uppercase(),
            color = if (color.luminance() > 0.4f) Color.Black else Color.White,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun RatingLimit.label(): String = stringResource(
    when (this) {
        RatingLimit.LittleKids -> R.string.rating_little_kids
        RatingLimit.OlderKids -> R.string.rating_older_kids
        RatingLimit.Teens -> R.string.rating_teens
        RatingLimit.Everyone -> R.string.rating_everyone
    },
)

/** "Who's watching?" Full screen; picking a profile with a PIN asks for it first. */
@Composable
fun ProfilePicker(store: ProfileStore, onPicked: () -> Unit, onDismiss: (() -> Unit)? = null) {
    val theme = LocalLauncherTheme.current
    var askingPinFor by remember { mutableStateOf<Profile?>(null) }
    val firstFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(theme.background.first()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.profiles_who), style = MaterialTheme.typography.displaySmall, color = theme.text)
                Spacer(Modifier.height(48.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    store.profiles.forEachIndexed { index, profile ->
                        ProfileTile(
                            profile = profile,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = {
                                if (profile.hasPin) {
                                    askingPinFor = profile
                                } else {
                                    store.select(profile.id)
                                    onPicked()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    askingPinFor?.let { profile ->
        val wrong = stringResource(R.string.pin_wrong)
        PinDialog(
            title = stringResource(R.string.pin_enter, profile.name),
            onPin = { pin ->
                if (store.checkPin(profile, pin)) {
                    askingPinFor = null
                    store.select(profile.id)
                    onPicked()
                    null
                } else {
                    wrong
                }
            },
            onDismiss = { askingPinFor = null },
        )
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun ProfileTile(profile: Profile, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        ) {
            // The ring sits outside the avatar with a gap, so it stays visible on gold or white avatars.
            Avatar(
                profile,
                120.dp,
                Modifier
                    .border(BorderStroke(4.dp, if (focused) theme.accent else Color.Transparent), CircleShape)
                    .padding(8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(profile.name, style = MaterialTheme.typography.titleLarge, color = theme.text)
            if (profile.hasPin) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.text.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Edits one profile: name, PIN, restrictions, allowed apps, or deleting it. */
@Composable
fun ProfileEditorDialog(store: ProfileStore, profileId: String, apps: List<LaunchableApp>?, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val profile = store.profiles.firstOrNull { it.id == profileId } ?: run {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    // The profile's own settings hold its hidden apps; for restricted profiles that's the block list.
    val profileSettings = remember(profileId) { LauncherSettings(context, profileId) }
    var name by remember(profileId) { mutableStateOf(profile.name) }
    var settingPin by remember { mutableStateOf(false) }
    var editingApps by remember { mutableStateOf(false) }
    val firstRowFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LazyColumn(
                Modifier
                    .width(720.dp)
                    .fillMaxHeight(0.9f)
                    .background(theme.card, RoundedCornerShape(16.dp)),
                contentPadding = PaddingValues(32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(profile.copy(name = name.ifBlank { profile.name }), 56.dp)
                        Spacer(Modifier.width(16.dp))
                        SectionTitle(stringResource(R.string.profile_edit_title, profile.name))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    Text(stringResource(R.string.profile_name), color = theme.text.copy(alpha = 0.7f))
                    var nameFocused by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = theme.text),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (name.isNotBlank()) store.update(store.profiles.first { it.id == profileId }.copy(name = name.trim()))
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            // Text fields swallow the D-pad on TV; let Up/Down move on to the other rows.
                            .onPreviewKeyEvent { event ->
                                val direction = when (event.key) {
                                    Key.DirectionDown -> FocusDirection.Down
                                    Key.DirectionUp -> FocusDirection.Up
                                    else -> null
                                }
                                if (direction != null && event.type == KeyEventType.KeyDown) {
                                    focusManager.moveFocus(direction)
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged {
                                // Save when leaving the field, too.
                                if (nameFocused && !it.isFocused && name.isNotBlank()) {
                                    store.update(store.profiles.first { p -> p.id == profileId }.copy(name = name.trim()))
                                }
                                nameFocused = it.isFocused
                            }
                            .border(
                                BorderStroke(2.dp, if (nameFocused) theme.accent else theme.text.copy(alpha = 0.3f)),
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                item {
                    ListRow(
                        title = stringResource(R.string.profile_pin),
                        subtitle = stringResource(if (profile.hasPin) R.string.profile_pin_set else R.string.profile_pin_none),
                        onClick = { settingPin = true },
                        // Start on a row, not the text field, so the on-screen keyboard doesn't pop up.
                        modifier = Modifier.focusRequester(firstRowFocus),
                    )
                }
                if (profile.hasPin) {
                    item { ListRow(stringResource(R.string.profile_pin_remove), onClick = { store.setPin(profileId, null) }) }
                }
                if (!profile.isMain) {
                    item {
                        ListRow(
                            title = stringResource(R.string.profile_restricted),
                            subtitle = stringResource(
                                if (store.main.hasPin) R.string.profile_restricted_hint else R.string.profile_restricted_needs_main_pin,
                            ),
                            onClick = { store.update(profile.copy(restricted = !profile.restricted)) },
                        ) {
                            Text(stringResource(if (profile.restricted) R.string.state_on else R.string.state_off))
                        }
                    }
                    if (profile.restricted) {
                        item {
                            ListRow(
                                title = stringResource(R.string.profile_rating),
                                subtitle = profile.ratingLimit.label(),
                                onClick = {
                                    val next = RatingLimit.entries[(profile.ratingLimit.ordinal + 1) % RatingLimit.entries.size]
                                    store.update(profile.copy(ratingLimit = next))
                                },
                            )
                        }
                    }
                }
                item {
                    val total = apps?.size ?: 0
                    val hidden = apps.orEmpty().count { it.key in profileSettings.hidden }
                    ListRow(
                        title = stringResource(R.string.profile_apps),
                        subtitle = stringResource(R.string.profile_apps_hint, hidden, total),
                        onClick = { editingApps = true },
                    )
                }
                if (!profile.isMain) {
                    item {
                        ListRow(stringResource(R.string.profile_delete), onClick = {
                            store.delete(profileId)
                            onDismiss()
                        })
                    }
                }
            }
        }
    }

    if (settingPin) {
        SetPinDialog(
            onSet = { pin ->
                store.setPin(profileId, pin)
                settingPin = false
            },
            onDismiss = { settingPin = false },
        )
    }
    if (editingApps) {
        AllowedAppsDialog(apps.orEmpty(), profileSettings, onDismiss = { editingApps = false })
    }

    LaunchedEffect(Unit) { firstRowFocus.requestFocusWhenReady() }
}

@Composable
private fun AllowedAppsDialog(apps: List<LaunchableApp>, settings: LauncherSettings, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LazyColumn(
                Modifier
                    .width(640.dp)
                    .fillMaxHeight(0.9f)
                    .background(theme.card, RoundedCornerShape(16.dp)),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = SafeVertical),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SectionTitle(stringResource(R.string.profile_apps)) }
                items(apps, key = { it.key }) { app ->
                    val hidden = app.key in settings.hidden
                    ListRow(
                        title = app.label,
                        onClick = { settings.setHidden(app.key, !hidden) },
                        modifier = if (app == apps.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        Text(stringResource(if (hidden) R.string.state_hidden else R.string.state_shown))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}
