# Reels Counter

Counts the Instagram Reels and YouTube Shorts you watch. It shows the count in a pinned notification (the status-bar icon *is* the number) and in an optional black pill near the notch. You can set daily limits on the number of reels and on minutes watched. Once a limit is reached, opening Reels or Shorts shows a toast and the app goes back out of the feed automatically.

## Build (command line)

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
.\gradlew.bat testDebugUnitTest assembleDebug
```
APK: `app\build\outputs\apk\debug\app-debug.apk`

## Install on your phone

1. On the phone: Settings → About phone → tap **Build number** 7 times, then Developer options → **USB debugging** on.
2. Plug in the phone, accept the prompt, then run:
   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
   ```
   (Or copy the APK to the phone and open it.)
3. Open **Reels Counter** and follow the setup card:
   - Allow notifications.
   - Android 13+: **App info → ⋮ → Allow restricted settings** (needed for sideloaded apps).
   - Settings → Accessibility → **Reels Counter** → on.
   - Optional: remove battery restrictions.

## How it works

- `service/ReelsAccessibilityService.kt` detects the Reels/Shorts feed by view ID, counts swipes, adds up watch time and enforces limits.
- `detect/ReelsDetector.kt` holds the built-in view IDs: IG `clips_viewer_view_pager`, YT `reel_player_page_container`, `reel_progress_bar`, `reel_recycler`.
- If an app update breaks detection: open the feed, then go to **Settings → Detection (advanced)** in the app. It lists the view IDs that were on screen. Add the right one as an "extra ID", and no rebuild is needed.
- Data stays on the device: Room (`reel_events`, `watch_time`) and DataStore.

Debug logs: `adb logcat -s ReelsCounter`
