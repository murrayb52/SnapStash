# SnapStash

A minimal Android camera with big buttons that you define yourself, for example **Clicks** and
**Vitality**. Each button opens the camera, and each photo is saved straight into that button's own
folder with the date and time in the filename. The app only captures and files photos. All
processing happens later, in one batch. See [docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md) for the
original what and why.

## How it works

**One-time setup:** open **⚙ Settings → + Add button**. Give the button a name, then tap
**Choose folder…**. The system folder picker opens at `Pictures/SnapStash`, where you can pick
an existing folder or create a new one (e.g. `Clicks`). Add as many buttons as you like. You can
rename, reorder, re-folder or delete them at any time. No buttons or folders are built in.

**Everyday use:**

1. Tap a button on the app's home screen, or use a home-screen shortcut that opens that camera
   directly.
2. Tap the shutter. Every tap saves one photo, and the camera stays open for the next one.
3. A green "✓ Saved …" message confirms each photo. If a save fails, for example because the
   folder was deleted, a dialog says **Not saved** and gives the reason.

Photos are full-resolution JPEGs named after the button, e.g. `clicks_2026-11-07_121305.jpg`.
Names sort by time. If two shots land in the same second, the later ones get `_2`, `_3` and so
on, so nothing is overwritten.

A folder can be anywhere the system picker can reach. Keeping them under `Pictures/SnapStash/`
makes them easy to find in any file manager, and Google Photos can back them up: in Google Photos,
open **Library → Photos on device** and turn on backup for each folder once. A folder that a sync
app such as Syncthing or Drive watches works too.

### Home-screen shortcuts

Every button automatically gets a shortcut:

- **Long-press the SnapStash icon** to see your buttons. Drag one to the home screen.
- Or, in Settings, tap **Add to home screen** on a button.
- Or, from the launcher's widgets/shortcuts list, add **SnapStash camera**. It asks which button
  the shortcut should open, then places it.

Renaming a button renames its shortcut. Deleting a button disables its shortcut.

### SnapStash Quick (gesture launcher)

SnapStash installs a second launcher entry, **SnapStash Quick**, made to be opened by a
home-screen gesture (in Octopi or Nova, set a gesture's action to open the app *SnapStash Quick*).
It shows a card that floats up from the bottom of the home screen, with the home screen still
visible around it:

- **Tap a folder** in the list to open SnapStash's camera for it.
- **Swipe up anywhere** to go straight to the phone's normal camera app (the hint is at the bottom
  of the card). It fires as soon as the swipe passes a short distance, without waiting for your
  finger to lift.
- **Tap outside the card**, or press back, to go back to the home screen.
- **Open app** in the corner opens the full app.

The card grows taller as you add folders. Once it reaches the top of the screen, the list scrolls.
How it appears is set in **Settings → Quick launcher**: *Slide up* (default), *Pop*, *Fade* or
*None*. Opening a camera is always instant; only closing back to the home screen animates.

The card is built for speed: plain Android views rather than Compose, a see-through window with no
window animation or splash, and nothing left behind in Recents. While it is showing, it warms up
the camera library in the background so a folder's camera opens faster.

## Framework choice

| Option | Verdict | Why |
| --- | --- | --- |
| **Kotlin + Jetpack Compose + CameraX** | **Chosen** | Google's own stack for Android. CameraX gives an in-app viewfinder with one-tap repeat shots, and the app picks the folder at the moment of capture. |
| Kotlin + system camera intent (`ACTION_IMAGE_CAPTURE`) | Runner-up | Even less code, but it takes only one photo per launch, so each extra receipt photo means a round trip. Quality and behaviour also depend on whichever camera app is installed. |
| Flutter (`camera` plugin) | Not chosen | Works fine, but adds a second toolchain and a plugin layer between the app and the camera. That buys nothing here, because the app is Android only. |
| React Native / Expo (`expo-camera`) | Not chosen | Same trade-off as Flutter, plus JavaScript dependency churn. Expo is easy to start with but heavier to keep running long-term. |
| MacroDroid / Tasker / Open Camera | Rejected in the brief | Needs file watching, or a global save folder setting. |

Other technical decisions:

- **Storage: the system folder picker (Storage Access Framework).** Each button holds a lasting
  permission to the one folder the user picked for it, so folders are fully user-configurable
  without broad storage permissions. The photos are ordinary files that survive uninstalling the
  app. The only runtime permission is the camera.
- **The file is created in the button's folder before the shutter fires.** A missing or
  disconnected folder is reported at once, and a failed capture deletes its empty file.
- **Buttons are stored as a small JSON list** in the app's preferences.
- **minSdk 29 (Android 10)**, which covers effectively every phone still in use.
- **`CAPTURE_MODE_MAXIMIZE_QUALITY`** at the camera's default (highest) resolution keeps receipt
  text legible.
- **Portrait-locked UI, rotation-aware photos.** The screen stays still, but a landscape shot of a
  board is still saved the right way up.
- **No accounts, no network, no settings screen.**

## Building and installing

**Easiest (no tools needed):** every push runs the GitHub Actions workflow
[`Build APK`](.github/workflows/build.yml). Open the latest run under the repo's **Actions** tab,
download the `snapstash-<version>` artifact (e.g. `snapstash-0.1.12`), unzip it, and install the
APK inside on the phone (you will need to allow "Install unknown apps" for your browser
or file manager).

Versions are `<major.minor>.<CI run number>`. Major.minor is `snapstash.version` in
`gradle.properties`, and the run number is also the `versionCode`, so each build is newer than the
last. The installed version is shown at the bottom of Settings.

The `snapstash-<version>` APK is the optimised release build (shrunk by R8, not debuggable), which
starts noticeably faster. A `-debug` APK is uploaded alongside it for troubleshooting.

> Every build, release and debug, is signed with the same debug key, `app/signing/debug.keystore`, so a new build
> installs over the previous one and keeps your buttons. It's a debug key, committed to the repo on
> purpose, and not suitable for Play Store releases.

**Screenshots:** CI also renders the main screens (home, settings, edit folder, camera, quick launcher) with
Roborazzi. For builds of `main` they're published to the
[`ci-screenshots`](../../tree/ci-screenshots) branch, so you can review the UI without installing.

**Locally:** install [Android Studio](https://developer.android.com/studio), open this folder,
and press Run with the phone connected over USB. Or, from a terminal with the Android SDK
installed:

```sh
./gradlew assembleRelease      # APK at app/build/outputs/apk/release/app-release.apk (version 0.1-local)
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

## Project layout

```
app/src/main/java/io/github/murrayb52/snapstash/
  MainActivity.kt    Entry point; routes shortcut intents straight to a camera
  QuickLaunchActivity.kt  "SnapStash Quick": swipe up for the system camera, tap a folder
  HomeScreen.kt      The big buttons (or a prompt to add the first one)
  SettingsScreen.kt  Add / rename / choose folder / reorder / delete buttons
  CameraScreen.kt    CameraX viewfinder, shutter, save confirmation and errors
  CaptureButton.kt   A button: name, folder, colour, filename prefix
  ButtonStore.kt     Persists the buttons
  FolderAccess.kt    Folder picker start location, folder permissions, writing files
  PhotoNamer.kt      Filename scheme
  Shortcuts.kt       Launcher shortcuts kept in sync with the buttons
  ShortcutPickerActivity.kt  "SnapStash camera" shortcut from the launcher: pick which button
```

## Open question

From the brief: is a Vitality capture a camera photo of a physical board, or a screenshot on the
phone? For now every button uses the camera. If it's a screenshot, a button could get a
"move latest screenshot here" mode instead.
