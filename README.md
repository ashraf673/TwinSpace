# TwinSpace

A **second space** for Android. Clone a game you already have — Hill Climb Racing, for example — so someone else can play a **fresh copy** on the same phone. Your original install and saves are not touched.

This uses Android’s **work profile** (the same OS feature Island and Shelter use). The clone is the real APK, with its own data directory. It is not Parallel Space virtualization and it is not a WebView.

## On the phone

1. Open TwinSpace → **Create second space**. Accept Android’s work-profile screens (one-time).
2. On Android 11+, if TwinSpace asks, tap **Open connected apps** and allow it.
3. Tap **+**, pick the game (Hill Climb Racing), tap **Done**.
4. Wait a few seconds. If Android shows an install prompt, tap Install.
5. The game appears in TwinSpace. Open it — it starts like a brand-new install.

Long-press a clone to remove it (only the copy). Settings can wipe the whole second space. Your main apps stay.

## Limits

- One second space per phone. If a work profile already exists (company email, Island, Shelter), remove it first.
- Some games with Play Integrity / anti-cheat refuse a work profile. Casual games like Hill Climb Racing usually work.
- Not a Play Store listing as-is (device-admin + QUERY_ALL_PACKAGES). Sideload the Codemagic APK.

## Codemagic (Stage 1)

1. Add [this GitHub repo](https://github.com/ashraf673/TwinSpace) in [Codemagic](https://codemagic.io).
2. Run workflow **TwinSpace - Stage 1 Android APK**.
3. Download the debug APK from artifacts.

Same Gradle + Java 17 setup as Let's Backup. Codemagic installs Gradle 8.11.1 and runs `assembleDebug`. No signing key for Stage 1.

Package: `com.twinspace.app` · minSdk 26 · targetSdk 35 · version 2.1
