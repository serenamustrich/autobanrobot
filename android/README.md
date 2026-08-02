# AutoBanRobot Android

Android WebView browser for X with the shared AutoBanRobot matching engine.

## Build

Open the `android/` directory in Android Studio. The project uses the installed
Android SDK, Gradle, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, and compile SDK
35. From a terminal with `ANDROID_SDK_ROOT` configured:

```bash
gradle assembleDebug
```

The APK is generated at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Auto Ban behavior

1. Sign in to X inside the app.
2. Enable `自动 Ban 命中账号` in `规则`.
3. The shared page script scans dynamically rendered X posts.
4. Matching accounts enter a persistent native queue.
5. The queue first checks the current relationship and skips accounts the user
   follows. It then calls X's authenticated block endpoint and only reports a
   success after X confirms `blocking=true`.
6. Confirmed records are stored locally and uploaded to `ban.richccy.com`.

Bearer tokens, CSRF tokens, and cookies are used only for the authenticated X
request. They are not included in the telemetry upload.

## Plugin format

Import a `.xplugin` ZIP from `插件`. The ZIP may contain only:

```text
plugin.json
content.js
styles.css
rules.json
```

Example `plugin.json`:

```json
{
  "id": "my-x-filter",
  "name": "My X Filter",
  "version": "1.0.0",
  "permissions": ["read_page", "hide_content"]
}
```

Plugin files are capped at 2 MB and extracted with path traversal checks.
Plugins run as page content scripts; the native bridge exposes only the narrow
queue and toast methods, not arbitrary filesystem or network APIs.

## Current verification boundary

The debug APK builds locally. Real-device verification still needs an Android
emulator or phone with an X login session; X can change its web API headers and
DOM at any time, so the first device run should verify login persistence,
dynamic timeline scanning, relationship checks, and confirmed Ban state.
