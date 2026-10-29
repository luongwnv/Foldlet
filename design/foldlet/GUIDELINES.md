# Foldlet — Glance

Hướng **A — Glance**, đã được chọn: hình máy gập đóng, hai camera và màn hình ngoài màu vàng có ba chấm ứng dụng. Nền xanh sáng, hình học phẳng, không chữ L và không nền đen.

## Tệp dùng chính thức

- `final/`: SVG gốc, symbol, wordmark, lockup ngang/dọc, đen, trắng và màu.
- `exports/`: PNG 32/512/1024, app icon, favicon ICO/SVG/PNG và web manifest.
- `final/symbol-small.svg`: bản đơn sắc cho 16–24 px, bỏ ba lỗ nhỏ và tăng độ dày khung.
- `final/app-tile.svg`: bản trình bày icon hoàn chỉnh; Android dùng vector adaptive riêng trong `app/src/main/res/`.
- `generate.py`: tái tạo SVG cuối và vector Android từ cùng hình học.
- `presentation.html`: ngữ cảnh ứng dụng và bộ nhận diện; đây là mockup thương hiệu, không phải ảnh giao diện sản phẩm.
- `preview.html`, `final-audit.txt`: bảng kiểm tra và báo cáo SVG tự động.

Các thư mục `concepts`, `dots`, `cover` và bảng v1/v2 là lịch sử nghiên cứu, không dùng để phát hành. `design/logo/` là nhận diện LIT cũ.

## Màu và nền

| Màu | HEX | RGB |
| --- | --- | --- |
| Foldlet Blue | `#3289FF` | 50, 137, 255 |
| Glance Yellow | `#FFE14A` | 255, 225, 74 |
| Ink | `#173B5A` | 23, 59, 90 |
| White | `#FFFFFF` | 255, 255, 255 |

Trên nền xanh: khung trắng, màn hình vàng. Trên nền trắng: khung xanh, màn hình vàng; chữ Ink. Bản đơn sắc dùng Ink/đen trên nền sáng hoặc trắng trên nền tối. Bản RGB phục vụ màn hình; chưa có bản in thử CMYK/Pantone.

## Kích thước và khoảng trống

- Giữ khoảng trống ít nhất **24 đơn vị** quanh nét ngoài trên lưới 256.
- Symbol đầy đủ: tối thiểu **32 px**; dưới 32 px dùng `symbol-small`.
- Icon Android dùng foreground thu về 60% của viewport adaptive 108 dp. Hệ điều hành tự cắt mặt nạ vuông bo góc/tròn; không thêm nền đen hoặc lồng một tile thứ hai.
- Favicon 16 px dùng bản nhỏ đơn sắc. Không ép bản có ba chấm xuống 16 px.
- Không kéo giãn, nghiêng, thêm bóng, gradient, viền hoặc đổi vị trí màn hình/camera.

Wordmark FOLDLET được dựng bằng path hình học riêng, không phụ thuộc phông chữ. Giữ nguyên khoảng cách các ký tự và lockup đã xuất.

## Hình học và kiểm tra

Symbol dùng lưới 256, khung bo góc, hai camera tròn, màn hình ngoài và một khe gập ngang. Các vùng rỗng là path even-odd. SVG cuối không chứa ảnh raster, live text, filter hoặc gradient. Đã render và xem app tile, lockup, bản đơn sắc và favicon; báo cáo audit chỉ là hỗ trợ tự động, không phải chứng nhận chất lượng hay tra cứu nhãn hiệu.
