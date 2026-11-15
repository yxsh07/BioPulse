# BioPulse native Android

Native Java/XML client for the existing BioPulse API. The ten screens follow `../design/ANDROID-SCREENS.md` and the Framer phone references. The app uses Inter, dark outlined panels, six vital cards and native ECG, PPG and respiration charts.

## Build

Install JDK 21, Android SDK platform 36, build-tools 35.0.0 and platform-tools. Set `JAVA_HOME` and `ANDROID_HOME`, or put `sdk.dir=<SDK path>` in the ignored `local.properties` file. From this directory:

```powershell
.\gradlew.bat assembleDebug lintDebug
```

The wrapper pins Gradle 8.13 and verifies its distribution checksum. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Build tools, caches, credentials, local properties and generated APKs are excluded from Git. The Android app requires Android 8.0 (API 26) or later.

## Connect to the backend

Run the existing backend on port 8080 with its configured database. The development website on port 5173 is a separate client; do not enter that address as the Android API server.

For a USB-connected phone:

```powershell
adb devices -l
adb -s <phone-serial> reverse tcp:8080 tcp:8080
adb -s <phone-serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Open BioPulse, choose Connection settings, and enter `http://127.0.0.1:8080`. Sign in with an existing BioPulse account. USB reverse must be restored after reconnecting the phone or restarting ADB. Android may ask you to approve installation. Xiaomi devices may also require Install via USB and USB debugging (Security settings) for automation.

For an emulator, the default server is `http://10.0.2.2:8080`. For Wi-Fi, use the computer's current private LAN address with port 8080, with the backend listening on that interface and Windows allowing the connection. A Wi-Fi change can change that address. Connection settings can be edited without rebuilding the app. HTTPS is supported; release builds require HTTPS. No certificate validation is bypassed.

## Data and session behavior

Sign-in uses the backend's session cookie and CSRF contract. Cookies, passwords and observations stay in memory; only the chosen server, theme and device notification preferences persist. Logout clears the session and private shared-report cache. After process death, sign in again.

Selecting a replay scopes observations to its output recording and historical cursor. Monitoring combines authenticated WebSocket notifications with REST refresh and reconnect. Paused and missing data retain their original timestamps and quality. Scrubbing inspects stored samples; it does not implement an unsupported server seek operation. Administrative controls use server authorization.

Trend summaries, gaps and anomalies come from the backend. Counts and list filters describe their loaded, authorized page. CSV reports retain the backend's report format; Preview, Save and Share use native views and Android system actions. The API has no PDF export or push delivery endpoint, so neither is simulated. Notification switches are explicitly device-local preferences.

See `IMPLEMENTATION.md` for design, dependency and testing requirements. Verification is still in progress; an assembled APK alone does not establish device behavior.
