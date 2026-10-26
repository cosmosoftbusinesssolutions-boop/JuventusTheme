# TV Theme

A themeable home-screen launcher for Android TV, built with Jetpack Compose for TV.

- **Side menu:** Home, Apps, Customize and Quick settings. It shows icons only, and widens to show labels when focused.
- **Home:** a clock, then rows for Favorites, TV apps (shown with their banners) and More apps.
- **Long-press any app:** open it, add it to or remove it from favorites, move it left or right in favorites, hide it, or open its app info.
- **Apps:** a grid of every app that isn't hidden.
- **Customize:**
  - Wallpaper: your own picture, three built-in pictures (Nebula, Lagoon, Ember), or four color themes. Picture wallpapers recolor the launcher using colors pulled from the image.
  - Turn each Home row on or off.
  - Show hidden apps again.
  - A shortcut to the default home app setting.
- **Quick settings:** a side sheet laid out like the TV's own Settings, in TV Theme's colors.
  - Status: network and volume.
  - TV Theme: Customize, and home screen setup.
  - TV settings: shortcuts with an icon and short description for each system screen.
- **Profiles (Premium):**
  - Each profile has its own favorites, hidden apps, rows and wallpaper, and can have a 4-digit PIN.
  - "Who's watching?" can appear when the launcher starts.
  - Restricted (kids) profiles need the Main profile's PIN to open Customize or Quick settings, see only the apps you allow, and only see Plex titles up to a chosen rating.
- **Trial and purchase:** a 14-day free trial from install, then a lifetime purchase or a monthly subscription through Google Play. While locked, the basic launcher keeps working, but Plex, picture wallpapers and extra profiles are unavailable. To sell it, create these products in the Play Console:
  - `tvtheme_lifetime`, a one-time product.
  - `tvtheme_monthly`, a subscription with one base plan.
- **Home screen setup:** a card on Home (and a Customize entry) explains how to make the Home button open TV Theme. There are three ways:
  - Choose the home app (Android TV).
  - The **Home button helper**, an accessibility service for Google TV and Fire TV, shown with a disclosure first.
  - ADB commands that use this TV's IP address.
- **Remote buttons:** Home always returns to the Home screen. Back returns to Home but never closes the launcher.
- **Plex:** connect in Customize → Plex. The TV shows a 4-character code; enter it at plex.tv/link on your phone or computer. Your password is never typed on the TV.
  - Home gains Continue watching, Watchlist and "Recently added in …" rows for each movie or TV library. Each can be turned off in Customize.
  - Detail pages show artwork, info and cast, with Play or Resume, Play from beginning, Mark as watched or unwatched, and Add to or Remove from watchlist. Shows list their seasons and episodes.
  - The built-in player (Media3) plays the original file. If the TV can't decode it, the player asks the server for an HLS stream instead. Progress is reported to Plex so resume points and Continue watching stay in sync.
  - A watchlist title that isn't in your libraries shows its catalog info, with no Play button.

## Project layout

Kotlin files are under `app/src/main/java/com/cosmosoft/tvtheme/`.

