# CoverScreen OS Legacy — phân tích ban đầu

Ngày thực hiện: 29/10/2026, múi giờ Việt Nam.

## Mẫu tham chiếu và thiết bị

- Package cài trên máy: `apps.ijp.coveros`.
- `versionName=CSOS_BETA_67`, `versionCode=218`, `minSdk=30`, `targetSdk=36`.
- Base APK: 41.893.657 byte; SHA-256 `2fbcd9c0f311fbae7b5a8f2bfe2842e40e19ba4e4ccad0f4cc15383629bfcf90`.
- Máy: Samsung `SM-F711B`, Android 14, SDK 34; `ro.build.version.oneui=60101`.
- Display vật lý: chính 1080 × 2640, ngoài 512 × 260, density ngoài 160.
- Có display ảo scrcpy trong phiên ADB. Nó không phải màn hình ngoài của máy và ID thay đổi giữa các phiên.

Đây là phiên bản Legacy đang có trên máy, không phải bằng chứng về toàn bộ hành vi của phiên bản CoverScreen OS mới nhất.

## Bằng chứng đọc từ manifest và JADX

Manifest được giải mã bằng Android SDK `apkanalyzer`. JADX 1.5.6 xuất hai lớp dịch vụ và toàn bộ cây tham chiếu vào `.reference/coverscreen-os/decoded/`.

| Bằng chứng | Ý nghĩa và giới hạn |
| --- | --- |
| `CoverOSEngine` đăng ký `NotificationListenerService` | Có thành phần nhận thông báo; chưa xác nhận toàn bộ luồng dữ liệu |
| `CoverOSSupportEngine` đăng ký `AccessibilityService` | Có thành phần trợ năng; không suy ra tất cả tính năng chỉ từ khai báo |
| Một số Activity/service chạy ở process `:flione` | UI và engine có tổ chức đa tiến trình |
| Có `ActivityOptions.setLaunchDisplayId(1)` trong mã xuất | Có luồng chỉ định display đích trực tiếp |
| Có `DisplayManager`, `createDisplayContext`, `WindowManager` | Có tích hợp display và cửa sổ riêng |
| Có khởi tạo LayoutParams với type `2032` | Tương ứng loại cửa sổ accessibility overlay; cần xem cả lifecycle để hiểu luồng đầy đủ |
| Có proxy Activity cho bind/configure widget | Widget có luồng cấp quyền và cấu hình riêng |
| Có khai báo hỗ trợ API Shizuku | Không chứng minh mọi tính năng đều cần Shizuku |
| 28 phương thức native trong `CoverOSEngine`, 7 trong `CoverOSSupportEngine` | Java xuất ra không đủ để mô tả các phương thức này |

JADX báo 11 lỗi khi xuất riêng engine, 2 lỗi khi xuất riêng support engine; lần xuất toàn cây báo 250 lỗi xử lý, tổng cộng 17.411 lớp được lên lịch. Không xem kết quả này là source gốc hay source có thể build lại. Các thư viện phụ thuộc cũng nằm trong cây xuất.

APK có dấu hiệu dùng lớp bảo vệ PairIP trong manifest và mã native. Chưa phân tích mã máy của các thư viện native và chưa xác định chính xác phần logic được bảo vệ. Không thay đổi cơ chế kiểm tra giấy phép.

## Kết quả tích hợp ban đầu

