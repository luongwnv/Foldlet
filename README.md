<p align="center"><img src="design/foldlet/exports/foldlet-app-icon-512.png" width="96" alt="Foldlet"></p>

# Foldlet

Open apps on your **Galaxy Z Flip3 cover screen**. Foldlet adds an app drawer, search, favorites, and navigation alongside Samsung’s native clock and widgets.

**[Download APK 0.5.6](https://github.com/luongwnv/Foldlet/releases/download/v0.5.6/Foldlet-0.5.6.apk)** · **0.5.6 beta** · Tested on **SM-F711B, Android 14 / One UI 6.1.1**.

## Screenshots

Actual captures of the native Android interface running on a dedicated test emulator. The drawer runs at **512 × 260**, matching the Flip3 cover screen. The apps shown depend on what is installed on your phone.

| App drawer | Search |
| --- | --- |
| ![Foldlet app drawer](docs/images/drawer.png) | ![App search](docs/images/search.png) |

<p align="center">
  <img src="docs/images/overview.png" width="260" alt="Overview and permissions">
  <img src="docs/images/settings.png" width="260" alt="Settings with the actual app drawer preview">
</p>

## Features

- Large icons, 4–6 columns, search, favorites, sorting, and hidden apps.
- A toolbar that hides when scrolling down, with optional app labels.
- Tap the right edge of the **Samsung clock page** to open the drawer. Samsung’s widget pages keep their normal touch controls.
- Back, app drawer, Recent apps, and Samsung Home navigation while using an app on the cover screen.
- Recent apps from across your phone, including apps opened on the inner screen.
- English and Vietnamese following your system language; other languages fall back to English.
- Optional portrait and landscape rotation through a USB connection to your computer.

## Getting started

1. Download **Foldlet-0.5.6.apk** from [Releases](https://github.com/luongwnv/Foldlet/releases), open it on your phone, and allow installation from that source when Android asks.
2. Open **Foldlet** on the inner screen. In **Overview**, grant **Display over other apps** and enable the **Foldlet** service in Android’s **Accessibility** settings.
3. Grant **Usage access** to use Recent apps. Allow notifications if you want the service notification and its **Stop** button.
4. Turn on the Foldlet service, fold your phone, and wake the cover screen.

To update, install the new APK over your existing version and open Foldlet once. Versions signed with the same certificate can update without uninstalling the app.

## Using Foldlet

**From the Samsung clock:** tap or swipe inward from the right edge to open the app drawer. Foldlet places this touch area only on the clock page, leaving widget pages to Samsung.

**In the app drawer:** tap an icon to open an app and swipe vertically to scroll. Use the toolbar to search, filter, or sort. Long-press an icon to favorite it, hide it, or choose its launch orientation. Swipe inward from either the left or right edge to return to Samsung Home.

**Inside an app:** swipe from the right edge toward the left to reveal the navigation bar.

![Foldlet navigation bar on a Galaxy Z Flip3, shown over Samsung Calculator](docs/images/navigation.png)

Captured directly on a Galaxy Z Flip3. Each control below is cropped from the same screenshot, in top-to-bottom order.

| Control | Action |
| --- | --- |
| <img src="docs/images/control-back.png" alt="Back" width="48" height="52" align="absmiddle"> **Back** | Go back in the current app |
| <img src="docs/images/control-apps.png" alt="Apps" width="48" height="52" align="absmiddle"> **Apps** | Open the Foldlet app drawer |
| <img src="docs/images/control-recents.png" alt="Recent apps" width="48" height="52" align="absmiddle"> **Recent apps** | Open Recent apps |
| <img src="docs/images/control-home.png" alt="Home" width="48" height="52" align="absmiddle"> **Home** | Return to the Samsung clock |
| <img src="docs/images/control-hide.png" alt="Hide" width="48" height="52" align="absmiddle"> **Hide** | Hide the navigation bar |

Long-press **Hide** to give Samsung control until the next screen wake.

### Recent apps and Close all

Recent apps shows up to **30 apps used within the past 7 days**, based on Android’s usage history. Tap a card to reopen the app on the cover screen.

**Close all** clears the cards from Foldlet. An app appears again after you use it. This does not force-stop apps or remove tasks from Android’s system Recents.

### Customization

Open **Settings** to choose the column count, app labels, sorting, filters, and launch orientation. The preview uses the actual drawer and your installed app icons to reflect your selected layout.

## Permissions

Permission shortcuts are available in **Overview**. You can revoke permissions in Android Settings.

| Permission | Purpose |
| --- | --- |
| Display over other apps | Show the drawer and navigation bar on the cover screen |
| Accessibility | Distinguish Samsung’s clock and widget pages, and handle Back when you press it |
| Usage access (Optional) | Show Recent apps and help identify an app’s launcher page when handling Back |
| Notifications (Optional) | Show the service status and Stop button |
| Notification access (Optional) | Support notification and media features when used |

Recent-app data is read on your device, is not sent to a server, and is not saved as a separate usage history. Foldlet does not read text-field contents or save screen captures. Its network connection is used only for the ADB rotation bridge on localhost.

## Optional USB rotation

Opening apps, Back, Home, and Recent apps work independently. **Forced rotation** on the Flip3 requires an active USB connection to a computer. No root or Shizuku is needed.

On a computer with Python 3 and ADB installed, download this repository and enable **USB debugging** on your phone. From the project folder, run:

```sh
adb devices
python3 scripts/adb_rotation_bridge.py --serial DEVICE_SERIAL
```

Replace `DEVICE_SERIAL` with the device ID shown by `adb devices`, authorize the USB connection on your phone, and keep the process running. Choose **Landscape** or **Portrait** in Foldlet. When the helper stops or the connection ends, it restores the previous rotation policy. Apps can still open when the helper is unavailable.

## Troubleshooting

- **The right edge does not open the drawer:** return to the Samsung clock page and check the service switch, Accessibility, and Display over other apps permissions. After updating the APK, open Foldlet on the inner screen once.
- **Recent apps is empty:** grant Usage access and open another app. After Close all, use an app again for its card to reappear.
- **Back does not work in an app:** some apps do not expose a usable navigation path on the cover screen. Use the app’s own back control when Foldlet prompts you.
- **Rotation does not change:** check the USB connection and the helper running on your computer.

This beta focuses on the **Galaxy Z Flip3**; other models have not been verified. Samsung controls its own clock, widgets, and date formatting. Some apps and protected Android screens may restrict cover-screen operation.
