package com.cosmosoft.tvtheme.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cosmosoft.tvtheme.R
import com.cosmosoft.tvtheme.ui.theme.LocalLauncherTheme

private const val PIN_LENGTH = 4

/**
 * A 4-digit PIN pad usable with the remote's arrows (and number keys, if it has them).
 * [onPin] gets each complete entry and returns an error message to show, or null to accept it.
 */
@Composable
fun PinDialog(title: String, onPin: (String) -> String?, onDismiss: () -> Unit) {
    val theme = LocalLauncherTheme.current
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val firstKey = remember { FocusRequester() }

    fun press(digit: Char) {
        if (entered.length >= PIN_LENGTH) return
        entered += digit
        error = null
        if (entered.length == PIN_LENGTH) {
            error = onPin(entered)
            entered = ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(theme.card, RoundedCornerShape(16.dp))
                .padding(32.dp)
                .onPreviewKeyEvent { event ->
                    val digit = event.key.digit()
                    if (event.type == KeyEventType.KeyDown && digit != null) {
                        press(digit)
                        true
                    } else {
                        false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = theme.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(
                                if (index < entered.length) theme.accent else theme.text.copy(alpha = 0.25f),
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(error ?: " ", color = theme.accent, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            val rows = listOf("123", "456", "789")
            rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { i, digit ->
                        val modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstKey) else Modifier
                        PadKey(digit.toString(), modifier) { press(digit) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PadKey(stringResource(R.string.pin_cancel), wide = true, onClick = onDismiss)
                PadKey("0") { press('0') }
                PadKey(stringResource(R.string.pin_delete), wide = true) { entered = entered.dropLast(1) }
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }
}

@Composable
private fun PadKey(label: String, modifier: Modifier = Modifier, wide: Boolean = false, onClick: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = if (wide) 96.dp else 72.dp, height = 56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.background.first().copy(alpha = 0.6f),
            contentColor = theme.text,
            focusedContainerColor = theme.accent,
            focusedContentColor = theme.background.first(),
        ),
    ) {
        Box(Modifier.width(if (wide) 96.dp else 72.dp).height(56.dp), contentAlignment = Alignment.Center) {
            Text(label, style = if (wide) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge)
        }
    }
}

private fun Key.digit(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** Asks for a new PIN twice and passes it to [onSet] when both entries match. */
@Composable
fun SetPinDialog(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    val mismatch = stringResource(R.string.pin_mismatch)
    PinDialog(
        title = stringResource(if (first == null) R.string.pin_new else R.string.pin_confirm),
        onPin = { pin ->
            val chosen = first
            when {
                chosen == null -> {
                    first = pin
                    null
                }
                chosen == pin -> {
                    onSet(pin)
                    null
                }
                else -> {
                    first = null
                    mismatch
                }
            }
        },
        onDismiss = onDismiss,
    )
}
