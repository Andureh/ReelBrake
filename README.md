# ReelBrake ⏸️

An Android app that puts a pause between you and the Instagram Reels loop. Watch the reel a friend sent you, but think twice before you swipe to the next twenty.

ReelBrake **does not modify Instagram**. It is a separate app that uses Android's accessibility service to notice when you are watching Reels and shows a full-screen pause on top. No account and no ads. The app does not even have the internet permission.

**[⬇️ Download the latest version (APK)](../../releases/latest)** · Android 8.0 or newer

---

## How it works

ReelBrake watches the places where the loop usually starts and lets you choose what happens for each one:

| Opened from | Default | What happens |
|---|---|---|
| **Messages** | Pause | The reel someone sent you is free. Swiping to the next one brings up the pause. |
| **Feed** | Pause | Same: the reel you tapped is free, the next one is not. |
| **Reels tab** | Block | Sends you straight back to Home. |
| **Explore, profiles, others** | Pause | Same as the feed. |

Each source can be set to **Free** (ReelBrake stays out of the way), **Pause** or **Block**. A master switch turns everything off without losing your choices.

### The pause

> **Pause.**
> You're on reel #2 (from your messages).
> Is this really what you opened Instagram for?
>
> **[ Leave Reels ]**  ·  [ One more (10) ]

- **Leave Reels** takes you back to where you came from (the chat, the feed…).
- **One more** only unlocks after a countdown that grows every time you use it: 10 s, 20 s, 30 s… up to 60 s. It only resets after 30 minutes without Reels.
- While the pause is on screen, ReelBrake takes audio focus so the reel behind it stops or goes quiet.
- Leaving Reels any way you like (the button, gesture back, switching apps) counts as an exit. The next reel someone sends you is free again.

### Settings
- Free reels before the pause (default 1; 0 pauses right away).
- Base countdown for "one more" (5–30 s).
- Safety net: maximum time on the same reel before the pause appears anyway, in case a swipe is not detected (default 120 s).
- Today's counters: reels watched, pauses, exits and "one more" taps.

### Languages
🇬🇧 English · 🇵🇹 Português · 🇪🇸 Español · 🇫🇷 Français · 🇩🇪 Deutsch · 🇮🇹 Italiano

The app follows the system language. On Android 13+ you can also pick a language just for ReelBrake in **Settings → System → Languages → App languages**.

---

## Installation

1. Download `ReelBrake.apk` from the [Releases page](../../releases/latest) and install it. If Android warns about "unknown sources", allow the installation.
2. Open ReelBrake and tap **Open Accessibility**, then turn ReelBrake on (usually under **Installed apps** or **Downloaded services**).
3. **Android 13 and newer:** sideloaded apps can't turn on accessibility services straight away. If the switch is greyed out ("restricted setting"), open **App info → ⋮ menu → Allow restricted settings** and try again. The app has a button that opens App info for you.
4. Optional: if the brake stops working on its own after a while, remove ReelBrake from battery optimization. Some brands (Xiaomi, Samsung, Huawei…) kill background services aggressively.

To try it out, open a reel someone sent you in a chat and swipe to the next one.

## Privacy

- ReelBrake only reads the **structure** of Instagram's screens (internal view IDs such as `clips_viewer_view_pager` and which tab is selected). It never reads message text, captions or usernames.
- It ignores every app except Instagram, apart from noticing when you leave it.
- It has **no internet permission**. Nothing leaves your phone. Settings and the daily counters are stored locally.

## When detection breaks

Instagram renames its internal view IDs from time to time. If ReelBrake stops reacting:

1. Open Instagram, go to the screen that should trigger the pause (for example, a reel opened from a chat) and swipe a couple of times.
2. Open ReelBrake, scroll down to **Diagnostics** and tap **Copy report**. The report contains view IDs and detected states only, never screen text.
3. Open an issue and paste the report.

The IDs live in [`InstagramReader.kt`](app/src/main/java/com/andureh/reelbrake/InstagramReader.kt).

---

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 34).

```bash
./gradlew testDebugUnitTest   # unit tests for the pause logic
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the local debug key, so they can be sideloaded directly.

## Project structure

```
app/src/main/java/com/andureh/reelbrake/
├── ReelPolicy.kt        # Pure Kotlin state machine: when to pause, block or leave (unit tested)
├── ReelBrakeService.kt  # AccessibilityService: feeds Instagram events to the policy, runs its actions
├── InstagramReader.kt   # Reads view IDs to detect the Reels viewer, selected tab and the source screen
├── BrakeOverlay.kt      # Full-screen pause (accessibility overlay, no "draw over apps" permission)
├── MainActivity.kt      # Settings, status and today's counters (Jetpack Compose)
├── Prefs.kt             # Settings and daily stats (SharedPreferences)
└── Diagnostics.kt       # In-memory log of detected states for troubleshooting
```

Translations live in `app/src/main/res/values*/strings.xml`.

## Credits

The Instagram view IDs and the approach of tracking the source screen were learned from [LifeIsShort](https://github.com/Feuscel/LifeIsShort).

ReelBrake is not affiliated with, endorsed by or connected to Instagram or Meta.

## License

[MIT](LICENSE)