| File | What it does |
| --- | --- |
| `ui/LauncherApp.kt` | Root screen: wallpaper, side menu, current screen and overlays. |
| `ui/NavRail.kt` | Side menu. |
| `ui/HomeScreen.kt` / `ui/AppsScreen.kt` / `ui/CustomizeScreen.kt` | The three screens. |
| `ui/QuickSettingsPanel.kt` | Quick settings panel on the right. |
| `ui/AppOptionsDialog.kt` | Long-press menu. |
| `ui/Components.kt` | Shared cards, rows, sizes and launch helpers. |
| `ui/theme/LauncherTheme.kt` | Color themes. Add a `LauncherTheme(...)` to `LauncherThemes` to add one. |
| `data/Wallpapers.kt` | Built-in wallpapers, loading your own picture, and colors pulled from a picture. Add a `BuiltinWallpaper` to add a built-in picture. |
| `data/LauncherSettings.kt` | Saved favorites, hidden apps, row toggles and wallpaper choice. |
| `data/AppRepository.kt` | Finds installed apps and loads their banners and icons. |
| `plex/PlexApi.kt` | Every Plex call: plex.tv sign-in and servers, your server's libraries and watch state, the watchlist, and image and stream URLs. |
| `plex/PlexClient.kt` / `plex/PlexModels.kt` | HTTP and Plex headers, and the parsed server, library and item types. |
| `plex/PlexSession.kt` | Saved Plex token and chosen server. |
| `ui/plex/PlexSignIn.kt` | Link-code sign-in and the server picker. |
| `ui/plex/MediaRows.kt` | Loads the Home media rows and draws poster and landscape cards. |
| `ui/plex/MediaDetail.kt` | Movie, show and episode pages. |
| `PlayerActivity.kt` | Video player and progress reporting. |
| `data/Profiles.kt` | Profiles, PINs (stored hashed) and rating limits. |
| `ui/profiles/` | "Who's watching?" picker, PIN pad, profile editor. |
| `billing/Premium.kt` | Trial and Google Play Billing. |
| `home/HomeButtonService.kt` / `home/HomeSetup.kt` | Home button helper (accessibility service) and default-launcher checks. |
| `ui/HomeSetupDialog.kt` | Home screen setup guide. |
| `MainActivity.kt` | Hosts the UI, reloads apps on install or uninstall, and handles the Home button. |
| `AndroidManifest.xml` (under `app/src/main/`) | `HOME` intent filter, plus the network-status and photo permissions. |

## Toolchain

Everything is installed locally under `D:\MyPersonal\AndroidTools`:
JDK 17 (`jdk17`), Android SDK (`sdk`), and Gradle 8.9. `local.properties` points at the SDK.

## Build

There are two builds:
- **play**: for Google Play. No Home button helper (accessibility service), and it uses the system photo picker.
- **sideload**: the APK you install yourself. It includes the Home button helper and the in-app photo grid.

```powershell
$env:JAVA_HOME = "D:\MyPersonal\AndroidTools\jdk17"
.\gradlew.bat assembleSideloadRelease   # APK for USB stick / adb
.\gradlew.bat bundlePlayRelease         # App Bundle to upload to Google Play
```

Outputs:
- `app\build\outputs\apk\sideload\release\app-sideload-release.apk`
- `app\build\outputs\bundle\playRelease\app-play-release.aab`

See [PUBLISHING.md](PUBLISHING.md) for the Google Play steps.

## Install on a TV

1. On the TV: **Settings → Device Preferences → About**, then press **Build** 7 times to enable Developer options.
2. In **Developer options**, turn on **USB debugging** (and **Network debugging**, if your TV offers it).
3. Connect and install from this PC, using the TV's IP address:

```powershell
$adb = "D:\MyPersonal\AndroidTools\sdk\platform-tools\adb.exe"
& $adb connect <TV_IP>:5555
& $adb install -r app\build\outputs\apk\sideload\release\app-sideload-release.apk
```

4. Press **Home** on the remote. If the TV asks which launcher to use, choose **TV Theme**.

On Google TV devices (Chromecast with Google TV, and newer Sony or TCL models), the system home screen
can't be replaced through the settings. You can open TV Theme from the app list, or set it as the home
screen over adb (this disables the stock launcher, so note the package name so you can undo it):

```powershell
& $adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
# undo:
& $adb shell pm enable com.google.android.apps.tv.launcherx
```

## Testing without a TV

A virtual TV (`TV_1080p`, Android TV 14) is already set up in `D:\MyPersonal\AndroidTools\avd`.
It lives on D: because it needs about 7.4 GB, more than C: has free.

```powershell
$env:ANDROID_AVD_HOME = "D:\MyPersonal\AndroidTools\avd"
& "D:\MyPersonal\AndroidTools\sdk\emulator\emulator.exe" -avd TV_1080p
```

Once it has booted, install with the `adb install` command above. In the emulator window, use the
arrow keys and Enter as the remote.

On the emulator, the stock launcher (`com.google.android.tvlauncher`) has been turned off so Home opens
TV Theme. To turn it back on: `& $adb shell pm enable com.google.android.tvlauncher`
