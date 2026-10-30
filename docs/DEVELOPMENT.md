# Developing Foldlet

Foldlet is a native Android Views/Canvas application for the Galaxy Z Flip3 cover screen. The package ID stays `vn.lit.screenos` so compatible updates preserve data and permissions.

## Build

Requires JDK 17+, Android SDK 36, and ADB.

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintRelease
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/release/app-release.apk
```

Beta release builds are not debuggable. They use the existing local development certificate to preserve compatibility with installed test builds. Keystores stay outside Git. Builds signed with another key cannot update an installed APK with this package ID.

## Main components

| Component | Purpose |
| --- | --- |
| `CoverMonitorService` | Cover lifecycle and overlay coordination |
| `CoverOverlay` | Drawer, edge handle, navigation, and cover keyboard windows |
| `AppDrawerView` | Apps, filtering, configurable toolbar, and search |
| `CoverKeyboardView` / `SwipeDecoder` | Shared keyboard and local swipe recognition |
| `NavigationService` | Accessibility navigation and write-only input connection |
| `RecentApps` / `RecentAppsView` | Recent usage cards and Close all |
| `SamsungCoverPage` | Samsung clock/widget coexistence |
| `SettingsScreen` | Settings and the actual drawer preview |

The keyboard verifies the focused cover package and input-session generation before each write. Existing editor contents are not read. Candidate replacement is restricted to the known word length and expected cursor position. Password fields disable gesture suggestions. The bundled basic swipe vocabulary is original, with no external dictionary dependency.

## USB rotation

```sh
python3 scripts/adb_rotation_bridge.py --serial DEVICE_SERIAL
```

The authenticated localhost helper only handles cover rotation. Its lease ends when the tethered process stops. Keyboard input does not use this bridge.

## Optional development checks

The emulator scripts accept only the project-owned `LIT_Cover_API_36_1` AVD. Run sequentially if needed:

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/emulator_smoke.py --suite drawer
python3 scripts/emulator_overlay_smoke.py
python3 scripts/emulator_settings_smoke.py
python3 scripts/emulator_locale_smoke.py
```

Temporary captures and logs belong in ignored `artifacts/`. Public screenshots in `docs/images/` show native UI without personal phone data. Reference APKs, extracted resources, keystores, and build outputs are excluded from Git.
