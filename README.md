# Movin Standalone Android App

Native Jetpack Compose Android wrapper for [Saleem7x3/Movin](https://github.com/saleem7x3/movin), modeled after [Santoshkurmi/antigem](https://github.com/santoshkurmi/antigem)'s embedded Linux architecture.

## Architecture
- **Native Termux Flavor (`termux`):** Uses `applicationId = "com.termux"` so official Termux packages (`jellyfin-server`, `ffmpeg`, `python`, `openssh`) run at native speed from `/data/data/com.termux/files/usr` without `proot` overhead. Uninstall the standalone Termux app before installing this flavor.
- **Standalone Flavor (`standalone`):** Uses `applicationId = "com.movin.app"` for side-by-side installation.
- **Localhost Control Plane:** `app/src/main/assets/movin_bridge.py` runs on `127.0.0.1:8080` inside a persistent `ForegroundService` (`PARTIAL_WAKE_LOCK` + `WifiLock`) and exposes JSON endpoints for Jellyfin (`:8096`), Copyparty (`:3923`), OpenSSH (`:8022`), telemetry, and logs.
- **UI Themes:** Supports System, **Apple White Light** (`#FFFFFF` / `#F2F2F7`), and **True OLED Dark** (`#000000` / `#121214`).

## Build the APK in 2 Minutes via GitHub Actions (No Local Android Studio Needed)
1. Create a new GitHub repository and push all files in this folder to `main`.
2. Open the **Actions** tab in GitHub and run **Build Movin Android APK** (it also triggers automatically on push).
3. Download `movin-termux-native-debug-apk` from the workflow **Artifacts** section and sideload it on your Android device.

## Build Locally in Android Studio
1. Open this folder in Android Studio (JDK 17, Android SDK API 35).
2. Select Build Variant `termuxDebug` (recommended) or `standaloneDebug`.
3. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
