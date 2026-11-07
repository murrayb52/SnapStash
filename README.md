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
  MainActivity.kt    Entry point; routes shortcut intents straight to a camera
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
