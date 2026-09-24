# TwinSpace

A **simplified second space** for Android — dual spaces, PIN lock, calculator disguise, and cloned web-app sessions.

This is not OS-level app virtualization (Parallel Space / multiDroid). Each clone opens the official web app in an **isolated WebView process** (separate cookies and storage), so you can stay signed into a second account.

## Features

- **Home space** and **Secret space**
- 4-digit **PIN** (stored as SHA-256 on device)
- **Calculator disguise** — lock screen looks like a working calculator; type your PIN, then `=`
- Optional **relock** every time Secret is opened
- Clone catalog: WhatsApp, Telegram, Instagram, Gmail, X, and more
- Per-clone nickname, notes, app lock, and hidden flag
- Up to 8 isolated WebView processes

## Codemagic (Stage 1)

1. In [Codemagic](https://codemagic.io) add this GitHub repo.
2. Use the `codemagic.yaml` workflow **TwinSpace - Stage 1 Android APK**.
3. Run the build. Download the debug APK from artifacts.

Same Gradle + Java 17 setup as Let's Backup: Codemagic installs Gradle 8.11.1 and runs `assembleDebug`. No signing key needed for Stage 1.

## Build locally (optional)

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Package: `com.twinspace.app` · minSdk 26 · targetSdk 35
