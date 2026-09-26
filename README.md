# TwinSpace

A simplified **second space** for Android. Clone a game you already have — Hill Climb Racing, for example — into an isolated space so a second person can play from a **fresh save**. Your original install is not touched.

This uses Android’s **work profile**, the same OS feature Island and Shelter use. It is not Parallel Space virtualization. The cloned copy is the real APK, with its own data directory.

## What you do on the phone

1. Open TwinSpace → **Create second space**. Accept Android’s work-profile screens.
2. On Android 11+, tap **Open connected apps** if TwinSpace asks, and allow it.
3. Tap **+**, pick the game, tap **Done**.
4. Wait a few seconds (large games copy their APKs). If Android shows an install prompt, tap Install.
5. The game appears in TwinSpace. Open it — it starts like a new install.

Long-press a clone to remove it. Settings can wipe the whole second space. Your main apps stay.

## Limits

- One second space per phone. If a work profile already exists (company email, Island, Shelter), remove it first.
- Some games with Play Integrity / anti-cheat refuse to run in a work profile.
- Not a Play Store listing as-is (device-admin + QUERY_ALL_PACKAGES). Sideload via Codemagic APK.

## Codemagic (Stage 1)

1. Add [this GitHub repo](https://github.com/ashraf673/TwinSpace) in [Codemagic](https://codemagic.io).
2. Run workflow **TwinSpace - Stage 1 Android APK**.
3. Download the debug APK from artifacts.

Same Gradle + Java 17 setup as Let's Backup. Codemagic installs Gradle 8.11.1 and runs `assembleDebug`. No signing key for Stage 1.

Package: `com.twinspace.app` · minSdk 26 · targetSdk 35 · version 2.0
