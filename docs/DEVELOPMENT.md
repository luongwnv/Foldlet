# Developing Foldlet

Foldlet is a native Android Views/Canvas application for the Galaxy Z Flip3 cover screen. The package ID stays `vn.lit.screenos` so compatible updates preserve data and permissions.

## Build

Requires JDK 17+, Android SDK 36, and ADB.

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintRelease
./gradlew :controls:assembleRelease
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/release/app-release.apk
```

Beta release builds are not debuggable. They use the existing local development certificate to preserve compatibility with installed test builds. Keystores stay outside Git. Builds signed with another key cannot update an installed APK with this package ID.

## Main components

| Component | Purpose |
| --- | --- |
| `CoverMonitorService` | Cover lifecycle and overlay coordination |
| `CoverOverlay` | Drawer, edge handles, navigation, shortcuts, and keyboard windows |
| `HomeGestureOverlay` / `HomeTouchConfig` | Bounded clock-only tap region and top swipe target |
| `QuickShortcuts` | Exported system control entry points available on the device |
| `RadioControls` / `controls:RadioService` | Signature-protected bound helper for legacy Wi-Fi/Bluetooth toggles |
| `ShortcutStates` / `QuickSettingsIcons` | Live control state and runtime One UI artwork with vector fallbacks |
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

Long-press shortcuts use exported OS panels. The optional `controls` APK targets API 28 to use the documented legacy Wi-Fi/Bluetooth toggle compatibility, while the main app targets API 35: see [WifiManager](https://developer.android.com/reference/android/net/wifi/WifiManager#setWifiEnabled(boolean)) and [Android 13 Bluetooth changes](https://developer.android.com/about/versions/13/behavior-changes-13#bluetooth-adapter). It has no network access and does not scan networks or devices, and binds only through a signature permission. Both APKs must use the same signing certificate. The helper also validates the Messenger sender UID/package/certificate and refuses toggles while locked. It accepts only state reads and Wi-Fi/Bluetooth toggles; hotspot/mobile data/network mode have no privileged toggle backend.

Controls 1.1 reads hotspot state through the legacy `WifiManager.getWifiApState` getter, requiring its existing Wi-Fi state permission ([AOSP source](https://android.googlesource.com/platform/packages/modules/Wifi/+/refs/heads/main/framework/java/android/net/wifi/WifiManager.java)). It reads the configured network mode for the default data SIM from One UI's legacy settings, returning unknown if unavailable or ambiguous. This is a configured preference, not a claim about the currently connected radio technology. The main app reads mobile-data preference with `ACCESS_NETWORK_STATE` ([TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager#isDataEnabled())), and sound/airplane/location/brightness mode through read-only platform APIs. No phone-number or location-data permission is requested. Only state is returned, without SIM identifiers, network names, or device lists.

Quick-setting icons are loaded by resource name from the installed `com.android.systemui` package. Samsung artwork is not copied into the repository or APK. Missing/inaccessible resources use Foldlet's Canvas glyphs. The panel polls while attached, updating state, labels, and sound icon without rebuilding the grid or interrupting a press.
