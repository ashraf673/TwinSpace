# TwinSpace

A **second space** for Android. Clone an app you already have — Instagram, WhatsApp, a game, anything installed — so someone else can use a **fresh copy** on the same phone. Your original install and data are not touched.

Clones run in TwinSpace’s **application container** (isolated process + data directory, with path redirection). There is **no work profile**, so this works on phones that already have Samsung Knox / work email / Island.

## On the phone

1. Open TwinSpace.
2. Tap **+**, pick the app, tap **Done**.
3. Wait a few seconds while TwinSpace copies the APK into the container.
4. The app appears. Open it — empty data, original untouched.

Long-press a clone to remove it. Settings can wipe every clone. Your main apps stay.

## How it works

TwinSpace hosts selected installed APKs inside its own virtual runtime:

- Copies the installed APK (and split APKs) into TwinSpace private storage
- Loads the app from the phone’s installed package (same code Android uses)
- Starts it in a dedicated TwinSpace process (`:c1` … `:c8`)
- Redirects files, databases, and SharedPreferences to a per-clone data folder — including hardcoded `/data/data/<package>` paths
- Keeps same-package activity starts inside the container so the original icon is never opened

The original app keeps its own UID and `/data/data/<package>` directory. TwinSpace cannot write there, so the original save stays safe.

Some apps still refuse clones (Play Integrity / anti-cheat). TwinSpace is built to clone **any installed app**, not only games.

## Codemagic (Stage 1)

1. Add [this GitHub repo](https://github.com/ashraf673/TwinSpace) in [Codemagic](https://codemagic.io).
2. Run workflow **TwinSpace - Stage 1 Android APK**.
3. Download the debug APK from artifacts.

Package: `com.twinspace.app` · minSdk 26 · targetSdk 35 · version 3.1.0