# Phát triển Foldlet

## Build và cài đặt

Yêu cầu **JDK 17+**, Android SDK 36 và ADB. Dự án dùng Kotlin, Android Views/Canvas và Gradle Wrapper; không dùng Compose.

```sh
./gradlew :app:assembleDebug :app:lintDebug
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

- APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Package ID: `vn.lit.screenos`.
- Tên hiển thị: **Foldlet**. Package ID được giữ ổn định để cập nhật app, dữ liệu và quyền đã cấp.
- Nếu cài lần đầu, mở Foldlet và hoàn tất thiết lập quyền trên điện thoại.

## Kiểm tra

Các script dưới đây chỉ chạy trên AVD dành riêng cho dự án, có tên `LIT_Cover_API_36_1`. Tên AVD là định danh kiểm tra cũ, độc lập với tên hiển thị Foldlet. Chạy lần lượt vì các suite dùng chung máy ảo.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/emulator_smoke.py --suite drawer
python3 scripts/emulator_overlay_smoke.py
python3 scripts/emulator_settings_smoke.py
```

Các kiểm tra bao gồm mở app khi thiếu helper xoay, tìm kiếm/lưu cài đặt, Back tới app bên dưới, Gần đây từ Usage Access khi lịch sử LIT rỗng, điều hướng, chạm xuyên vùng ngoài overlay và vòng đời cửa sổ. Các quyền/kích thước thay đổi tạm trên AVD được phục hồi sau kiểm tra.

Ảnh, báo cáo và log nằm trong `artifacts/` — thư mục này không đưa vào Git. Các phép thử trên máy thật được ghi riêng trong [ghi chú kỹ thuật](research/REFERENCE_ANALYSIS.md).

## Cấu trúc dự án

```text
app/src/main/java/vn/lit/screenos/
  CoverMonitorService.kt   Vòng đời cover và điều phối giao diện
  CoverOverlay.kt          Drawer, vùng chạm và thanh điều hướng
  AppDrawerView.kt         Danh sách app và tìm kiếm
  RecentApps.kt            Dữ liệu app vừa dùng từ Android
  RecentAppsView.kt        Giao diện Gần đây và Đóng tất cả
  NavigationService.kt     Back và nhận biết giao diện qua trợ năng
  SamsungCoverPage.kt      Phân biệt đồng hồ/widget Samsung
  SettingsScreen.kt        Thiết lập trên màn hình chính
app/src/main/res/          Icon, theme và bản dịch Việt/Anh
scripts/                  Cầu nối USB và các kiểm tra AVD
design/foldlet/            Thiết kế nhận diện Foldlet
research/                 Ghi chú phân tích và kiểm chứng
```


## Kiểm tra ngôn ngữ và release

```sh
python3 scripts/emulator_locale_smoke.py
./gradlew :app:assembleRelease :app:lintRelease
```

Release 0.5.6 là bản beta, `debuggable=false`, ký bằng chứng chỉ phát triển cục bộ hiện có để cập nhật được bản đã cài. Keystore nằm ngoài repository; không chia sẻ khóa hoặc APK trong Git. Với bản phân phối lâu dài, cấu hình chứng chỉ riêng và giữ khóa ổn định. Hai máy build khác nhau dùng debug keystore khác nhau sẽ không tạo APK cập nhật tương thích.

Mở Foldlet sau khi cập nhật để khôi phục dịch vụ nếu công tắc trước đó đang bật. Các quyền Android đã cấp được giữ khi chữ ký tương thích.

Ảnh trong `docs/images/` là ảnh chụp giao diện native từ AVD riêng 512 × 260 và màn hình thiết lập 648 × 1536; không có dữ liệu điện thoại cá nhân.
