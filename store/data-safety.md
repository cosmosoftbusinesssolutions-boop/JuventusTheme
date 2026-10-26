# Play Console answers – TV Theme (play flavor)

Based on what the code in this repository does. Review each answer yourself before submitting:
you, not the developer of this checklist, are responsible for what you declare.

## App content → Data safety

**Does your app collect or share any of the required user data types?** → **No**

Why this is accurate for the `play` build:
- The app has no servers and no analytics, crash-reporting or advertising SDKs.
- App list, profiles, PINs (hashed), wallpaper and settings stay on the device (backup is disabled).
- Plex data is exchanged directly between the TV and Plex only after the user chooses to connect Plex. Play
  excludes data sent to a third party at the user's own request, when the user expects it to be shared, so it's
  neither collection nor sharing by you. The privacy policy still explains it.
- Purchases are processed by Google Play Billing.

If you later add analytics, crash reporting or your own server, this answer must change.

**Is all of the user data collected by your app encrypted in transit?** → Not asked when you answer "No" above.
(Plex requests use HTTPS, except to Plex servers on your local network, which may only offer HTTP.)

**Do you provide a way for users to request that their data is deleted?** → Not asked when you answer "No".
Data is deleted by uninstalling the app or using "Sign out of Plex" / "Delete profile".

## App content → other sections

| Section | Answer |
|---|---|
| Privacy policy | URL of `docs/privacy-policy.html` once hosted (see PUBLISHING.md) |
| Ads | **No**, the app contains no ads |
| App access | **All functionality is available without special access.** Plex is optional. If reviewers ask, explain they can test Plex with any free Plex account. |
| Content rating | Fill the questionnaire: a utility/launcher with no violence, sexual content, gambling or user-to-user communication. Expect **Everyone / PEGI 3**. |
| Target audience and content | **18 and over**, or 13+. Don't select under-13 age groups: the app isn't designed for children (kids profiles are a parental tool), and selecting them triggers the Families policy. |
| News app | No |
| COVID-19 apps | No |
| Data safety | As above |
| Government apps | No |
| Financial features | None |
| Health | None |

## Permissions used by the play build (for reviewer questions)

| Permission | Why |
|---|---|
| `INTERNET` | Plex sign-in, libraries, artwork and streaming |
| `ACCESS_NETWORK_STATE` | Shows whether the TV is online in Quick settings |
| `com.android.vending.BILLING` | Premium purchase through Google Play |
| `QUERY_ALL_PACKAGES` | **Not used.** The app declares `<queries>` for launcher intents only. |

The play build has **no accessibility service** and **no photo or storage permission**. Users pick a wallpaper
picture through the system photo picker.
