# Release checklist

## 1. Create the signing key (you, once)

No keystore exists in this project, and none should ever be committed. Run this yourself in PowerShell from `C:\Projects\Svara`. It asks for the passwords interactively, so they are never written into a script or a log:

```
keytool -genkeypair -v -keystore svara-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias svara
```

Then create `C:\Projects\Svara\keystore.properties` (git-ignored) with:

```
storeFile=svara-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=svara
keyPassword=YOUR_KEY_PASSWORD
```

Back up the `.jks` file and both passwords somewhere safe. Without them you cannot publish updates.

## 2. Build

```
.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat assembleRelease
```

With `keystore.properties` present, `assembleRelease` signs with your key. Without it, the release build is signed with the debug key; that build is only for benchmarking and must not be shared.

Output: `app\build\outputs\apk\release\app-release.apk`.

## 3. Check before sharing

- [ ] `versionCode` and `versionName` in `app/build.gradle.kts` are raised (currently 300 / 3.0.0).
- [ ] The merged manifest has no `INTERNET` permission (the merged-manifest check in `app/build.gradle.kts` fails the build if it appears).
- [ ] The launcher icon shows the mark, including the monochrome (themed) icon on Android 13+.
- [ ] The splash shows the mark on Android 12+.
- [ ] R8 and resource shrinking are on (`release` build type).
- [ ] Baseline profile generated (`.\gradlew.bat :app:generateBaselineProfile` with a device connected).
- [ ] Instrumented tests pass, run one at a time on the emulator (`connectedDebugAndroidTest` uninstalls the app when it finishes).
- [ ] Phone checks: playback in the background for 30 minutes with the screen off, widgets, quick settings tile, Bluetooth, and a backup exported then restored.
- [ ] `docs/known-limitations.md` is up to date.
