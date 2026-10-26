# Publishing TV Theme on Google Play (Android TV)

The app is prepared for Google Play. These are the steps only you can do: they need your Google account,
identity, payment and legal agreement.

## 1. What's in the repo for this

| Item | Where |
|---|---|
| Google Play build (App Bundle) | `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab` |
| Sideload APK (USB stick / adb) | `./gradlew assembleSideloadRelease` → `app/build/outputs/apk/sideload/release/` |
| Privacy policy | `docs/privacy-policy.html` |
| Store listing text | `store/listing.md` |
| Data safety and app-content answers | `store/data-safety.md` |
| Icon, feature graphic, TV banner, TV screenshots | `store/graphics/` (regenerate with `store/graphics/src/render.ps1`) |

### Why there are two builds
- **play**: what Google Play reviews. No accessibility service: Play rejects apps that use one for anything
  other than helping users with disabilities. No photo permission: Play requires the system photo picker for
  occasional photo use. TV-only (`android.software.leanback` required).
- **sideload**: the APK you install yourself. Keeps the Home button helper (for Google TV / Fire TV) and the
  built-in photo grid.

Both use the same package name and signing key, so a sideloaded install can later be updated from Play (and the
reverse), as long as the version code goes up.

## 2. Before you upload

1. **Fill in the placeholders** in `docs/privacy-policy.html` and `store/listing.md`:
   - `[DEVELOPER NAME]`: your name or company, as shown on Play.
   - `[SUPPORT EMAIL]`: a public support address. It's shown on the store page.
2. **Host the privacy policy.** The simplest way:
   GitHub → JuventusTheme → Settings → Pages → Source: *Deploy from a branch*, branch `main`, folder `/docs`.
   After a minute it's live at
   `https://cosmosoftbusinesssolutions-boop.github.io/JuventusTheme/privacy-policy.html`.
   - Pages on a free account needs a **public** repository. If you keep the repo private, host the page anywhere
     else (your website, Google Sites).
   - Consider renaming the repository (Settings → General) so it doesn't carry a football club's trademark. If you
     do, the Pages URL changes too.
3. **Back up the signing key** `D:\MyPersonal\AndroidTools\signing\tvtheme-release.jks` and `keystore.properties`.
   With Play App Signing (step 4) this key becomes your *upload key*. If you lose it, Google can reset it, but
   that takes a support request.

## 3. Create the developer account (one time)
1. Go to https://play.google.com/console and sign up. There's a one-time **US $25** fee and identity verification.
2. **New personal accounts must run a closed test first.** Before you can publish to everyone, Google requires a
   closed test with at least **12 testers opted in for 14 continuous days**. Organization accounts (with a
   D-U-N-S number) are exempt.
3. To sell Premium you need a **payments profile** (Play Console → Setup → Payments profile).

## 4. Create the app
1. Play Console → **Create app**: name "TV Theme – Home Screen", default language, **App**, **Free** (with in-app
   purchases). Accept the declarations.
2. **Setup → App signing**: keep Google's default, *Play App Signing*. Your keystore becomes the upload key.
3. **Advanced settings → Form factors**: add **Android TV** and opt in to the TV track. TV apps go through an extra
   Android TV quality review (D-pad navigation, banner, no touch-only UI). This app was built for that.
4. Make sure the release only targets TV: the manifest requires `android.software.leanback`, so phones are filtered
   out automatically.

## 5. Store listing and app content
- **Main store listing**: paste from `store/listing.md` and upload the graphics from `store/graphics/`.
  - The Android TV banner and at least one TV screenshot are required for the TV form factor.
  - The listing also asks for phone screenshots. Because the app is TV-only, you can add the TV screenshots
    there too.
- **App content**: answer using `store/data-safety.md` (privacy policy, ads: no, data safety: no data collected,
  target audience: 18+, content rating questionnaire).

## 6. In-app products (Premium)
Play Console → **Monetize → Products**:
1. **In-app products → Create**: Product ID **`tvtheme_lifetime`**, e.g. "TV Theme Premium (lifetime)". Set a
   price and activate it.
2. **Subscriptions → Create**: Product ID **`tvtheme_monthly`**. Add one auto-renewing monthly **base plan**, set
   a price and activate it.

The IDs must match exactly. They're in `app/src/main/java/com/cosmosoft/tvtheme/billing/Premium.kt`. Products
only appear in the app once a build has been uploaded to a test track and the tester's account is on that track's
list.

## 7. Upload and test
1. **Testing → Internal testing → Create release** → upload `app-play-release.aab` → add release notes → roll out.
   Add your own Google account as a tester and install the app on your TV from the tester link.
2. Check on a real TV that:
   - Home, Customize, profiles and Quick settings work with the remote.
   - Premium shows your prices, and a test purchase works. Add your account under
     **Setup → License testing** so purchases are free test purchases.
   - Plex sign-in works.
3. **Closed testing** (required for new personal accounts): create a closed track, add at least 12 testers'
   emails and keep it running 14 days.
4. **Production → Create release** → upload the same or a newer bundle → **Send for review**. TV review can take
   from a few days to a couple of weeks.

## 8. Every update after that
1. Raise `versionCode` (and `versionName`) in `app/build.gradle.kts`.
2. `./gradlew bundlePlayRelease`, then upload the new `.aab` to a track.

## Things that commonly get TV apps rejected, already handled
- ✅ Works entirely with a D-pad, with a visible focus state on everything
- ✅ Leanback launcher intent and a 320×180 TV banner in the app
- ✅ No touchscreen required
- ✅ No accessibility-service misuse and no broad photo access in the Play build
- ✅ Target SDK 36
- ✅ Privacy policy covering Plex, purchases and local data
- ⚠️ Plex trademark: the listing says Plex isn't affiliated. Don't use the Plex logo in your graphics.
- ⚠️ Don't mention Juventus or use club crests anywhere in the listing or graphics.
