package com.cosmosoft.tvtheme.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.home.HomeSetup
import com.cosmosoft.tvtheme.home.TvPlatform
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

private enum class SetupPage { Options, HelperDisclosure, Adb }

/**
 * Explains how to make the Home button open TV Theme on this kind of TV.
 * [refreshKey] bumps when the launcher resumes, so the status updates after the user returns from Settings.
 */
@Composable
fun HomeSetupDialog(refreshKey: Int, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val context = LocalContext.current
    var page by remember { mutableStateOf(SetupPage.Options) }
    val platform = remember { HomeSetup.platform(context) }
    val done = remember(refreshKey) { !HomeSetup.needsSetup(context) }
    val helperOn = remember(refreshKey) { HomeSetup.isHelperEnabled(context) }
    val firstFocus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = { if (page == SetupPage.Options) onDismiss() else page = SetupPage.Options },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(760.dp)
                    .background(theme.card, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionTitle(stringResource(R.string.home_setup_title))
                when (page) {
                    SetupPage.Options -> {
                        Text(
                            stringResource(if (done) R.string.home_setup_done else R.string.home_setup_not_done),
                            color = if (done) theme.accent else theme.text,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (platform == TvPlatform.AndroidTv) {
                            ListRow(
                                title = stringResource(R.string.home_setup_choose),
                                subtitle = stringResource(R.string.home_setup_choose_hint),
                                onClick = { context.openSettings(Settings.ACTION_HOME_SETTINGS) },
                                modifier = Modifier.focusRequester(firstFocus),
                            )
                        }
                        if (HomeSetup.helperAvailable) {
                            ListRow(
                                title = stringResource(R.string.home_setup_helper),
                                subtitle = stringResource(R.string.home_setup_helper_hint),
                                onClick = { page = SetupPage.HelperDisclosure },
                                modifier = if (platform != TvPlatform.AndroidTv) Modifier.focusRequester(firstFocus) else Modifier,
                            ) {
                                Text(stringResource(if (helperOn) R.string.state_on else R.string.state_off))
                            }
                        }
                        val adbFirst = platform != TvPlatform.AndroidTv && !HomeSetup.helperAvailable
                        ListRow(
                            title = stringResource(R.string.home_setup_adb),
                            subtitle = stringResource(R.string.home_setup_adb_hint),
                            onClick = { page = SetupPage.Adb },
                            modifier = if (adbFirst) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                        if (platform != TvPlatform.AndroidTv && !HomeSetup.helperAvailable) {
                            Text(stringResource(R.string.home_setup_app_list_hint), color = theme.text.copy(alpha = 0.7f))
                        }
                    }
                    // Play policy: explain what the accessibility service does before sending people to turn it on.
                    SetupPage.HelperDisclosure -> {
                        Text(stringResource(R.string.home_setup_helper_disclosure), color = theme.text)
                        Spacer(Modifier.height(8.dp))
                        ListRow(
                            title = stringResource(R.string.home_setup_open_accessibility),
                            onClick = { context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                    }
                    SetupPage.Adb -> {
                        val ip = HomeSetup.ipAddress(context) ?: stringResource(R.string.home_setup_adb_no_ip)
                        val launcher = HomeSetup.stockLauncher(context) ?: HomeSetup.GOOGLE_TV_LAUNCHER
                        Text(stringResource(R.string.home_setup_adb_steps), color = theme.text)
                        Command("adb connect $ip:5555\nadb shell pm disable-user --user 0 $launcher")
                        Text(stringResource(R.string.home_setup_adb_undo), color = theme.text)
                        Command("adb shell pm enable $launcher")
                        ListRow(stringResource(R.string.action_done), onClick = { page = SetupPage.Options }, modifier = Modifier.focusRequester(firstFocus))
                    }
                }
            }
        }
    }

    LaunchedEffect(page) { firstFocus.requestFocusWhenReady() }
}

@Composable
private fun Command(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(16.dp),
    )
}
