# LIT Screen OS

Ứng dụng Android độc lập cho màn hình ngoài Galaxy Z Flip3, phát triển từ phân tích hành vi/giao diện APK CoverScreen OS Legacy đang có trên máy. Package `vn.lit.screenos`, bản **0.5.3**. Không có billing hoặc giới hạn Premium trong các tính năng đã triển khai. Chưa đạt tương đương toàn bộ CoverScreen OS Premium.

## Trạng thái đã kiểm chứng

- Overlay thật trên **display 1** của SM-F711B, Android 14, 512 × 260; không dùng Activity/Presentation làm engine.
- Firmware này không cấp token accessibility overlay cho cover. LIT dùng `TYPE_APPLICATION_OVERLAY` (2038) sau khi người dùng cho phép hiển thị trên ứng dụng khác. Trên display hỗ trợ token, dùng accessibility overlay 2032.
- **Màn hình Samsung là mặc định.** Chỉ trên **trang đồng hồ Samsung**, chạm vùng **48 px ở cạnh phải** để mở danh sách app LIT; vẫn hỗ trợ vuốt từ phải sang trái. Vùng này trong suốt, chỉ có vạch gợi ý nhỏ ở mép. Sang trang widget, LIT tháo hẳn vùng chạm để Samsung nhận toàn bộ thao tác. Khi chưa xác định được trang, LIT cũng nhường Samsung. Chạm hơi lệch tay vẫn nhận; kéo dọc không mở nhầm menu.
- Trong menu LIT, vuốt từ cạnh trái sang phải hoặc cạnh phải sang trái để về Samsung. Cử chỉ bắt đầu trong 24 dp ở cạnh, đi vào ít nhất 48 dp và chủ yếu theo chiều ngang; cuộn dọc vẫn hoạt động. Icon đang chạm được hủy trước khi rời menu để không mở nhầm app.
- Khi đang dùng app, vùng cạnh thu về **18 px**; vuốt hoặc chạm cạnh phải hiện thanh điều hướng 48 px: Quay lại, launcher, gần đây, về Samsung, ẩn thanh. Nhấn giữ nút ẩn để tạm tháo cả vùng cạnh. Nút về Samsung gọi secondary-home Samsung trên display 1 bằng UID app, không cần helper ADB.
- Launcher nền đen, thanh công cụ năm nút: hướng mở app, tìm kiếm, sắp xếp, gần đây, yêu thích. Toolbar trượt ẩn khi cuộn xuống và hiện khi cuộn lên; không thay đổi bề rộng/ô icon.
- Glyph cùng màu trắng, cùng kích thước 26 dp và nét vẽ; trạng thái chọn dùng nền đánh dấu/ngôi sao tô đầy. Chữ LIT tối thiểu 16 sp, ô tìm kiếm 17 sp, phím tìm kiếm 14 sp.
- Settings trên màn hình chính dùng nền xanh nhạt, thẻ nhóm, icon, công tắc và ô chọn; ba tab Tổng quan/Cài đặt/Thông tin. Trạng thái chạy không có icon đứng trước. Bộ icon Settings thống nhất nét viền 1,8 đơn vị, một màu; bánh răng sáu răng, trợ năng không nền tròn, chuông và hai hướng màn hình đều cùng ngôn ngữ hình học.
- Lựa chọn số cột, tên ứng dụng, sắp xếp, bộ lọc và hướng mở app có tác dụng thật, lưu qua lần mở sau. Tên app là tùy chọn, mặc định tắt để giữ bố cục icon 112 px.
- Mặc định **4 cột, icon 112 px** trên cover 512 × 260, ô rộng cố định 128 px. Đã đối chiếu ảnh launcher CoverScreen OS Legacy chụp trực tiếp trên cùng máy (icon khoảng 112 px). Có lựa chọn 4/5/6 cột trong thiết lập, icon thu theo bề rộng ô.
- Tìm kiếm trực tiếp với bàn phím nhỏ tích hợp; sắp xếp A–Z/Z–A/dùng nhiều/tùy chỉnh; yêu thích, ẩn app; gần đây lưu app được mở qua LIT.
- Smoke test máy ảo kiểm tra thao tác launcher, lưu yêu thích, tìm kiếm, sắp xếp, ẩn và ghi lịch sử từ lần mở Clock thật. Test overlay kiểm tra chạm xuyên vùng ngoài cạnh, vuốt hiện điều hướng, quay lại launcher, nhường màn hình và tháo cửa sổ.
- Bản 0.4.0 đã cài và kiểm tra trên Z Flip3: Samsung → vuốt cạnh mở LIT → mở Máy tính → điều hướng → Samsung. Kiểm tra cả toolbar ẩn khi cuộn và native Samsung home hiển thị dưới vùng cạnh; luồng này chạy khi không có helper ADB.
- Đã sửa cửa sổ launcher bị sót khi tháo trước khung hình đầu tiên. Test máy ảo tạo/tháo liên tiếp cửa sổ chưa attach và xác nhận không còn root đăng ký trong WindowManager; kiểm tra máy thật cũng xác nhận chỉ một cửa sổ LIT tồn tại ở mỗi bước.