- APK LIT đã build và cài vào Z Flip3 thành công.
- Lệnh ADB `am start --display 1` được Activity Manager chấp nhận. Việc shell mở được activity **không chứng minh UID thường của app cũng được phép** hoặc màn hình đang phát sáng.
- Bài smoke test ban đầu từ UID app chỉ liệt kê được display 0; chưa thấy cover qua `getDisplays()`. Tra cứu trực tiếp `getDisplay(1)` trong profile Z Flip3 giải quyết việc khám phá display.
- **Smoke test phần cứng PASS** sau khi sửa: UID thật của app mở được display 1, dashboard đo được 512 × 260, sáu trang App/Tin/Nhạc/Widget/Nhanh/Nhà render không crash. Máy đang mở khóa trong lần test này. Kết quả không xác nhận tự tiếp quản Samsung SystemUI hoặc các tính năng cần quyền chưa cấp.
- Bản đầu gặp lỗi gọi `window.insetsController` trước khi tạo decor; đã chuyển thao tác xuống sau `setContentView`.
- Theo yêu cầu của người dùng, tiếp tục kiểm thử UI bằng máy ảo, hạn chế phụ thuộc thao tác gập máy thật.
- AVD dành riêng `LIT_Cover_API_36_1`, 512 × 260, 160 dpi. Công cụ tạo AVD ban đầu ghi `target=android-0` cho image 36.1, làm emulator bỏ HVF và chạy TCG rồi crash. Đã sửa target của AVD này thành `android-36`, bật tăng tốc và tắt Vulkan cho lần chạy thử. Đây là vấn đề cấu hình emulator, tách biệt với lỗi app.
- **Smoke test máy ảo PASS**: sáu trang render ở 512 × 260 trên display 0 của AVD. Notification Access chưa cấp, không có phiên media hoặc widget được bind; kết quả xác nhận render và trạng thái thiếu quyền/dữ liệu, không xác nhận thao tác gửi trả lời hay điều khiển ứng dụng bên thứ ba.

Native split đã được lấy cùng các split ngôn ngữ/mật độ, tổng cộng 5 APK. Nó chứa `libcsos.so` (302.920 byte), `libnrb.so` (389.352 byte) cùng các thư viện graphics/nhận diện ngôn ngữ/dự đoán văn bản. Chỉ kiểm kê tên, kích thước và một số chuỗi API; chưa dịch ngược mã máy. Tên file không đủ chứng minh vai trò đầy đủ của từng thư viện.

## Đối chiếu tính năng và hướng triển khai

