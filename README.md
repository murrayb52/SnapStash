# SnapStash

A minimal Android camera with two big buttons, **Clicks** and **Vitality**. Each button opens the
camera, and each photo is saved straight into that button's own folder with the date and time in
the filename. The app only captures and files photos. All processing happens later, in one batch.
See [docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md) for the what and why.

## How it works

1. Tap **Clicks** or **Vitality** on the app's home screen, or use a home-screen shortcut that
   opens that camera directly.
2. Tap the shutter. Every tap saves one photo, and the camera stays open for the next one
   (receipt, then items).
3. A green "✓ Saved …" message confirms each photo. If a save fails, a dialog says **Not saved**
   and gives the reason.

Photos are saved as full-resolution JPEGs to:

```
Internal storage/Pictures/SnapStash/Clicks/clicks_2026-11-07_121305.jpg
Internal storage/Pictures/SnapStash/Vitality/vitality_2026-11-07_121310.jpg
```

Filenames sort by time. If two shots land in the same second, the later ones get `_2`, `_3` and so
on, so nothing is ever overwritten. Because the folders are under `Pictures/`, any file manager
can browse them, and Google Photos (or another backup app) can back them up: in Google Photos,
open **Library → SnapStash folders** and turn on backup once.

### Home-screen shortcuts

- **Long-press the SnapStash icon** to see *Clicks* and *Vitality*. Drag either one to the home
  screen.
- Or, in the app, tap **Add to home screen** under a button.

## Framework choice

| Option | Verdict | Why |
| --- | --- | --- |
| **Kotlin + Jetpack Compose + CameraX** | **Chosen** | Google's own stack for Android. CameraX gives an in-app viewfinder with one-tap repeat shots, and the app picks the folder at the moment of capture. |
| Kotlin + system camera intent (`ACTION_IMAGE_CAPTURE`) | Runner-up | Even less code, but it takes only one photo per launch, so each extra receipt photo means a round trip. Quality and behaviour also depend on whichever camera app is installed. |
| Flutter (`camera` plugin) | Not chosen | Works fine, but adds a second toolchain and a plugin layer between the app and the camera. That buys nothing here, because the app is Android only. |
| React Native / Expo (`expo-camera`) | Not chosen | Same trade-off as Flutter, plus JavaScript dependency churn. Expo is easy to start with but heavier to keep running long-term. |
| MacroDroid / Tasker / Open Camera | Rejected in the brief | Needs file watching, or a global save folder setting. |

Other technical decisions:

- **Storage: MediaStore with `RELATIVE_PATH`.** No storage permission is needed, the photos are
  ordinary shared files, and they survive uninstalling the app. The only runtime permission is the
  camera.
- **minSdk 29 (Android 10).** This is the oldest version where the storage approach above works
  without legacy permissions. It covers effectively every phone still in use.
- **`CAPTURE_MODE_MAXIMIZE_QUALITY`** at the camera's default (highest) resolution keeps receipt
  text legible.
- **Portrait-locked UI, rotation-aware photos.** The screen stays still, but a landscape shot of a
  board is still saved the right way up.
- **No accounts, no network, no settings screen.**

## Building and installing

**Easiest (no tools needed):** every push runs the GitHub Actions workflow
[`Build APK`](.github/workflows/build.yml). Open the latest run under the repo's **Actions** tab,
download the `snapstash-debug-apk` artifact, unzip it, and install `app-debug.apk` on the phone
(you will need to allow "Install unknown apps" for your browser or file manager).

> Each CI run signs with a fresh debug key. To install a newer build over an older one, you may
> first need to uninstall the old one. Your photos are safe either way, because they live in
> `Pictures/`, not inside the app. A fixed release signing key can be added later if this gets
> annoying.

**Locally:** install [Android Studio](https://developer.android.com/studio), open this folder,
and press Run with the phone connected over USB. Or, from a terminal with the Android SDK
installed:

```sh
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

## Project layout

```
app/src/main/java/io/github/murrayb52/snapstash/
  MainActivity.kt   Entry point; routes shortcut intents straight to a camera
  HomeScreen.kt     The two big buttons
  CameraScreen.kt   CameraX viewfinder, shutter, save confirmation and errors
  Category.kt       Clicks / Vitality: label, folder, filename prefix, colour
  PhotoNamer.kt     Filename and folder scheme
  Shortcuts.kt      Pin-to-home-screen helper
app/src/main/res/xml/shortcuts.xml   Long-press launcher shortcuts
```

## Open question

From the brief: is a Vitality capture a camera photo of a physical board, or a screenshot on the
phone? For now both buttons use the camera. If it's a screenshot, the Vitality button could
instead pick or move the latest screenshot into `Pictures/SnapStash/Vitality/`.
