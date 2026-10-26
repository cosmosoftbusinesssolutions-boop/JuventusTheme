package com.cosmosoft.tvtheme.ui

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

private class SettingsEntry(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val action: String,
)

private val SystemEntries = listOf(
    SettingsEntry(Icons.Filled.Bluetooth, R.string.quick_bluetooth, R.string.quick_bluetooth_hint, Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsEntry(Icons.Filled.Tv, R.string.quick_display, R.string.quick_display_hint, Settings.ACTION_DISPLAY_SETTINGS),
    SettingsEntry(Icons.AutoMirrored.Filled.VolumeUp, R.string.quick_sound, R.string.quick_sound_hint, Settings.ACTION_SOUND_SETTINGS),
    SettingsEntry(Icons.Filled.Apps, R.string.quick_apps, R.string.quick_apps_hint, Settings.ACTION_APPLICATION_SETTINGS),
    SettingsEntry(Icons.Filled.Bedtime, R.string.quick_screensaver, R.string.quick_screensaver_hint, Settings.ACTION_DREAM_SETTINGS),
    SettingsEntry(Icons.Filled.Schedule, R.string.quick_date, R.string.quick_date_hint, Settings.ACTION_DATE_SETTINGS),
    SettingsEntry(Icons.Filled.Accessibility, R.string.quick_accessibility, R.string.quick_accessibility_hint, Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsEntry(Icons.Filled.Info, R.string.quick_about, R.string.quick_about_hint, Settings.ACTION_DEVICE_INFO_SETTINGS),
    SettingsEntry(Icons.Filled.Settings, R.string.quick_all, R.string.quick_all_hint, Settings.ACTION_SETTINGS),
)

/**
 * Side sheet on the right, laid out like the TV's own Settings panel (sections, icon rows with a
 * status line) but in TV Theme's colors. Rows jump straight to the matching system screen.
 */
@Composable
fun QuickSettingsPanel(onClose: () -> Unit, onCustomize: () -> Unit, onHomeSetup: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var volume by remember { mutableIntStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val network = remember { networkStatus(context) }
    val firstFocus = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }

    fun adjustVolume(direction: Int) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun open(action: String) {
        context.openSettings(action)
        onClose()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(visible = shown, enter = slideInHorizontally { it / 3 } + fadeIn()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(480.dp)
                        .background(theme.background.first()),
                    contentPadding = PaddingValues(bottom = SafeVertical),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.quick_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = theme.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.card)
                                .padding(horizontal = 28.dp, vertical = 28.dp),
                        )
                    }

                    item { SectionHeader(R.string.quick_section_status) }
                    item {
                        PanelRow(
                            icon = network.icon,
                            title = stringResource(R.string.quick_network),
                            subtitle = stringResource(network.label),
                            onClick = { open(Settings.ACTION_WIFI_SETTINGS) },
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                    }
                    item {
                        VolumeRow(
                            volume = volume,
                            maxVolume = maxVolume,
                            onDown = { adjustVolume(AudioManager.ADJUST_LOWER) },
                            onUp = { adjustVolume(AudioManager.ADJUST_RAISE) },
                        )
                    }

                    item { SectionHeader(R.string.quick_section_launcher) }
                    item {
                        PanelRow(
                            icon = Icons.Filled.Edit,
                            title = stringResource(R.string.nav_customize),
                            subtitle = stringResource(R.string.quick_customize_hint),
                            onClick = onCustomize,
                        )
                    }
                    item {
                        PanelRow(
                            icon = Icons.Filled.Home,
                            title = stringResource(R.string.home_setup_title),
                            subtitle = stringResource(R.string.quick_home_setup_hint),
                            onClick = onHomeSetup,
                        )
                    }

                    item { SectionHeader(R.string.quick_section_settings) }
                    SystemEntries.forEach { entry ->
                        item {
                            PanelRow(
                                icon = entry.icon,
                                title = stringResource(entry.title),
                                subtitle = stringResource(entry.subtitle),
                                onClick = { open(entry.action) },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        shown = true
        firstFocus.requestFocusWhenReady()
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = LocalLauncherTheme.current.accent,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Full-width row: icon, title, and a dimmer status line underneath, like the system Settings list. */
@Composable
private fun PanelRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = theme.text,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(volume: Int, maxVolume: Int, onDown: () -> Unit, onUp: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Column(Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.quick_volume, volume, maxVolume), style = MaterialTheme.typography.titleMedium, color = theme.text)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.AutoMirrored.Filled.VolumeDown, stringResource(R.string.quick_volume_down), onDown)
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .background(theme.text.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (maxVolume > 0) volume / maxVolume.toFloat() else 0f)
                        .background(theme.accent, RoundedCornerShape(3.dp)),
                )
            }
            RoundIconButton(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.quick_volume_up), onUp)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.card,
            contentColor = theme.text,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

private class NetworkStatus(val icon: ImageVector, @StringRes val label: Int)

private fun networkStatus(context: Context): NetworkStatus {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?: return NetworkStatus(Icons.Filled.WifiOff, R.string.network_offline)
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus(Icons.Filled.Lan, R.string.network_ethernet)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus(Icons.Filled.Wifi, R.string.network_wifi)
        else -> NetworkStatus(Icons.Filled.Wifi, R.string.network_other)
    }
}
