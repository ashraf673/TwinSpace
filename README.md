# TwinSpace

A **second space** for Android. Clone a game you already have — Hill Climb Racing, for example — so someone else can play a **fresh copy** on the same phone. Your original install and saves are not touched.

Clones run in TwinSpace’s **application container** (isolated process + data directory). There is **no work profile**, so this works on phones that already have Samsung Knox / work email / Island.

## On the phone

1. Open TwinSpace.
2. Tap **+**, pick the game, tap **Done**.
3. Wait a few seconds while TwinSpace copies the APK into the container.
4. The game appears. Open it — empty save, original untouched.

Long-press a clone to remove it. Settings can wipe every clone. Your main apps stay.

## How it works

TwinSpace hosts selected installed APKs inside its own virtual runtime:

- Copies the installed APK (and split APKs) into TwinSpace private storage
- Extracts native libraries
- Starts the app in a dedicated TwinSpace process (`:c1` … `:c8`)
- Redirects files, databases, and SharedPreferences to a per-clone data folder
- Keeps same-package activity starts inside the container so the original icon is never opened

The original app keeps its own UID and `/data/data/<package>` directory. TwinSpace cannot write there, so the original save stays safe.

This is not OS-level virtualization (Parallel Space / VirtualApp) and not a work profile. Heavy games with anti-cheat or Play Integrity may refuse the container. Casual games like Hill Climb Racing are the target.

## Codemagic (Stage 1)

1. Add [this GitHub repo](https://github.com/ashraf673/TwinSpace) in [Codemagic](https://codemagic.io).
2. Run workflow **TwinSpace - Stage 1 Android APK**.
3. Download the debug APK from artifacts.

Package: `com.twinspace.app` · minSdk 26 · targetSdk 35 · version 3.0.1