## Build và cài

Cần JDK 17+ và Android SDK platform 36. Đặt `sdk.dir` trong `local.properties` hoặc khai báo `ANDROID_HOME`.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n vn.lit.screenos/.MainActivity
```

Trong tab **Tổng quan**, cấp **Hiển thị trên ứng dụng khác**, bật dịch vụ **LIT Screen OS · Overlay màn hình ngoài**, rồi gạt công tắc LIT. Samsung tiếp tục hiển thị; chạm cạnh phải để gọi danh sách app. Notification Access là quyền tùy chọn trong tab Thông tin cho thông báo/media. Quyền gửi thông báo cho phép dùng nút Dừng trong thông báo thường trực.

App chỉ chọn màn hình cover trong profile Flip3. Không lấy display ảo scrcpy làm cover và không dựa vào việc màn hình chính có đang sáng hay không. Cảm biến hinge/lid giúp phân biệt trạng thái gập. Trợ năng chỉ kiểm tra mã khung và trạng thái hiển thị của giao diện Samsung trên cover để phân biệt trang đồng hồ với widget; không đọc chữ, mô tả hoặc nội dung widget. Không duyệt cây giao diện của các app khác hay màn hình chính. Việc kiểm tra trang này không cần helper ADB.

## Xoay ứng dụng

Người dùng đã chọn **chỉ dùng ADB khi cắm máy tính**. App không tích hợp hoặc khởi động Shizuku, không cần root. One UI trên máy thử bỏ qua `screenOrientation` của overlay, nên dùng helper shell giới hạn chỉ xoay display 1:

```sh
python3 scripts/adb_rotation_bridge.py --serial DEVICE_SERIAL
```

Giữ tiến trình chạy trong lúc dùng. Chọn hướng trong toolbar; hướng áp dụng khi mở app, không xoay lưới icon lúc đang chọn. Khi về launcher, helper giữ launcher nằm ngang dù app bên dưới yêu cầu dọc. Rời launcher, mở điện thoại hoặc dừng LIT sẽ khôi phục policy trước đó; lúc mở app có chọn hướng, app tiếp tục áp dụng hướng được chọn. Ctrl+C trên máy tính dừng helper và khôi phục policy. Sau khi khôi phục policy tự động, hướng thực tế còn phụ thuộc ứng dụng đang chạy bên dưới.

Helper trên điện thoại nghe **127.0.0.1**, dùng token ngẫu nhiên riêng cho mỗi phiên; token được gửi tới app qua receiver được bảo vệ bởi quyền signature `DUMP`. Chỉ nhận auto/portrait/landscape trên display 1 của profile Flip3. Một token khác chỉ nằm phía máy tính dùng để gia hạn nhịp sống; app không thể gia hạn. Mất nhịp quá 4 giây sẽ đóng helper và khôi phục policy; có tệp phục hồi cho lần chạy tiếp theo nếu tiến trình bị dừng đột ngột.

```sh
python3 scripts/test_cover_rotation.py --serial DEVICE_SERIAL
```

Script thử rotation 3 trên display 1, kiểm tra policy display 0 và luôn khôi phục policy cũ.

## Kiểm tra máy ảo

AVD dành riêng `LIT_Cover_API_36_1`: 512 × 260, 160 dpi. File `~/.android/avd/LIT_Cover_API_36_1.ini` phải có `target=android-36`; bộ công cụ hiện có từng tạo nhầm `android-0`, làm mất tăng tốc.

```sh
"$ANDROID_HOME/emulator/emulator" -avd LIT_Cover_API_36_1 -accel on -no-snapshot -gpu host -feature -Vulkan -skip-adb-auth
python3 scripts/emulator_smoke.py
python3 scripts/emulator_smoke.py --suite drawer
python3 scripts/emulator_overlay_smoke.py
python3 scripts/emulator_settings_smoke.py
python3 scripts/test_adb_bridge_lease.py --serial emulator-5556
```

Các script chỉ nhận AVD LIT, từ chối điện thoại thật. Test overlay tạm cấp/bind trợ năng trong AVD và phục hồi thiết lập sau test. `-skip-adb-auth` dành cho AVD thử nghiệm cục bộ không có dữ liệu cá nhân. Ảnh/kết quả ở `artifacts/emulator/`, `artifacts/overlay/`. Chạy các suite tuần tự vì chúng dùng chung máy ảo.

Test nhịp sống tự khởi động helper, thử xoay bằng token của app, xác nhận token đó không gia hạn được phiên, rồi tạm dừng tiến trình máy tính. Helper phải đóng socket và khôi phục cả hai policy xoay. Kết quả ở `artifacts/adb-bridge-lease.txt`. Không chạy đồng thời với helper đang dùng thủ công.

Test Settings tạm chuyển AVD LIT sang khổ dọc 648 × 1536 / 288 dpi, kiểm tra tab, dropdown, lưu cài đặt, tên app trong drawer thật và màn hướng dẫn ADB; phục hồi kích thước/mật độ trước test. Ảnh và báo cáo ở `artifacts/settings/`.

## Logo và trang Tổng quan

Đã áp dụng hướng **A — Nếp mở** được người dùng chọn: hai nếp hình học, không có chữ L trong biểu tượng. Icon Android nền xanh nhạt với hình xanh đậm, có adaptive/monochrome và bản thông báo một màu. [Bộ logo SVG/PNG](design/logo/), [bảng trình bày](design/logo/presentation.html) và [hướng dẫn dùng](design/logo/GUIDELINES.md).

Trang Tổng quan bắt đầu ngay bằng thẻ trạng thái và công tắc; đã bỏ khối logo, tên ứng dụng và khẩu hiệu lớn theo yêu cầu. Logo vẫn có trong launcher và tab Thông tin. Bộ icon chức năng Cài đặt tiếp tục dùng nét viền đồng bộ.

## Phần chưa hoàn tất

| Phần | Giới hạn hiện tại |
| --- | --- |
| Giao diện | Đã bám cấu trúc toolbar/grid; chưa xác nhận khớp từng pixel của mọi màn CoverScreen OS |
| Gần đây | App mở qua LIT, chưa phải toàn bộ task hệ thống |
| Thông báo/RemoteInput/media | Có mã; chưa kiểm thử với dữ liệu/app bên thứ ba thật |
| Widget và đồng hồ LIT | Đã bỏ theo yêu cầu; dùng màn hình/Widget Samsung. Host widget LIT cũ được giải phóng khi nâng cấp |
| Ngày/giờ Samsung | Giữ theo Samsung, không sửa định dạng ngày hoặc cỡ chữ của SystemUI. LIT không còn trang đồng hồ và dòng nhắc chạm |
| Bàn phím | Có bàn phím tìm kiếm trong launcher; chưa có IME T9/QWERTY/voice cho mọi app |
| Flex Mode, chuyển app khi mở máy | Chưa triển khai |
| VoIP, trả lời thông minh, hình nền động, edge lighting | Chưa triển khai |
| Overlay trên màn nhạy cảm | Tuân theo giới hạn Android; ứng dụng có thể yêu cầu hệ thống ẩn overlay |

App có quyền socket để liên lạc **chỉ với 127.0.0.1** qua helper ADB; không gửi dữ liệu ra Internet. Nội dung thông báo chỉ lấy trong bộ nhớ khi hiển thị. Không nhúng APK, native library, mã dịch ngược hoặc tài nguyên của CoverScreen OS trong APK LIT.

## Tham chiếu

[Báo cáo phân tích](research/REFERENCE_ANALYSIS.md). APK gốc, split APK, cây xuất JADX và framework tham chiếu nằm ở `.reference/`, được loại khỏi Git.

```sh
python3 scripts/capture_reference.py --serial DEVICE_SERIAL
```

Bản debug cho phép ADB điều khiển engine qua service được bảo vệ bằng quyền signature `DUMP`; bản release giữ service không exported. Không sửa hoặc ký lại APK CoverScreen OS đã cài.
