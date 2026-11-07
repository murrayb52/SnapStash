# Receipt & Vitality Camera App: Product Brief

Nov 7, 2026 · @Trinity

## What we're building

A minimal Android app that wraps the camera and has exactly two big buttons: **Clicks** and **Vitality**. Tapping a button opens the camera, and the photo is saved straight into that button's own folder with a date in the filename. There is no sorting, tagging or menu to deal with.

The point is to build up a clean pile of photos for tax time. Clicks photos are the receipt plus the items on it. Vitality photos are the weekly game board. All processing happens later, in one go, so the app only has to capture and file reliably.

## Decisions so far

We are building a small custom app rather than stitching together existing tools, because every existing route either needs fiddling or can't do two separate save folders.

| Option | Verdict | Why |
| --- | --- | --- |
| Custom two-button camera app | **Chosen** | One tap to the right camera mode and the right folder. Nothing to tweak after setup. |
| MacroDroid or Tasker | Rejected | Needs a file-watching trigger to move photos after the fact. Timing is fiddly and breaks easily. |
| Open Camera with a set save folder | Rejected | The save folder is one global setting, so switching between Clicks and Vitality means opening settings each time. |
| Share-sheet shortcuts into cloud folders | Rejected | More taps than the user wants, and still needs sorting afterwards. |

Other decisions:

- **Platform:** Android only.
- **No automatic categorizing or OCR.** The user will process everything in one batch later, which is simpler.
- **Priorities:** frictionless and robust, set up once and then just use.
- **Who builds it:** a separate Claude Code session, which can work out the technical details. This brief only fixes what and why.

## How it works

The whole experience is: pick a button, take photos, done.

1. Open the app, or tap a home screen shortcut that goes straight to one of the two buttons.
2. Tap **Clicks** or **Vitality**. The camera opens ready to shoot.
3. Take the photo. A Clicks purchase usually needs several in a row (the receipt, then the items), so taking another should be one tap, with no trip back to the home screen.
4. Each photo is saved automatically into that button's folder, named with the date and time, for example `clicks_2026-11-07_1213_01.jpg`.
5. The app shows a brief confirmation that the photo was saved.

There are two folders, **Clicks** and **Vitality**, kept somewhere easy to find in the phone's file manager and ideally picked up by the user's cloud backup.

## Requirements

The app succeeds if capturing a receipt takes about as long as taking a normal photo and no photo ever ends up in the wrong place or goes missing.

- **Frictionless:** from the home screen to the shutter in as few taps as possible, ideally one tap per button via home screen shortcuts.
- **Set up once:** no settings, prompts or choices to make during normal use.
- **Robust:** no timing tricks or background file watching. The app itself decides the folder at the moment of capture, so a photo can't be misfiled.
- **Never lose a photo:** save explicitly and confirm it. If saving fails, say so clearly.
- **Readable images:** full-resolution photos, so receipt text is legible for tax purposes.
- **Works offline,** with no account or sign-in.
- **Easy to get out:** photos sit in ordinary folders the user can browse, copy or sync, so batch processing later needs no special export step.

## Out of scope and open points

The first version only captures and files photos. Left out on purpose:

- OCR, categorizing, or reading totals off receipts
- Spreadsheets, tax reports or any kind of export
- Built-in cloud upload (a folder the user's existing backup can pick up is enough)
- Support for iPhone

Left for the builder to decide: the tech stack, which camera API to use, exact folder location, the filename scheme, and whether to add a home screen widget as well as shortcuts. The builder should prefer whichever choice is simplest and most reliable.

One open question for the user: is a Vitality capture a real camera photo of a board, or a screenshot taken on the phone? If it's a screenshot, that button may not need the camera at all.
