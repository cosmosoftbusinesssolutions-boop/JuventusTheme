package com.cosmosoft.tvtheme.ui.plex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.plex.PlexServer
import com.cosmosoft.tvtheme.plex.PlexSession
import com.cosmosoft.tvtheme.ui.ListRow
import com.cosmosoft.tvtheme.ui.SectionTitle
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface SignInStep {
    data object Loading : SignInStep
    data class ShowCode(val code: String) : SignInStep
    data object FindingServers : SignInStep
    data class ChooseServer(val servers: List<PlexServer>) : SignInStep
    data object NoServers : SignInStep
    data class Failed(val message: Int) : SignInStep
}

/**
 * Links a Plex account with the PIN flow: we show a short code, the user enters it at plex.tv/link
 * on another device, and plex.tv hands us a token. No password is ever typed on the TV.
 */
@Composable
fun PlexSignInDialog(session: PlexSession, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<SignInStep>(SignInStep.Loading) }

    LaunchedEffect(attempt) {
        step = SignInStep.Loading
        try {
            val pin = withContext(Dispatchers.IO) { session.api.createPin() }
            step = SignInStep.ShowCode(pin.code)
            val deadline = System.currentTimeMillis() + pin.expiresInSeconds * 1_000L
            var token: String? = null
            while (token == null && System.currentTimeMillis() < deadline) {
                delay(2_000)
                token = withContext(Dispatchers.IO) { runCatching { session.api.checkPin(pin.id) }.getOrNull() }
            }
            if (token == null) {
                step = SignInStep.Failed(R.string.plex_expired)
                return@LaunchedEffect
            }
            val name = withContext(Dispatchers.IO) { runCatching { session.api.accountName(token) }.getOrNull() }
            session.signIn(token, name)
            step = SignInStep.FindingServers
            val found = findServers(session)
            if (found is SignInStep.ChooseServer && found.servers.size == 1) {
                // Most people have one server: pick it for them and finish.
                session.selectServer(found.servers.single())
                session.contentChanged()
                onDismiss()
            } else {
                step = found
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            step = SignInStep.Failed(R.string.plex_error)
        }
    }

    PlexDialogFrame(onDismiss) {
        SectionTitle(stringResource(R.string.plex_link_title))
        Spacer(Modifier.height(24.dp))
        SignInStepContent(step, session, onRetry = { attempt++ }, onDismiss = onDismiss)
    }
}

/** Lets a signed-in user switch to another server on their account. */
@Composable
fun PlexServerPickerDialog(session: PlexSession, onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<SignInStep>(SignInStep.FindingServers) }
    LaunchedEffect(attempt) {
        step = SignInStep.FindingServers
        step = try {
            findServers(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SignInStep.Failed(R.string.plex_error)
        }
    }
    PlexDialogFrame(onDismiss) {
        SectionTitle(stringResource(R.string.plex_choose_server))
        Spacer(Modifier.height(24.dp))
        SignInStepContent(step, session, onRetry = { attempt++ }, onDismiss = onDismiss)
    }
}

private suspend fun findServers(session: PlexSession): SignInStep {
    val token = session.token ?: return SignInStep.NoServers
    val servers = withContext(Dispatchers.IO) { session.api.servers(token) }
    return if (servers.isEmpty()) SignInStep.NoServers else SignInStep.ChooseServer(servers)
}

@Composable
private fun SignInStepContent(step: SignInStep, session: PlexSession, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    val focus = remember { FocusRequester() }
    when (step) {
        SignInStep.Loading -> Text(stringResource(R.string.plex_loading), color = theme.text)
        SignInStep.FindingServers -> Text(stringResource(R.string.plex_finding_servers), color = theme.text)
        is SignInStep.ShowCode -> Column {
            Text(stringResource(R.string.plex_link_step), color = theme.text, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.plex_link_url),
                color = theme.accent,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.plex_link_enter), color = theme.text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = step.code,
                color = theme.text,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 16.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.plex_link_waiting), color = theme.text.copy(alpha = 0.7f))
        }
        is SignInStep.ChooseServer -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            step.servers.forEachIndexed { index, server ->
                ListRow(
                    title = server.name,
                    subtitle = server.uri,
                    onClick = {
                        session.selectServer(server)
                        session.contentChanged()
                        onDismiss()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                )
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        SignInStep.NoServers -> Column {
            Text(stringResource(R.string.plex_no_servers), color = theme.text)
            Spacer(Modifier.height(16.dp))
            ListRow(stringResource(R.string.action_done), onClick = onDismiss, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        is SignInStep.Failed -> Column {
            Text(stringResource(step.message), color = theme.text)
            Spacer(Modifier.height(16.dp))
            ListRow(stringResource(R.string.action_retry), onClick = onRetry, modifier = Modifier.focusRequester(focus))
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }
}

@Composable
private fun PlexDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val theme = LocalLauncherTheme.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(720.dp)
                    .background(theme.card, RoundedCornerShape(16.dp))
                    .padding(40.dp),
            ) {
                content()
            }
        }
    }
}
