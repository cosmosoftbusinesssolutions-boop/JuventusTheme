package com.cosmosoft.tvtheme.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** One home-screen look. Add a new entry to [LauncherThemes] to ship another theme. */
data class LauncherTheme(
    val name: String,
    val background: List<Color>,
    val accent: Color,
    val card: Color,
    val text: Color,
)

/** Black and white with gold highlights: the default look. */
val BianconeriTheme = LauncherTheme(
    name = "Bianconeri",
    background = listOf(Color(0xFF000000), Color(0xFF111111), Color(0xFF1E1E1E)),
    accent = Color(0xFFD4AF37),
    card = Color(0xFF1C1C1C),
    text = Color(0xFFFFFFFF),
)

// Saved choices refer to presets by position, so add new ones at the end.
val LauncherThemes = listOf(
    LauncherTheme(
        name = "Aurora",
        background = listOf(Color(0xFF0B1026), Color(0xFF1B1F3B), Color(0xFF3A1F6B)),
        accent = Color(0xFF00D1FF),
        card = Color(0xFF252A4A),
        text = Color(0xFFF2F4FF),
    ),
    LauncherTheme(
        name = "Sunset",
        background = listOf(Color(0xFF2B0F1E), Color(0xFF6B1E3A), Color(0xFFC2523A)),
        accent = Color(0xFFFFC857),
        card = Color(0xFF4A1C2E),
        text = Color(0xFFFFF4EC),
    ),
    LauncherTheme(
        name = "Forest",
        background = listOf(Color(0xFF07170F), Color(0xFF0F2E1F), Color(0xFF1E5034)),
        accent = Color(0xFF7DFFB0),
        card = Color(0xFF173A28),
        text = Color(0xFFEFFFF5),
    ),
    LauncherTheme(
        name = "Midnight",
        background = listOf(Color(0xFF000000), Color(0xFF0D0D0D), Color(0xFF1A1A1A)),
        accent = Color(0xFFFFFFFF),
        card = Color(0xFF222222),
        text = Color(0xFFFFFFFF),
    ),
    BianconeriTheme,
)

val BianconeriPresetIndex = LauncherThemes.indexOf(BianconeriTheme)

val LocalLauncherTheme = staticCompositionLocalOf { BianconeriTheme }

@Composable
fun LauncherThemeProvider(theme: LauncherTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = theme.accent,
            surface = theme.card,
            onSurface = theme.text,
            background = theme.background.first(),
            onBackground = theme.text,
        ),
    ) {
        CompositionLocalProvider(LocalLauncherTheme provides theme, content = content)
    }
}