Danh sách chức năng tham chiếu lấy từ [hướng dẫn của IJP](https://ijp.app/complete-guide-tips-tricks-faqs-answered/), có cả các giới hạn của phiên bản cũ. Danh sách này chưa xác nhận gói Premium hiện hành.

Nền Android thông thường có thể phục vụ launcher, app widget host, notification actions/RemoteInput, MediaSession và cấu hình giao diện. Việc gọi các API này vẫn phụ thuộc quyền người dùng và khả năng của app cung cấp.

[Chính sách launch đa màn hình của AOSP](https://source.android.com/docs/core/display/multi_display/activity-launch) mô tả hạn chế đối với display private, virtual và các đặc quyền hệ thống. Vì vậy cần thử launch dưới UID app, không suy từ thành công của shell.

Giai đoạn tiếp theo: xác nhận display discovery và lựa chọn backend trên One UI; sau đó bổ sung điều hướng xuyên app, bàn phím phù hợp cover, chuyển trạng thái gập, VoIP và cá nhân hóa. Mỗi phần cần kiểm thử riêng trước khi đánh dấu tương đương Premium.

## Cập nhật triển khai 0.3.1

Đã bỏ engine Presentation. Cửa sổ cover thật tạo bằng WindowManager, thử accessibility overlay 2032 rồi chuyển sang application overlay 2038 khi firmware không cấp token và người dùng đã cấp quyền overlay.

Đã lấy framework.jar/services.jar từ chính máy để kiểm tra nguyên nhân. `AbstractAccessibilityServiceConnection.onAdded()` đăng ký token theo `DisplayManager.getDisplays()`; nhánh bổ sung display built-in số 1 bị phụ thuộc cờ hỗ trợ cover lớn của Samsung. Trên SM-F711B, app chỉ liệt kê được display 0 và token cover 1 là null. Vì vậy chỉ đổi loại Dialog hoặc Context không giải quyết được vấn đề.

Kết quả trên máy thật: overlay 2038 gắn display 1, mode DASHBOARD; gập được nhận từ hinge sensor type 36. Sau thu nhỏ, input vuốt từ (510,130) sang (420,130) trên display 1 chuyển sang SIDEBAR rộng 48 px. Không sửa hoặc tắt CoverScreen OS tham chiếu.

Launcher đã đổi sang nền đen, toolbar năm icon và grid tương tự cấu trúc layout tham chiếu. Phản hồi người dùng chỉ ra glyph bị kéo dẹt và icon app quá nhỏ. Nguyên nhân glyph là thiếu intrinsic size, còn grid dùng weight khiến một icon yêu thích nằm giữa. Đã sửa intrinsic 24 × 24, mặc định 4 cột cố định và icon 84 px; regression test xác nhận một icon yêu thích vẫn ở cột đầu.

Các test launcher trên AVD đã kiểm tra tìm kiếm, no-results, yêu thích qua tạo lại Activity, A–Z/Z–A, ẩn app, mở Clock thật và lọc gần đây. Test overlay dùng cửa sổ 2032 thật trên AVD và input để kiểm tra chạm xuyên, cử chỉ cạnh phải, sidebar, quay lại launcher, yield/stop.

Xoay bằng `screenOrientation` của cửa sổ không đổi panel trên firmware này. Đã kiểm tra riêng ADB: fixed-to-user-rotation + user-rotation trên display 1 đổi được actualRotation sang 3, rồi phục hồi cả mode/free và fixed/default; display 0 không đổi.

Người dùng chọn **chỉ ADB khi cắm máy tính**, không dùng Shizuku. Đã bỏ dependency/provider/quyền và màn hình cấp quyền Shizuku. Bản 0.3.2 dùng helper ADB localhost có token phiên và nhịp sống chỉ máy tính gia hạn; app chỉ có quyền yêu cầu xoay/khôi phục, không thể gia hạn helper sau khi rút cáp.

Test 0.3.2 trên AVD xác nhận UID app gửi yêu cầu qua socket và đổi actualRotation sang 3. Bài test nhịp sống riêng dùng token của app để xoay, xác nhận yêu cầu heartbeat bị từ chối, sau đó tạm dừng tiến trình máy tính trong khi ADB vẫn hoạt động. Helper đóng socket và khôi phục user-rotation/fixed-to-user-rotation về giá trị trước test. Không cần rút cáp hay ngắt ADB của thiết bị khác. Z Flip3 đã ngắt kết nối trong giai đoạn thử máy ảo này, sau đó người dùng cắm lại cáp để tiếp tục.

## Kiểm tra USB và sửa lifecycle 0.3.3

Bản 0.3.2 đã được cài khi Z Flip3 kết nối lại. App UID yêu cầu portrait qua helper, actualRotation display 1 đổi sang 3; yêu cầu auto phục hồi user-rotation/fixed-to-user-rotation, policy display 0 giữ nguyên. Kết quả ở `artifacts/physical-adb-bridge-smoke.txt`.

Kiểm tra UI phát hiện nhiều cửa sổ dashboard LIT còn tồn tại sau khi thu nhỏ, che Máy tính dù engine báo EDGE. `WindowManager.addView()` đăng ký ViewRoot trước khi traversal đầu tiên gắn decor; `hide()` cũ chỉ remove khi `isAttachedToWindow`, nên có thể bỏ sót root đang chờ. Bài test tái hiện trên AVD trước sửa báo “Overlay root leaked before its first frame”. Bản 0.3.3 tháo mọi decor đã add thành công, kể cả chưa attach; test tạo/tháo liên tiếp và kiểm tra WindowInspector đã qua.

Samsung còn giữ hướng dọc theo Activity Máy tính sau khi phục hồi policy free/default. Vì thế 0.3.3 giữ launcher nằm ngang bằng helper khi ADB còn hoạt động; rời launcher hoặc helper kết thúc sẽ phục hồi policy gốc. Không thể đồng nhất việc phục hồi policy với ép actualRotation về 0 khi ứng dụng bên dưới vẫn yêu cầu dọc.

Luồng UI 0.3.3 trên SM-F711B đã qua: chọn Dọc trên toolbar → UID app mở Máy tính trên display 1 → actualRotation 3, Máy tính visible và chỉ còn cửa sổ cạnh 18 px → vuốt cạnh phải hiện sidebar → nút launcher trở lại landscape 0. Mỗi bước chỉ có một cửa sổ LIT; policy display 0 không thay đổi. Kết quả và ảnh ở `artifacts/physical-033-launch-navigation.txt`, `artifacts/physical-033-portrait-calculator.png`, `artifacts/physical-033-portrait-navigation.png`, `artifacts/physical-033-restored-drawer.png`.

Đã kiểm tra dừng helper USB ngay khi launcher đang dùng lock 0/enabled: policy cover trở lại free/default, display 0 vẫn lock 0/default. Kết quả ở `artifacts/physical-033-adb-stop.txt`. Kiểm tra này không thay thế phép thử rút cáp vật lý; nhánh hết nhịp sống được kiểm tra độc lập trên AVD như mô tả phía trên.

## Cập nhật giao diện và Samsung home 0.4.0

Người dùng chọn Samsung mặc định, LIT chỉ mở danh sách app. Đã bỏ trang đồng hồ LIT, dòng nhắc chạm và toàn bộ picker/renderer widget LIT; giải phóng host widget cũ của riêng LIT khi nâng cấp. Vì giữ đồng hồ Samsung, định dạng ngày và cỡ chữ đồng hồ thuộc Samsung; không phủ một lớp ngày/giờ LIT lên giao diện đó.

Đã kiểm tra manifest SystemUI lấy từ chính máy: `com.android.systemui.subscreen.SubHomeActivity` exported=true, không có permission trên activity hoặc application, nhận SECONDARY_HOME. LIT mở component này bằng UID app với launchDisplayId=1. Kiểm tra máy thật khi không có token/helper ADB: Samsung home visible dưới vùng cạnh 18 px → vuốt cạnh mở drawer → mở Máy tính → vuốt mở rail → nút Home đưa về Samsung landscape. Không gọi global HOME trên màn hình chính. Kết quả ở `artifacts/physical-040-samsung-coexist.txt`; ảnh tương ứng bắt đầu bằng `physical-040-`.

Toolbar được đặt trên vùng cuộn, trượt ra ngoài khi cuộn xuống và hiện lại khi cuộn lên, không resize lưới. Mọi glyph dùng cùng màu trắng/nét vẽ/kích thước; trạng thái chọn dùng nền và star fill thay vì làm mờ nút. Chữ LIT tăng tối thiểu 16 sp, tìm kiếm 17 sp, phím 14 sp. Regression launcher đã kiểm tra ẩn/hiện toolbar, geometry, tìm kiếm, yêu thích, sắp xếp, ẩn và mở Clock thật. Regression overlay kiểm tra Samsung mặc định, gọi lại drawer bằng cử chỉ, chuyển chế độ, chạm xuyên, lifecycle và teardown.

Đã thử lại xoay ADB với 0.4.0: chọn Dọc trong toolbar mới → Máy tính hiển thị rotation 3 → rail → Home Samsung về rotation 0, policy cover free/default được phục hồi và policy màn hình chính giữ nguyên. Kết quả ở `artifacts/physical-040-adb-rotation.txt`. Sau test trả hướng mở app về auto, giữ Samsung mặc định và khởi động helper USB để người dùng tiếp tục thử.

## Cử chỉ menu và kích thước icon 0.4.3

Vuốt vào từ cạnh trái hoặc phải của menu gọi Samsung secondary home. Vùng khởi đầu 24 dp, khoảng vuốt tối thiểu 48 dp; khi đã cuộn dọc thì không đổi thành thao tác về home. Hủy sự kiện chạm của icon con trước khi đóng cửa sổ để không mở nhầm app. Menu trống cũng nhận DOWN tại cạnh để tiếp tục nhận MOVE. Regression overlay đã thử hai cạnh, yêu thích trống, chạm cạnh, cuộn dọc, không tăng lịch sử/số lần mở app và lifecycle cửa sổ.

Để so kích thước, đã tạm nhường overlay LIT, mở drawer CoverScreen OS gốc bằng thao tác chạm trên cover và chụp ảnh thực tế 512 × 260: `artifacts/coverscreen-os-reference-drawer.png`. Ảnh cho thấy bốn cột, icon hiển thị khoảng 112 px; không dùng số dp của XML làm kích thước thực tế vì tài nguyên và runtime có điều chỉnh. Đã vuốt đóng drawer gốc sau khi chụp, không thay đổi cài đặt hoặc APK CoverScreen OS.

LIT tăng icon mặc định từ 84 lên 112 px (tăng khoảng 33% mỗi chiều), giữ ô cố định 128 px/bốn cột, khoảng đệm hàng 16 px. Khi chọn 5/6 cột, icon thu theo bề rộng ô. Test launcher xác nhận 112 px, cột cố định và các thao tác tìm kiếm/sắp xếp/yêu thích/gần đây. Test máy thật xác nhận hai cạnh về Samsung, cuộn dọc không thoát menu, không mở nhầm app và không đổi policy màn hình chính. Kết quả ở `artifacts/physical-043-menu.txt`; ảnh LIT ở `artifacts/physical-043-drawer.png` và `artifacts/physical-043-scrolled.png`.

## Settings và icon 0.5.0

Đã mở CoverOSSettings trên màn hình chính để đối chiếu thực tế: trang Home dùng nền xanh nhạt/thẻ, Settings dùng nhóm có icon/ô chọn/công tắc và thanh điều hướng dưới. LIT được viết bằng View/Canvas Android riêng, không nhúng mã hoặc tài nguyên CoverScreen OS. Ba tab Tổng quan/Cài đặt/Thông tin giữ các chức năng đã có; widget và đồng hồ riêng vẫn bị loại theo yêu cầu.

Settings mới có công tắc engine, quyền truy cập, mở menu/Samsung, số cột, sắp xếp, bộ lọc, hướng mở, quản lý app ẩn và tùy chọn hiện tên app thật. Chẩn đoán chuyển vào trang Thông tin. Màn ADB có trạng thái, ba bước và sao chép lệnh. Icon launcher adaptive/monochrome không còn chữ L; logo thương hiệu theo logo-design vẫn là concept chờ người dùng chọn.

Theo phản hồi tiếp theo, bỏ icon trước trạng thái hoạt động; lấy dáng bánh răng sáu răng từ ảnh người dùng, vẽ lại trợ năng/chuông/hai hướng màn hình. Sau khi người dùng yêu cầu đồng bộ, thống nhất icon Settings về nét viền đơn sắc, bỏ nền tròn và các mảng tô hai tông. Đây là bộ icon giao diện, tách với các concept logo.

Test Settings trên AVD đã qua: ba tab, lưu số cột/sắp xếp/bộ lọc/hướng, tên app xuất hiện trong drawer và tồn tại sau mở lại, mở preview và hướng dẫn ADB. Regression launcher 112 px và overlay/cử chỉ cũng đã qua. Ảnh/báo cáo ở `artifacts/settings/` và ảnh máy thật bắt đầu `physical-050-`.

## Logo A và Tổng quan 0.5.1

Người dùng chọn A — Nếp mở sau bảng ba concept. Logo được dựng bằng path riêng, triển khai SVG/PNG, logo ngang/dọc, adaptive/monochrome/status và bộ hướng dẫn trong `design/logo/`. Theo phản hồi không dùng nền đen, icon Android dùng xanh nhạt `#D8F2FC` và hình teal `#006F95`; các glyph chức năng Settings vẫn nét viền. Không sử dụng logo/tài nguyên CoverScreen OS.

Đã bỏ toàn bộ khối logo lớn, tên LIT Screen OS và khẩu hiệu ở đầu Tổng quan. Thẻ trạng thái/công tắc là nội dung đầu tiên, không có khoảng trống thay thế. Logo giữ trong launcher và tab Thông tin. Build/lint và smoke Settings đã qua, bản APK cập nhật được cài trên SM-F711B qua ADB.

## Chạm cạnh phải home Samsung 0.5.2

Theo phản hồi vùng cạnh khó thao tác, tách trạng thái HOME_EDGE khỏi cạnh điều hướng khi đang mở app. Home Samsung có vùng trong suốt rộng 48 dp (48 px trên cover Z Flip3); chạm trực tiếp gọi drawer, vẫn nhận vuốt trái. Khi mở app, cạnh thu về 18 dp và gọi thanh điều hướng như trước. Chỉ giữ vạch gợi ý nhỏ ở mép, không thêm nút lớn lên mặt đồng hồ Samsung.

Tap cho phép lệch ngón trong touch slop ít nhất 12 dp, kiểm tra cả X/Y và hủy nếu kéo dọc, đa điểm hoặc ACTION_CANCEL. Vuốt trái cần ngang chiếm ưu thế. Test overlay thật 2032 trên AVD 512 × 260 đã qua: tap ở x=480 (ngoài cạnh 18 px cũ) mở menu, lệch ngón 6/5 px vẫn mở, kéo dọc không mở nhầm, chạm ngoài vùng 48 px tới underlay; cả hai cạnh menu trở về home và cạnh 18 px trong app vẫn mở rail. Kết quả `artifacts/052-overlay-smoke.log`; build/lint `artifacts/052-build.log`. Máy thật đang mở nên chưa kiểm tra chạm vật lý trên cover trong lượt này.

## Vùng chạm chỉ ở trang đồng hồ 0.5.3

Các trang đồng hồ/widget/quick settings cùng nằm trong SubHomeActivity nên không thể phân biệt chỉ bằng activity hoặc trạng thái đã gọi Home. Đã kiểm tra cấu trúc native trên firmware SM-F711B: khung `sub_home_default_page_clock_container` và `sub_clock_content_container` chỉ được báo đang hiển thị khi đồng hồ hiện. Các widget nằm dưới `sub_ui_home_horizontal_vp`. Bảng resource của plugin AOD dùng namespace `app`, nạp vào context SystemUI; tìm ID trực tiếp từ root không giải được bảng resource này, nên bộ nhận diện duyệt có giới hạn các khung native và chỉ đọc ID/visibility/bounds. Dừng trước cây con của pager widget và ngay khi tìm đủ hai khung đồng hồ; không đọc text/contentDescription. Chỉ xét cửa sổ trên display cover của SystemUI/AOD, không duyệt app khác hay màn hình chính.

Trợ năng được cấu hình nhận sự kiện chuyển cửa sổ/scroll/content của SystemUI và AOD, có report view IDs và truy xuất cửa sổ để kiểm tra các khung trên. Mô tả quyền trong app được cập nhật đúng với phạm vi này. Khi đang scroll, chưa biết trang hoặc trang khác, LIT tháo HOME_EDGE hoàn toàn; polling dự phòng 200 ms và kiểm tra lại trước khi mở menu. Khi app dùng Back trở về Samsung, trạng thái native được nhận diện lại. Cạnh 18 px cho app mở qua LIT vẫn giữ nguyên. Việc nhận diện không dùng helper ADB.

Đã thử trên máy thật khi helper tắt (`adbBridgeConfigured=false`): chạm clock mở drawer; vuốt menu trở về Samsung; sang thời tiết và widget kế tiếp thì không còn cửa sổ LIT; chạm bên phải widget không gọi LIT; quay về clock thì vùng 48 px trở lại. Quick settings Samsung cũng được nhường toàn bộ. Kiểm tra trạng thái sau khi hết animation (không chỉ lúc `scrolling`). Bằng chứng: `artifacts/physical-053-home-widget-check.txt`, `physical-053-test.log`, ảnh `physical-053-clock.png`, `physical-053-widget.png`, `physical-053-quick-settings.png`.

Thêm sự kiện trợ năng làm lộ race khi một cửa sổ vừa add chưa có frame đầu: reconcile cùng mode không được tháo/tạo lại root đang chờ layout. Giữ root đã add đến khi đổi mode hoặc hide, đồng thời giữ nguyên tháo root pending ở lifecycle cũ. Build/lint và regression overlay ghi tại `artifacts/053-build.log`, `053-overlay-smoke.log`. Nếu Samsung đổi các ID native ở firmware khác, bộ nhận diện nhường giao diện Samsung thay vì phủ vùng chạm lên widget.

## Icon điều hướng 0.5.4

Tách glyph NAV_BACK/NAV_HOME/NAV_RECENT cho thanh điều hướng trong app: Back dạng ‹, Home vuông bo góc, Gần đây ba vạch dọc theo ngôn ngữ Samsung; cùng nét trắng 1,8. Các glyph ngôi nhà và lịch sử có nghĩa khác trong giao diện khác nên không thay toàn bộ enum HOME/RECENT. Đã render và xem ảnh rail 48 × 260; build/lint và regression overlay đã qua (`artifacts/054-build.log`, `054-overlay-smoke.log`). Lượt này chỉnh hình icon; nút Gần đây vẫn là lịch sử mở app qua LIT, không gọi đa nhiệm hệ thống hoặc thao tác trên display chính.
