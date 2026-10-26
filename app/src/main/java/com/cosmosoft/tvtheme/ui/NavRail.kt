package com.cosmosoft.tvtheme.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.data.Profile
import com.cosmosoft.tvtheme.ui.profiles.Avatar
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

enum class Destination { Home, Apps, Customize }

/** Side menu: icons only until it gets focus, then it widens to show labels over the content. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavRail(
    selected: Destination,
    /** The current profile, shown at the top to switch profiles; null when there's only one. */
    profile: Profile?,
    onNavigate: (Destination) -> Unit,
    onQuickSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    quickSettingsFocus: FocusRequester,
) {
    val theme = LocalLauncherTheme.current
    val itemFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 260.dp else RailWidth, label = "rail width")
    // Collapsed, the menu is a soft shadow; expanded, it's a solid panel so labels don't clash with cards behind.
    val background = if (expanded) {
        SolidColor(theme.background.first())
    } else {
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(background)
            .onFocusChanged { expanded = it.hasFocus }
            // Entering the menu from the content always lands on the current screen's item.
            .focusProperties { enter = { itemFocus.getValue(selected) } }
            .focusGroup()
            .padding(horizontal = 12.dp, vertical = SafeVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        if (profile != null) {
            RailEntry(label = profile.name, selected = false, expanded = expanded, onClick = onSwitchProfile) {
                Avatar(profile, 28.dp)
            }
            Spacer(Modifier.height(24.dp))
        }
        val items = listOf(
            Triple(Destination.Home, Icons.Filled.Home, R.string.nav_home),
            Triple(Destination.Apps, Icons.AutoMirrored.Filled.List, R.string.nav_apps),
            Triple(Destination.Customize, Icons.Filled.Edit, R.string.nav_customize),
        )
        for ((destination, icon, label) in items) {
            RailItem(
                icon = icon,
                label = stringResource(label),
                selected = selected == destination,
                expanded = expanded,
                modifier = Modifier.focusRequester(itemFocus.getValue(destination)),
            ) {
                onNavigate(destination)
            }
        }
        Spacer(Modifier.height(24.dp))
        RailItem(
            icon = Icons.Filled.Settings,
            label = stringResource(R.string.nav_quick_settings),
            selected = false,
            expanded = expanded,
            modifier = Modifier.focusRequester(quickSettingsFocus),
            onClick = onQuickSettings,
        )
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RailEntry(label, selected, expanded, modifier, onClick) { Icon(icon, contentDescription = label) }
}

/** One menu row: [leading] (an icon or avatar), plus the label while the menu is expanded. */
@Composable
private fun RailEntry(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) theme.accent.copy(alpha = 0.25f) else Color.Transparent,
            contentColor = if (selected) theme.accent else theme.text,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(label, maxLines = 1)
            }
        }
    }
}
