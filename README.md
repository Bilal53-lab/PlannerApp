# My Workspace

Offline personal workspace app for Android: tasks, notes, short- and long-term goals, projects, analytics and reports.

- `app/src/main/assets/index.html` — the whole app UI and logic (data is stored on the device)
- `app/src/main/java/app/myworkspace/MainActivity.java` — Android WebView shell with file save/share/restore
- `MyWorkspace.apk` — debug-signed build, Android 7.0+

## Build

Needs JDK 17+, Gradle 8.9+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
